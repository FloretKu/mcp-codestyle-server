package top.codestyle.mcp.model.ast;

import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * 依赖图谱数据模型
 * <p>
 * 封装 JGraphT 有向加权图及节点索引，用于多粒度 PageRank、遍历与检索。
 * 节点 ID 格式：dir:path, file:path, class:filePath:ClassName, method:filePath:ClassName.methodName
 * 边类型：contains, import, extends, implements, invokes
 *
 * @since 2.1.0
 */
public class DependencyGraph {

    private final DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> graph;
    private final Map<String, AstNode> nodeIndex;
    private final Map<String, String> edgeTypeIndex;

    public DependencyGraph(DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> graph,
                          Map<String, AstNode> nodeIndex,
                          Map<String, String> edgeTypeIndex) {
        this.graph = graph != null ? graph : new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        this.nodeIndex = nodeIndex != null ? Collections.unmodifiableMap(nodeIndex) : Map.of();
        this.edgeTypeIndex = edgeTypeIndex != null ? Collections.unmodifiableMap(edgeTypeIndex) : Map.of();
    }

    public DependencyGraph(DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> graph,
                           Map<String, AstNode> nodeIndex) {
        this(graph, nodeIndex, Map.of());
    }

    public DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> getGraph() {
        return graph;
    }

    public Map<String, AstNode> getNodeIndex() {
        return nodeIndex;
    }

    public String getEdgeType(String src, String tgt) {
        if (src == null || tgt == null) return null;
        return edgeTypeIndex.get(edgeKey(src, tgt));
    }

    public Map<String, String> getEdgeTypeIndex() {
        return edgeTypeIndex;
    }

    public static String edgeKey(String src, String tgt) {
        return Objects.toString(src, "") + "|" + Objects.toString(tgt, "");
    }
}
