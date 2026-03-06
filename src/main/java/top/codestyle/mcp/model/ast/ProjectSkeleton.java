package top.codestyle.mcp.model.ast;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目骨架模型
 * <p>
 * 封装整个项目经 AST 解析后的结构化全貌：
 * 层级代码树 (HCT)、目录树、模块依赖列表、文件摘要、以及汇总统计信息。
 *
 * @since 2.1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectSkeleton {

    /**
     * 项目根路径
     */
    private String projectPath;

    /**
     * 解析到的文件总数
     */
    private int totalFiles;

    /**
     * 解析到的类总数
     */
    private int totalClasses;

    /**
     * 解析到的方法总数
     */
    private int totalMethods;

    /**
     * 所有文件中解析出的 AST 节点列表（顶层节点，即文件级）
     */
    @Builder.Default
    private List<AstNode> fileNodes = new ArrayList<>();

    /**
     * 模块依赖边列表 (source -> target)，类型含 import, contains, extends, implements, invokes
     */
    @Builder.Default
    private List<DependencyEdge> dependencies = new ArrayList<>();

    /**
     * 目录层级树节点（根目录及子目录，nodeType=directory）
     */
    @Builder.Default
    private List<AstNode> directoryNodes = new ArrayList<>();

    /**
     * 文件级摘要（文件相对路径 -> 基于 AST 规则生成的一行摘要，不调用 LLM）
     */
    @Builder.Default
    private Map<String, String> fileSummaries = new LinkedHashMap<>();

    /**
     * 模块依赖边
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DependencyEdge {
        /** 源节点标识（文件路径或 class:file:ClassName 或 method:file:Class.method） */
        private String source;
        /** 目标节点标识 */
        private String target;
        /** 依赖类型: import, contains, extends, implements, invokes */
        private String type;
        /** 边权重（用于加权 PageRank，默认 1.0） */
        @Builder.Default
        private double weight = 1.0;
    }
}
