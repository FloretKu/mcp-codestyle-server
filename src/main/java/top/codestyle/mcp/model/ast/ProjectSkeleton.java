package top.codestyle.mcp.model.ast;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 项目骨架模型
 * <p>
 * 封装整个项目经 AST 解析后的结构化全貌：
 * 层级代码树 (HCT)、模块依赖列表、以及汇总统计信息。
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
     * 模块依赖边列表 (source -> target)
     */
    @Builder.Default
    private List<DependencyEdge> dependencies = new ArrayList<>();

    /**
     * 模块依赖边
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DependencyEdge {
        /** 源文件路径 */
        private String source;
        /** 目标模块/类名 */
        private String target;
        /** 依赖类型 (import, extends, implements, call) */
        private String type;
    }
}
