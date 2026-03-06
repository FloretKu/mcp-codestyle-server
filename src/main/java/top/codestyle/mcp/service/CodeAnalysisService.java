package top.codestyle.mcp.service;

import lombok.RequiredArgsConstructor;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.config.ConditionalOnCodestyleToolGroup;

/**
 * P4: 仅当 codestyle.tool-group=code-analysis 或 all 时注册，暴露 2 个代码分析 MCP 工具，减少 tools/list 体积。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnCodestyleToolGroup("code-analysis")
public class CodeAnalysisService {

    private final CodestyleService codestyleService;

    @McpTool(name = "extractProjectSkeleton", description = "[必填projectPath] AST解析项目骨架. 首次调用返回骨架,后续自动缓存. 再调exploreCodeContext探索代码.")
    public String extractProjectSkeleton(
            @McpToolParam(description = "项目目录绝对路径") String projectPath,
            @McpToolParam(required = false, description = "详情级别 1-4: 1=目录概览, 2=类骨架(推荐), 3=方法签名, 4=完整") Integer detailLevel,
            @McpToolParam(required = false, description = "可选: 聚焦的子目录路径(如 src/main)，仅解析该目录") String focusPath,
            @McpToolParam(required = false, description = "强制刷新缓存(默认false)") Boolean forceRefresh) {
        return codestyleService.extractProjectSkeleton(projectPath, detailLevel, focusPath, forceRefresh);
    }

    @McpTool(name = "exploreCodeContext", description = "[必填projectPath,mode,query] mode=search(单关键词)/expand(类名或文件路径,可加lineRange)/trace(依赖追踪). 需先调extractProjectSkeleton.")
    public String exploreCodeContext(
            @McpToolParam(description = "项目目录绝对路径（需先调用 extractProjectSkeleton）") String projectPath,
            @McpToolParam(description = "模式: expand / trace / search") String mode,
            @McpToolParam(description = "查询目标: 文件路径、类名、方法名或搜索关键词。expand 支持 | 分隔批量，如 'file1.py:100-200|file2.py:300-400'") String query,
            @McpToolParam(required = false, description = "trace 模式方向: upstream / downstream / both (默认 both)") String direction,
            @McpToolParam(required = false, description = "最大遍历深度 (默认 3)") Integer maxDepth,
            @McpToolParam(required = false, description = "expand 模式可选: 行范围如 '100-200'（1基，含端点）") String lineRange) {
        return codestyleService.exploreCodeContext(projectPath, mode, query, direction, maxDepth, lineRange);
    }
}
