package top.codestyle.mcp.service;

import lombok.extern.slf4j.Slf4j;
import org.jgrapht.alg.scoring.PageRank;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.model.ast.AstNode;
import top.codestyle.mcp.model.ast.ProjectSkeleton;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RepoMaster 打分引擎
 * <p>
 * 直接借鉴 2505.21577v2 (RepoMaster) Section 3.1.2 中的 Node Scoring 算法，
 * 综合 PageRank 核心度、调用频次和代码复杂度对项目骨架中的每个类/模块进行打分。
 *
 * <p>
 * 打分结果可用于智能剪枝：按照 compressionRatio 移除低分节点，
 * 实现高达 90% 的 Context 压缩。
 *
 * @since 2.1.0
 */
@Slf4j
@Service
public class RepoMasterScoringService {

    /**
     * 节点评分结果
     */
    public record NodeScore(String filePath, String nodeName, double score) implements Comparable<NodeScore> {
        @Override
        public int compareTo(NodeScore other) {
            return Double.compare(other.score, this.score); // 降序
        }
    }

    /**
     * 对 ProjectSkeleton 中的所有类/接口进行综合打分。
     * <p>
     * 算法维度（参考 RepoMaster 论文 Section 3.1.2）：
     * <ol>
     * <li>PageRank 核心度：在依赖图中的中心性</li>
     * <li>调用频次：被 import 引用的次数</li>
     * <li>代码体量：方法数量作为复杂度的近似</li>
     * </ol>
     *
     * @param skeleton 项目骨架
     * @return 按分数降序排列的节点评分列表
     */
    public List<NodeScore> scoreNodes(ProjectSkeleton skeleton) {
        // 1. 构建依赖有向图
        DefaultDirectedGraph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        // 收集所有唯一文件路径作为图顶点
        Set<String> allFiles = skeleton.getFileNodes().stream()
                .map(AstNode::getFilePath)
                .collect(Collectors.toSet());

        for (String file : allFiles) {
            graph.addVertex(file);
        }

        // 添加依赖边
        Map<String, Integer> importCountMap = new HashMap<>();
        for (ProjectSkeleton.DependencyEdge edge : skeleton.getDependencies()) {
            String target = resolveTarget(edge.getTarget(), allFiles);
            if (target != null) {
                if (!graph.containsVertex(target)) {
                    graph.addVertex(target);
                }
                try {
                    graph.addEdge(edge.getSource(), target);
                } catch (Exception ignored) {
                    // 重复边
                }
                importCountMap.merge(target, 1, Integer::sum);
            }
        }

        // 2. 计算 PageRank
        Map<String, Double> pageRankScores = new HashMap<>();
        if (graph.vertexSet().size() > 1) {
            try {
                PageRank<String, DefaultEdge> pageRank = new PageRank<>(graph, 0.85);
                for (String vertex : graph.vertexSet()) {
                    pageRankScores.put(vertex, pageRank.getVertexScore(vertex));
                }
            } catch (Exception e) {
                log.warn("PageRank 计算异常: {}", e.getMessage());
                for (String vertex : graph.vertexSet()) {
                    pageRankScores.put(vertex, 1.0 / graph.vertexSet().size());
                }
            }
        } else {
            for (String vertex : graph.vertexSet()) {
                pageRankScores.put(vertex, 1.0);
            }
        }

        // 3. 综合打分
        List<NodeScore> scores = new ArrayList<>();
        for (AstNode fileNode : skeleton.getFileNodes()) {
            String filePath = fileNode.getFilePath();

            // 维度1: PageRank
            double prScore = pageRankScores.getOrDefault(filePath, 0.0);

            // 维度2: 被引用次数（调用热度）
            double refScore = importCountMap.getOrDefault(filePath, 0) * 0.1;

            // 维度3: 代码体量（类和方法数量）
            int classCount = 0, methodCount = 0;
            for (AstNode child : fileNode.getChildren()) {
                if ("class".equals(child.getNodeType()) || "interface".equals(child.getNodeType())) {
                    classCount++;
                    methodCount += child.getChildren().size();
                }
            }
            double complexityScore = (classCount * 0.3 + methodCount * 0.05);

            double totalScore = prScore * 100 + refScore + complexityScore;
            scores.add(new NodeScore(filePath, extractMainClassName(fileNode), totalScore));
        }

        Collections.sort(scores);
        return scores;
    }

    /**
     * 基于打分结果执行剪枝：只保留前 (1 - compressionRatio) 的节点。
     *
     * @param skeleton         项目骨架
     * @param compressionRatio 压缩比（0.0~1.0），0.9 表示移除 90% 的低分节点
     * @return 剪枝后的精简 ProjectSkeleton
     */
    public ProjectSkeleton prune(ProjectSkeleton skeleton, double compressionRatio) {
        List<NodeScore> scores = scoreNodes(skeleton);
        int keepCount = Math.max(1, (int) Math.ceil(scores.size() * (1 - compressionRatio)));

        Set<String> keepFiles = scores.stream()
                .limit(keepCount)
                .map(NodeScore::filePath)
                .collect(Collectors.toSet());

        List<AstNode> prunedFiles = skeleton.getFileNodes().stream()
                .filter(f -> keepFiles.contains(f.getFilePath()))
                .collect(Collectors.toList());

        List<ProjectSkeleton.DependencyEdge> prunedEdges = skeleton.getDependencies().stream()
                .filter(e -> keepFiles.contains(e.getSource()))
                .collect(Collectors.toList());

        int totalClasses = 0, totalMethods = 0;
        for (AstNode f : prunedFiles) {
            for (AstNode c : f.getChildren()) {
                if ("class".equals(c.getNodeType()) || "interface".equals(c.getNodeType())) {
                    totalClasses++;
                    totalMethods += c.getChildren().size();
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
                .build();
    }

    /**
     * 尝试将 import 目标解析为已知文件路径
     */
    private String resolveTarget(String importTarget, Set<String> allFiles) {
        // 尝试直接匹配
        for (String file : allFiles) {
            // 简单匹配：import 包含文件名
            String fileName = file.contains("/") ? file.substring(file.lastIndexOf('/') + 1) : file;
            String nameWithoutExt = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.'))
                    : fileName;
            if (importTarget.contains(nameWithoutExt)) {
                return file;
            }
        }
        return null;
    }

    /**
     * 提取文件中的主类名
     */
    private String extractMainClassName(AstNode fileNode) {
        for (AstNode child : fileNode.getChildren()) {
            if ("class".equals(child.getNodeType()) || "interface".equals(child.getNodeType())) {
                return child.getName();
            }
        }
        return fileNode.getFilePath();
    }
}
