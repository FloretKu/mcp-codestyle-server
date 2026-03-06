package top.codestyle.mcp.service;

import lombok.extern.slf4j.Slf4j;
import org.jgrapht.alg.scoring.BetweennessCentrality;
import org.jgrapht.alg.scoring.PageRank;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.model.ast.AstNode;
import top.codestyle.mcp.model.ast.DependencyGraph;
import top.codestyle.mcp.model.ast.ProjectSkeleton;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RepoMaster 打分引擎（多粒度 + 层级化剪枝）
 * <p>
 * 基于 DependencyGraph 做加权 PageRank，综合入度、代码复杂度、目录深度奖励，
 * 按 detailLevel 1–4 做层级化剪枝。
 *
 * @since 2.1.0
 */
@Slf4j
@Service
public class RepoMasterScoringService {

    @Value("${codestyle.scoring.alpha:0.45}")
    private double alpha;

    @Value("${codestyle.scoring.beta:0.25}")
    private double beta;

    @Value("${codestyle.scoring.gamma:0.15}")
    private double gamma;

    @Value("${codestyle.scoring.delta:0.15}")
    private double delta;

    @Value("${codestyle.scoring.git-weight:0.25}")
    private double gitWeight;

    @Value("${codestyle.scoring.semantic-weight:0.10}")
    private double semanticWeight;

    @Value("${codestyle.scoring.betweenness-weight:0.05}")
    private double betweennessWeight;

    public record NodeScore(String filePath, String nodeId, String nodeName, double score) implements Comparable<NodeScore> {
        @Override
        public int compareTo(NodeScore other) {
            return Double.compare(other.score, this.score);
        }
    }

    /**
     * 对骨架中的文件进行综合打分（可选用 DependencyGraph 做 PageRank）。
     *
     * @param skeleton 项目骨架
     * @param graph    可选，若非 null 则用其加权 PageRank
     * @return 按分数降序的文件级评分列表
     */
    public List<NodeScore> scoreNodes(ProjectSkeleton skeleton, DependencyGraph graph) {
        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> g = graph != null && graph.getGraph() != null
                ? graph.getGraph()
                : buildFileOnlyGraph(skeleton);

        Map<String, Double> pageRankScores = new HashMap<>();
        if (g.vertexSet().size() > 1) {
            try {
                PageRank<String, DefaultWeightedEdge> pr = new PageRank<>(g, 0.85);
                for (String v : g.vertexSet()) {
                    pageRankScores.put(v, pr.getVertexScore(v));
                }
            } catch (Exception e) {
                log.warn("PageRank 计算异常: {}", e.getMessage());
                double uniform = 1.0 / g.vertexSet().size();
                for (String v : g.vertexSet()) pageRankScores.put(v, uniform);
            }
        } else {
            for (String v : g.vertexSet()) pageRankScores.put(v, 1.0);
        }

        Map<String, Integer> inDegree = new HashMap<>();
        for (String v : g.vertexSet()) {
            inDegree.put(v, g.incomingEdgesOf(v).size());
        }

        Map<String, Double> betweenness = computeFileBetweenness(g);

        Path projectRoot = null;
        try {
            if (skeleton.getProjectPath() != null && !skeleton.getProjectPath().isBlank()) {
                projectRoot = Paths.get(skeleton.getProjectPath()).toAbsolutePath().normalize();
            }
        } catch (Exception ignored) {}

        List<NodeScore> scores = new ArrayList<>();
        Set<String> fileVertices = g.vertexSet().stream().filter(id -> id.startsWith("file:")).collect(Collectors.toSet());
        for (AstNode fileNode : skeleton.getFileNodes()) {
            String path = fileNode.getFilePath();
            String fileId = "file:" + path;
            if (!fileVertices.contains(fileId)) continue;

            double prScore = pageRankScores.getOrDefault(fileId, 0.0);
            double refScore = inDegree.getOrDefault(fileId, 0) * 0.1;
            int classCount = 0, methodCount = 0;
            if (fileNode.getChildren() != null) {
                for (AstNode c : fileNode.getChildren()) {
                    String nt = c.getNodeType();
                    if ("class".equals(nt) || "interface".equals(nt) || "enum".equals(nt)) {
                        classCount++;
                        methodCount += (c.getChildren() != null ? c.getChildren().size() : 0);
                    } else if ("method".equals(nt)) {
                        methodCount++;
                    }
                }
            }
            double complexityScore = classCount * 0.3 + methodCount * 0.05;
            boolean atRoot = path != null && !path.contains("/");
            double depthBonus = atRoot ? 3.0 : 1.0;

            double gitScore = projectRoot != null ? gitHistoryScore(projectRoot, path) : 0.0;      // 0..1
            double semScore = semanticScore(path);                                                   // 0..1
            double btwScore = betweenness.getOrDefault(fileId, 0.0);                                  // 0..1

            double total = alpha * prScore * 100
                    + beta * refScore
                    + gamma * complexityScore
                    + delta * depthBonus
                    + gitWeight * (gitScore * 10)
                    + semanticWeight * (semScore * 10)
                    + betweennessWeight * (btwScore * 10);
            scores.add(new NodeScore(path, fileId, extractMainClassName(fileNode), total));
        }
        Collections.sort(scores);
        return scores;
    }

