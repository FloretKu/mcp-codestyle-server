package top.codestyle.mcp.service;

import lombok.extern.slf4j.Slf4j;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.model.ast.AstNode;
import top.codestyle.mcp.model.ast.DependencyGraph;
import top.codestyle.mcp.model.ast.ProjectSkeleton;

import java.util.*;

/**
 * 富依赖图构建引擎
 * <p>
 * 从 ProjectSkeleton 构建四类边：contains, import, extends, implements, invokes。
 * 提供精确的 import 目标解析（Java/Python/JS/TS/Go）。
 *
 * @since 2.1.0
 */
@Slf4j
@Service
public class DependencyGraphBuilder {

    /**
     * 从骨架构建依赖图并补充 contains/extends/implements/invokes 边到 skeleton.dependencies。
     *
     * @param skeleton 已解析的项目骨架（含 import 边）
     * @return 封装好的 DependencyGraph（JGraphT 图 + 节点索引）
     */
    public DependencyGraph build(ProjectSkeleton skeleton) {
        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> graph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        Map<String, AstNode> nodeIndex = new HashMap<>();
        Map<String, String> edgeTypeIndex = new HashMap<>();
        Set<String> allFiles = new HashSet<>();

        for (AstNode f : skeleton.getFileNodes()) {
            String path = f.getFilePath();
            allFiles.add(path);
            String fileId = "file:" + path;
            graph.addVertex(fileId);
            nodeIndex.put(fileId, f);

            String dirPath = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "";
            if (!dirPath.isEmpty()) {
                String dirId = "dir:" + dirPath;
                if (!graph.containsVertex(dirId)) {
                    graph.addVertex(dirId);
                    nodeIndex.put(dirId, AstNode.builder().nodeType("directory").name(dirPath).filePath(dirPath).build());
                }
                addEdge(graph, edgeTypeIndex, dirId, fileId, "contains", 1.0);
            }

            if (f.getChildren() == null) continue;
            String currentClass = null;
            for (AstNode child : f.getChildren()) {
                String nt = child.getNodeType();
                if ("class".equals(nt) || "interface".equals(nt) || "enum".equals(nt)) {
                    currentClass = child.getName();
                    String classId = "class:" + path + ":" + currentClass;
                    graph.addVertex(classId);
                    nodeIndex.put(classId, child);
                    addEdge(graph, edgeTypeIndex, fileId, classId, "contains", 1.0);
                    skeleton.getDependencies().add(ProjectSkeleton.DependencyEdge.builder()
                            .source(fileId).target(classId).type("contains").weight(1.0).build());

                    List<String> classRefs = child.getReferences();
                    if (classRefs == null) classRefs = List.of();
                    for (String ref : classRefs) {
                        List<String> refNames = parseClassRefNames(ref);
                        boolean isImplements = ref.toLowerCase().contains("implement");
                        String edgeType = isImplements ? "implements" : "extends";
                        for (String name : refNames) {
                            String targetClassId = resolveClassRef(name, allFiles, skeleton);
                            if (targetClassId != null) {
                                addEdge(graph, edgeTypeIndex, classId, targetClassId, edgeType, 1.0);
                                skeleton.getDependencies().add(ProjectSkeleton.DependencyEdge.builder()
                                        .source(classId).target(targetClassId).type(edgeType).weight(1.0).build());
                            }
                        }
                    }
                } else if ("method".equals(nt)) {
                    String methodName = child.getName();
                    String methodId = currentClass != null
                            ? "method:" + path + ":" + currentClass + "." + methodName
                            : "method:" + path + ":" + methodName;
                    graph.addVertex(methodId);
                    nodeIndex.put(methodId, child);
                    if (currentClass != null) {
                        String classId = "class:" + path + ":" + currentClass;
                        addEdge(graph, edgeTypeIndex, classId, methodId, "contains", 1.0);
                    } else {
                        addEdge(graph, edgeTypeIndex, fileId, methodId, "contains", 1.0);
                    }
                    List<String> invRefs = child.getReferences();
                    if (invRefs == null) invRefs = List.of();
                    for (String inv : invRefs) {
                        String targetId = resolveInvocation(inv, path, currentClass, skeleton, nodeIndex);
                        if (targetId != null) {
                            addEdge(graph, edgeTypeIndex, methodId, targetId, "invokes", 0.5);
                            skeleton.getDependencies().add(ProjectSkeleton.DependencyEdge.builder()
                                    .source(methodId).target(targetId).type("invokes").weight(0.5).build());
                        }
                    }
                }
            }
        }

        List<ProjectSkeleton.DependencyEdge> importEdges = new ArrayList<>();
        for (ProjectSkeleton.DependencyEdge edge : skeleton.getDependencies()) {
            if ("import".equals(edge.getType())) importEdges.add(edge);
        }
        for (ProjectSkeleton.DependencyEdge edge : importEdges) {
            String targetFile = resolveImportTarget(edge.getTarget(), allFiles, skeleton);
            if (targetFile != null) {
                String srcId = edge.getSource().startsWith("file:") ? edge.getSource() : "file:" + edge.getSource();
                String tgtId = "file:" + targetFile;
                if (graph.containsVertex(srcId) && graph.containsVertex(tgtId)) {
                    addEdge(graph, edgeTypeIndex, srcId, tgtId, "import", edge.getWeight());
                }
            }
        }

        return new DependencyGraph(graph, nodeIndex, edgeTypeIndex);
    }