    private static Map<String, Double> computeFileBetweenness(DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> g) {
        if (g == null || g.vertexSet().isEmpty()) return Map.of();
        try {
            DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> fg =
                    new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
            for (String v : g.vertexSet()) {
                if (v != null && v.startsWith("file:")) fg.addVertex(v);
            }
            for (DefaultWeightedEdge e : g.edgeSet()) {
                String s = g.getEdgeSource(e);
                String t = g.getEdgeTarget(e);
                if (s == null || t == null) continue;
                if (!s.startsWith("file:") || !t.startsWith("file:")) continue;
                if (!fg.containsVertex(s)) fg.addVertex(s);
                if (!fg.containsVertex(t)) fg.addVertex(t);
                DefaultWeightedEdge ne = fg.addEdge(s, t);
                if (ne != null) fg.setEdgeWeight(ne, g.getEdgeWeight(e));
            }
            if (fg.vertexSet().size() <= 2) return Map.of();

            BetweennessCentrality<String, DefaultWeightedEdge> bc = new BetweennessCentrality<>(fg);
            Map<String, Double> raw = new HashMap<>();
            double max = 0.0;
            for (String v : fg.vertexSet()) {
                double s = bc.getVertexScore(v);
                raw.put(v, s);
                if (s > max) max = s;
            }
            if (max <= 0) return raw;
            Map<String, Double> norm = new HashMap<>();
            for (Map.Entry<String, Double> e : raw.entrySet()) {
                norm.put(e.getKey(), e.getValue() / max);
            }
            return norm;
        } catch (Throwable t) {
            return Map.of();
        }
    }

    private static double semanticScore(String filePath) {
        if (filePath == null || filePath.isBlank()) return 0.0;
        String p = filePath.replace("\\", "/");
        String base = p.contains("/") ? p.substring(p.lastIndexOf('/') + 1) : p;
        String name = base.toLowerCase();
        String lower = p.toLowerCase();

        Set<String> importantKeywords = Set.of(
                "main", "core", "engine", "api", "service", "controller",
                "manager", "handler", "processor", "factory", "builder",
                "provider", "repository", "executor", "scheduler",
                "config", "settings", "security"
        );
        double score = 0.0;
        for (String kw : importantKeywords) {
            if (name.contains(kw) || lower.contains("/" + kw + "/")) {
                score = Math.max(score, 0.3);
                break;
            }
        }
        if ("__main__.py".equals(name) || "main.py".equals(name) || "main.java".equals(name) || "main.go".equals(name)) {
            score = Math.max(score, 0.7);
        }
        if ("__init__.py".equals(name) || "app.py".equals(name) || "settings.py".equals(name) || "config.py".equals(name)
                || "utils.py".equals(name) || "constants.py".equals(name)) {
            score = Math.max(score, 0.5);
        }
        return Math.min(score, 1.0);
    }

    private static double gitHistoryScore(Path projectRoot, String relFilePath) {
        try {
            if (projectRoot == null || relFilePath == null || relFilePath.isBlank()) return 0.0;
            if (!Files.isDirectory(projectRoot)) return 0.0;
            if (!Files.isDirectory(projectRoot.resolve(".git"))) return 0.0;

            String rel = relFilePath.replace("\\", "/");

            int commits = runGitCountLines(projectRoot, List.of("log", "--oneline", "--", rel), 3000);
            double commitScore = Math.min(commits / 20.0, 1.0);

            String ts = runGitFirstLine(projectRoot, List.of("log", "-1", "--format=%ct", "--", rel), 2000);
            double recencyScore = 0.0;
            if (ts != null && ts.matches("\\d+")) {
                long epoch = Long.parseLong(ts);
                LocalDate last = Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault()).toLocalDate();
                long days = Math.abs(LocalDate.now().toEpochDay() - last.toEpochDay());
                recencyScore = Math.max(0.0, 1.0 - (days / 365.0));
            }

            return commitScore * 0.7 + recencyScore * 0.3;
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    private static int runGitCountLines(Path projectRoot, List<String> args, int timeoutMs) {
        String out = runGit(projectRoot, args, timeoutMs);
        if (out == null || out.isBlank()) return 0;
        int lines = 0;
        for (String l : out.split("\n")) {
            if (!l.isBlank()) lines++;
        }
        return lines;
    }

    private static String runGitFirstLine(Path projectRoot, List<String> args, int timeoutMs) {
        String out = runGit(projectRoot, args, timeoutMs);
        if (out == null) return null;
        int nl = out.indexOf('\n');
        String first = nl >= 0 ? out.substring(0, nl) : out;
        first = first != null ? first.trim() : null;
        return (first == null || first.isEmpty()) ? null : first;
    }

    private static String runGit(Path projectRoot, List<String> args, int timeoutMs) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("git");
            cmd.add("-C");
            cmd.add(projectRoot.toString());
            cmd.addAll(args);
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            if (!p.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                p.destroyForcibly();
                return null;
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                    if (sb.length() > 200_000) break;
                }
                return sb.toString();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> buildFileOnlyGraph(ProjectSkeleton skeleton) {
        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> g = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        Set<String> files = skeleton.getFileNodes().stream().map(AstNode::getFilePath).collect(Collectors.toSet());
        for (String f : files) {
            g.addVertex("file:" + f);
        }
        for (ProjectSkeleton.DependencyEdge e : skeleton.getDependencies()) {
            if (!"import".equals(e.getType())) continue;
            String src = e.getSource().startsWith("file:") ? e.getSource() : "file:" + e.getSource();
            for (String t : files) {
                if (e.getTarget() != null && (e.getTarget().contains(t.replace("/", ".")) || t.endsWith(e.getTarget()))) {
                    String tgt = "file:" + t;
                    if (g.containsVertex(src) && g.containsVertex(tgt)) {
                        try { g.addEdge(src, tgt); } catch (Exception ignored) {}
                    }
                    break;
                }
            }
        }
        return g;
    }

    /**
     * 按 detailLevel 1–4 做层级化剪枝。
     * Level 1: 仅目录 + 每目录下得分最高的 1 个文件
     * Level 2: 保留得分前 20% 的文件（类骨架 + import 摘要）
     * Level 3: 保留得分前 50% 的文件
     * Level 4: 不剪枝
     *
     * @param skeleton   项目骨架
     * @param detailLevel 1–4
     * @param graph      可选，用于打分
     * @return 剪枝后的骨架
     */
    public ProjectSkeleton prune(ProjectSkeleton skeleton, int detailLevel, DependencyGraph graph) {
        if (detailLevel >= 4) {
            return skeleton;
        }
        List<NodeScore> scores = scoreNodes(skeleton, graph);
        int total = scores.size();
        if (total == 0) return skeleton;

        int keepCount;
        switch (detailLevel) {
            case 1 -> keepCount = Math.max(1, (int) Math.ceil(total * 0.05));
            case 2 -> keepCount = Math.max(1, (int) Math.ceil(total * 0.20));
            case 3 -> keepCount = Math.max(1, (int) Math.ceil(total * 0.50));
            default -> keepCount = total;
        }

        Set<String> keepPaths = scores.stream()
                .limit(keepCount)
                .map(NodeScore::filePath)
                .collect(Collectors.toSet());

        List<AstNode> prunedFiles = skeleton.getFileNodes().stream()
                .filter(f -> keepPaths.contains(f.getFilePath()))
                .collect(Collectors.toList());

        List<ProjectSkeleton.DependencyEdge> prunedEdges = skeleton.getDependencies().stream()
                .filter(e -> keepPaths.contains(ownerPathFromNodeId(e.getSource())))
                .collect(Collectors.toList());

        int totalClasses = 0, totalMethods = 0;
        for (AstNode f : prunedFiles) {
            if (f.getChildren() == null) continue;
            for (AstNode c : f.getChildren()) {
                String nt = c.getNodeType();
                if ("class".equals(nt) || "interface".equals(nt) || "enum".equals(nt)) {
                    totalClasses++;
                    totalMethods += (c.getChildren() != null ? c.getChildren().size() : 0);
                } else if ("method".equals(nt)) {
                    totalMethods++;
                }
            }
        }

        return ProjectSkeleton.builder()
                .projectPath(skeleton.getProjectPath())
                .totalFiles(prunedFiles.size())
                .totalClasses(totalClasses)
                .totalMethods(totalMethods)
                .fileNodes(prunedFiles)
                .dependencies(prunedEdges)
                .directoryNodes(skeleton.getDirectoryNodes() != null ? skeleton.getDirectoryNodes() : List.of())
                .fileSummaries(skeleton.getFileSummaries() != null
                        ? prunedFiles.stream().collect(Collectors.toMap(AstNode::getFilePath, f -> skeleton.getFileSummaries().getOrDefault(f.getFilePath(), ""), (a, b) -> a, LinkedHashMap::new))
                        : Map.of())
                .build();
    }

    /**
     * 兼容旧接口：按压缩比剪枝（0.8 约等于保留 20%，对应 Level 2）。
     */
    public ProjectSkeleton prune(ProjectSkeleton skeleton, double compressionRatio) {
        int level = compressionRatio >= 0.9 ? 1 : compressionRatio >= 0.7 ? 2 : compressionRatio >= 0.4 ? 3 : 4;
        return prune(skeleton, level, null);
    }

    private static String ownerPathFromNodeId(String nodeId) {
        if (nodeId == null) return null;
        if (nodeId.startsWith("file:")) return nodeId.substring(5);
        if (nodeId.startsWith("class:") || nodeId.startsWith("method:")) {
            int first = nodeId.indexOf(':');
            int second = nodeId.indexOf(':', first + 1);
            return second > first ? nodeId.substring(first + 1, second) : nodeId.substring(first + 1);
        }
        return nodeId;
    }

    private String extractMainClassName(AstNode fileNode) {
        if (fileNode.getChildren() == null) return fileNode.getFilePath();
        for (AstNode child : fileNode.getChildren()) {
            String nt = child.getNodeType();
            if ("class".equals(nt) || "interface".equals(nt) || "enum".equals(nt)) {
                return child.getName();
            }
        }
        return fileNode.getFilePath();
    }
}