    private void addEdge(DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> graph,
                        Map<String, String> edgeTypeIndex,
                        String src, String tgt, String type, double weight) {
        if (!graph.containsVertex(src)) graph.addVertex(src);
        if (!graph.containsVertex(tgt)) graph.addVertex(tgt);
        try {
            DefaultWeightedEdge e = graph.addEdge(src, tgt);
            if (e != null) graph.setEdgeWeight(e, weight);
            if (edgeTypeIndex != null && type != null) {
                edgeTypeIndex.put(DependencyGraph.edgeKey(src, tgt), type);
            }
        } catch (Exception ignored) {}
    }

    private String resolveImportTarget(String importText, Set<String> allFiles, ProjectSkeleton skeleton) {
        if (importText == null || importText.isBlank()) return null;
        String trimmed = importText.trim();
        for (AstNode f : skeleton.getFileNodes()) {
            String path = f.getFilePath();
            String pathNoExt = path.contains(".") ? path.substring(0, path.lastIndexOf('.')) : path;
            String pathWithDots = pathNoExt.replace("/", ".");
            if (trimmed.equals(pathWithDots) || trimmed.startsWith(pathWithDots + ".")) return path;
            String simple = pathNoExt.contains("/") ? pathNoExt.substring(pathNoExt.lastIndexOf('/') + 1) : pathNoExt;
            if (trimmed.endsWith("." + simple) || trimmed.equals(simple)) return path;
        }
        String pathForm = trimmed.replace('.', '/');
        for (String file : allFiles) {
            if (file.equals(pathForm + ".java") || file.equals(pathForm + ".py") ||
                    file.equals(pathForm + ".ts") || file.equals(pathForm + ".tsx") ||
                    file.equals(pathForm + ".js") || file.equals(pathForm + ".go")) return file;
            if (file.endsWith("/" + pathForm + ".java") || file.endsWith("/" + pathForm + ".py")) return file;
        }
        String simple = trimmed.contains(".") ? trimmed.substring(trimmed.lastIndexOf('.') + 1) : trimmed;
        for (String file : allFiles) {
            if (file.endsWith(simple + ".java") || file.endsWith(simple + ".py") ||
                    file.endsWith(simple + ".ts") || file.endsWith(simple + ".tsx")) {
                return file;
            }
        }
        return null;
    }

    private List<String> parseClassRefNames(String ref) {
        if (ref == null || ref.isBlank()) return List.of();
        String s = ref.replaceFirst("^(?i)extends\\s+", "").replaceFirst("^(?i)implements\\s+", "").trim();
        String[] parts = s.split(",");
        List<String> names = new ArrayList<>();
        for (String p : parts) {
            String name = p.replaceAll("[<>\\s]+", "").trim();
            if (name.contains(".")) name = name.substring(name.lastIndexOf('.') + 1);
            if (!name.isEmpty()) names.add(name);
        }
        return names;
    }

    private String resolveClassRef(String simpleName, Set<String> allFiles, ProjectSkeleton skeleton) {
        if (simpleName == null || simpleName.isBlank()) return null;
        String simple = simpleName.replaceAll("[<>,\\s]+", "").trim();
        if (simple.contains(".")) simple = simple.substring(simple.lastIndexOf('.') + 1);
        for (AstNode f : skeleton.getFileNodes()) {
            if (f.getChildren() == null) continue;
            for (AstNode c : f.getChildren()) {
                if (("class".equals(c.getNodeType()) || "interface".equals(c.getNodeType()) || "enum".equals(c.getNodeType()))
                        && simple.equals(c.getName())) {
                    return "class:" + f.getFilePath() + ":" + c.getName();
                }
            }
        }
        return null;
    }

    private String resolveInvocation(String invName, String currentFile, String currentClass,
                                    ProjectSkeleton skeleton, Map<String, AstNode> nodeIndex) {
        if (invName == null || invName.isBlank()) return null;
        String simple = invName.contains(".") ? invName.substring(invName.lastIndexOf('.') + 1) : invName;
        for (Map.Entry<String, AstNode> e : nodeIndex.entrySet()) {
            if (!e.getKey().startsWith("method:")) continue;
            AstNode n = e.getValue();
            if (simple.equals(n.getName())) return e.getKey();
        }
        for (Map.Entry<String, AstNode> e : nodeIndex.entrySet()) {
            if (!e.getKey().startsWith("class:")) continue;
            if (e.getKey().contains(":" + simple)) return e.getKey();
        }
        return null;
    }
}
