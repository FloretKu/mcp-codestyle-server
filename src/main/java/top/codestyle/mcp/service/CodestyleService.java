package top.codestyle.mcp.service;

import lombok.RequiredArgsConstructor;

import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.config.RepositoryConfig;
import top.codestyle.mcp.model.ast.ProjectSkeleton;
import top.codestyle.mcp.model.local.LocalDeleteResult;
import top.codestyle.mcp.model.local.LocalUploadResult;
import top.codestyle.mcp.model.template.TemplateContent;
import top.codestyle.mcp.model.template.TemplateMetaInfo;
import top.codestyle.mcp.model.remote.RemoteSearchResult;
import top.codestyle.mcp.model.tree.TreeNode;
import top.codestyle.mcp.util.PromptUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import top.codestyle.mcp.model.ast.AstNode;
import top.codestyle.mcp.model.ast.DependencyGraph;

/**
 * 代码模板 MCP 工具服务
 * <p>
 * 封装面向 AI 大模型的 MCP Tool，提供模板搜索、内容获取、
 * 上传和删除等工具接口，放在 MCP 协议层与具体业务逻辑之间的编排层。
 * <p>
 * v2.1.0 新增：项目骨架提取、智能剪枝、意图分析、沙盒验证等深度研究工具。
 *
 * @author 小航love666, Kanttha, movclantian
 * @since 2025-12-03
 */
@Service
@RequiredArgsConstructor
public class CodestyleService {

    private static final int MCP_MAX_INLINE_CHARS = OutputGuardService.DEFAULT_MAX_INLINE_CHARS;

    private final TemplateService templateService;
    private final PromptService promptService;
    private final LuceneIndexService luceneIndexService;
    private final RepositoryConfig repositoryConfig;
    private final AstParsingService astParsingService;
    private final RepoMasterScoringService scoringService;
    private final DependencyGraphBuilder dependencyGraphBuilder;
    private final SkeletonFormatterService skeletonFormatterService;
    private final SkeletonCacheService skeletonCacheService;
    private final TemplateWatcherService watcherService;
    private final SandboxVerificationService sandboxService;
    private final OutputGuardService outputGuardService;

    /** P5: 前 2 次 explore 响应附加 hint，后续省略以减少重复 token */
    private final AtomicInteger exploreCallCount = new AtomicInteger(0);

    /**
     * 搜索代码模板
     * <p>
     * 根据模板提示词搜索模板信息，返回目录树和模板组介绍。
     * 支持本地Lucene检索和远程检索两种模式。
     *
     * @param templateKeyword 模板提示词，支持关键词或 groupId/artifactId 格式，如: CRUD, backend,
     *                        frontend, continew/DatabaseConfig
     * @return 模板目录树和描述信息字符串
     */
    public String codestyleSearch(
            @McpToolParam(description = "模板提示词，如: CRUD, bankend, frontend等") String templateKeyword) {
        try {
            // 远程检索模式
            if (templateService.isRemoteSearchEnabled()) {
                List<RemoteSearchResult> remoteResults = templateService.searchFromRemote(templateKeyword);

                // 远程返回空结果，自动fallback到本地Lucene检索
                if (remoteResults.isEmpty()) {
                    return searchLocalFallback(templateKeyword, true);
                }

                // 单个结果：精确匹配，下载到本地并返回目录树
                if (remoteResults.size() == 1) {
                    RemoteSearchResult result = remoteResults.get(0);

                    // 下载模板
                    boolean downloaded = templateService.downloadTemplate(result);
                    if (!downloaded) {
                        return "远程模板下载失败: " + result.getGroupId() + "/" + result.getArtifactId() + "/"
                                + result.getVersion();
                    }

                    List<TemplateMetaInfo> metaInfos = templateService.searchLocalRepository(
                            result.getGroupId(), result.getArtifactId());

                    if (metaInfos.isEmpty()) {
                        return "本地仓库模板文件不完整,请检查模板目录";
                    }

                    TreeNode treeNode = PromptUtils.buildTree(metaInfos);
                    String treeStr = PromptUtils.buildTreeStr(treeNode, "").trim();

                    // 使用 snippet 作为描述（如果没有则使用 title）
                    String description = result.getSnippet() != null ? result.getSnippet() : result.getTitle();
                    return promptService.buildSearchResult(result.getArtifactId(), treeStr, description);
                }

                // 多个结果：返回列表让AI选择
                return promptService.buildRemoteSearchResultResponse(templateKeyword, remoteResults);
            }

            // 本地Lucene全文检索模式
            return searchLocalFallback(templateKeyword, false);
        } catch (Exception e) {
            return "模板搜索失败: " + e.getMessage();
        }
    }

    /**
     * 本地 Lucene 检索（兜底策略）
     * <p>
     * 当远程检索不可用或返回空结果时，回退到本地全文检索。
     *
     * @param templateKeyword    搜索关键词
     * @param fromRemoteFallback 是否由远程检索降级触发
     * @return 格式化的检索结果字符串
     */
    private String searchLocalFallback(String templateKeyword, boolean fromRemoteFallback) {
        List<LuceneIndexService.SearchResult> searchResults = luceneIndexService.fetchLocalMetaConfig(templateKeyword);

        if (searchResults.isEmpty()) {
            if (fromRemoteFallback) {
                return promptService.buildRemoteUnavailable(templateKeyword);
            }
            return promptService.buildLocalNotFound(repositoryConfig.getRepositoryDir(), templateKeyword);
        }

        // 检查是否为同一groupId的多个模板（命名空间搜索）
        if (templateService.isGroupIdSearch(searchResults)) {
            return templateService.buildGroupAggregatedResult(templateKeyword, searchResults);
        }

        // 处理多个不同模板的情况（让AI选择）
        if (searchResults.size() > 1) {
            return templateService.buildMultiResultResponse(templateKeyword, searchResults);
        }

        // 单模板结果
        LuceneIndexService.SearchResult searchResult = searchResults.get(0);
        List<TemplateMetaInfo> metaInfos = templateService.searchLocalRepository(
                searchResult.groupId(), searchResult.artifactId());

        if (metaInfos.isEmpty()) {
            return "本地仓库模板文件不完整,请检查模板目录";
        }

        TreeNode treeNode = PromptUtils.buildTree(metaInfos);
        String treeStr = PromptUtils.buildTreeStr(treeNode, "").trim();
        return promptService.buildSearchResult(searchResult.artifactId(), treeStr, searchResult.description());
    }

    /**
     * 获取模板文件内容
     * <p>
     * 根据完整的模板文件路径获取详细内容，包括变量说明和模板代码
     *
     * @param templatePath 完整模板文件路径（包含版本号和.ftl扩展名），如:
     *                     backend/CRUD/1.0.0/src/main/java/com/air/controller/Controller.ftl
     * @return 模板文件的详细信息字符串（包含变量说明和模板内容）
     * @throws IOException 文件读取异常
     */
    public String getTemplateByPath(
            @McpToolParam(description = "模板文件路径,如:backend/CRUD/1.0.0/src/main/java/com/air/controller/Controller.ftl") String templatePath)
            throws IOException {

        // 使用精确路径搜索模板
        TemplateContent matchedTemplate = templateService.searchByPath(templatePath);

        // 校验搜索结果
        if (matchedTemplate == null) {
            return String.format("未找到路径为 '%s' 的模板文件,请检查路径是否正确。", templatePath);
        }

        // 构建变量信息
        Map<String, String> vars = new LinkedHashMap<>();
        if (matchedTemplate.getInputVariables() != null && !matchedTemplate.getInputVariables().isEmpty()) {
            for (var variable : matchedTemplate.getInputVariables()) {
                String desc = String.format("%s（示例：%s）[%s]",
                        variable.getVariableComment(),
                        variable.getExample(),
                        variable.getVariableType());
                vars.put(variable.getVariableName(), desc);
            }
        }

        // 使用PromptUtils格式化变量信息
        String varInfo = vars.isEmpty() ? "无变量" : PromptUtils.buildVarString(vars).trim();

        // 使用PromptService模板构建最终输出
        return promptService.buildPrompt(
                templatePath,
                varInfo,
                matchedTemplate.getTemplateContent() != null ? matchedTemplate.getTemplateContent() : "");
    }

    /**
     * 从文件系统上传模板到本地仓库或远程服务器
     * <p>
     * 本地模式：复制到本地缓存并重建索引
     * <p>
     * 远程模式：复制到本地缓存、上传到远程服务器并重建索引
     *
     * @param sourcePath 文件系统路径，如: E:/templates/CRUD
     * @param groupId    组ID，如: continew
     * @param artifactId 项目ID，如: CRUD
     * @param version    版本号，如: 1.0.0
     * @param overwrite  是否覆盖已存在的版本（可选，默认 false）
     * @return 上传结果信息字符串
     */
    public String uploadTemplateFromFileSystem(
            @McpToolParam(description = "文件系统路径，如: E:/templates/CRUD") String sourcePath,
            @McpToolParam(description = "组ID，如: continew") String groupId,
            @McpToolParam(description = "项目ID，如: CRUD") String artifactId,
            @McpToolParam(description = "版本号，如: 1.0.0") String version,
            @McpToolParam(description = "是否覆盖已存在的版本（可选，默认 false）") Boolean overwrite) {
        try {
            boolean shouldOverwrite = overwrite != null && overwrite;

            // 判断是否启用远程模式
            if (templateService.isRemoteSearchEnabled()) {
                // 远程模式：上传到远程服务器
                LocalUploadResult result = templateService.uploadTemplateFromFileSystemRemote(sourcePath, groupId,
                        artifactId, version, shouldOverwrite);

                return String.format("""
                        ✓ 模板已上传
                        - 源路径: %s
                        - 本地路径: %s
                        - 远程 ID: %s
                        - 文件数: %d
                        - 索引已更新
                        """,
                        sourcePath,
                        result.getLocalPath(),
                        result.getRemoteId(),
                        result.getFileCount());
            } else {
                // 本地模式：只保存到本地
                LocalUploadResult result = templateService.saveTemplateFromFileSystemLocal(sourcePath, groupId,
                        artifactId, version, shouldOverwrite);

                return String.format("""
                        ✓ 模板已保存到本地
                        - 源路径: %s
                        - 本地路径: %s
                        - 文件数: %d
                        - 索引已更新
                        """,
                        sourcePath,
                        result.getLocalPath(),
                        result.getFileCount());
            }
        } catch (Exception e) {
            String message = e.getMessage();
            if (message != null && message.contains("模板路径格式错误")) {
                message = message + "，请传入仓库相对路径，例如: continew/CRUD/1.0.0（不要包含 codestyle-cache 前缀）";
            }
            return "✗ 上传失败: " + message;
        }
    }

    /**
     * 上传模板到本地仓库或远程服务器
     * <p>
     * 本地模式：保存到本地缓存并重建索引
     * <p>
     * 远程模式：保存到本地缓存、上传到远程服务器并重建索引
     *
     * @param templatePath 模板路径，格式: groupId/artifactId/version，如:
     *                     continew/CRUD/1.0.0
     * @param overwrite    是否覆盖已存在的版本（可选，默认 false）
     * @return 上传结果信息字符串
     */
    public String uploadTemplate(
            @McpToolParam(description = "模板路径，格式: groupId/artifactId/version，如: continew/CRUD/1.0.0") String templatePath,
            @McpToolParam(description = "是否覆盖已存在的版本（可选，默认 false）") Boolean overwrite) {
        try {
            boolean shouldOverwrite = overwrite != null && overwrite;

            // 判断是否启用远程模式
            if (templateService.isRemoteSearchEnabled()) {
                // 远程模式：上传到远程服务器
                LocalUploadResult result = templateService.uploadTemplateRemote(templatePath, shouldOverwrite);

                return String.format("""
                        ✓ 模板已上传
                        - 本地路径: %s
                        - 远程 ID: %s
                        - 文件数: %d
                        - 索引已更新
                        """,
                        result.getLocalPath(),
                        result.getRemoteId(),
                        result.getFileCount());
            } else {
                // 本地模式：只保存到本地
                LocalUploadResult result = templateService.saveTemplateLocal(templatePath, shouldOverwrite);

                return String.format("""
                        ✓ 模板已保存到本地
                        - 路径: %s
                        - 文件数: %d
                        - 索引已更新
                        """,
                        result.getLocalPath(),
                        result.getFileCount());
            }
        } catch (Exception e) {
            return "✗ 上传失败: " + e.getMessage();
        }
    }

    /**
     * 删除指定版本的模板
     * <p>
     * 本地模式：删除本地缓存并重建索引
     * <p>
     * 远程模式：删除本地缓存、删除远程模板并重建索引
     *
     * @param templatePath 模板路径，格式: groupId/artifactId/version，如:
     *                     continew/CRUD/1.0.0
     * @return 删除结果信息字符串
     */
    public String deleteTemplate(
            @McpToolParam(description = "模板路径，格式: groupId/artifactId/version，如: continew/CRUD/1.0.0") String templatePath) {
        try {
            // 判断是否启用远程模式
            if (templateService.isRemoteSearchEnabled()) {
                // 远程模式：删除远程模板
                LocalDeleteResult result = templateService.deleteTemplateRemote(templatePath);

                return String.format("""
                        ✓ 模板已删除
                        - 本地路径: %s/%s/%s
                        - 远程 ID: %s/%s/%s
                        - 索引已更新
                        """,
                        result.getGroupId(), result.getArtifactId(), result.getVersion(),
                        result.getGroupId(), result.getArtifactId(), result.getVersion());
            } else {
                // 本地模式：只删除本地
                LocalDeleteResult result = templateService.deleteTemplateLocal(templatePath);

                return String.format("""
                        ✓ 模板已删除
                        - 路径: %s/%s/%s
                        - 索引已更新
                        """,
                        result.getGroupId(), result.getArtifactId(), result.getVersion());
            }
        } catch (Exception e) {
            return "✗ 删除失败: " + e.getMessage();
        }
    }

    // ========================= 深度研究工具 (v2.1.0) =========================

    /**
     * 提取项目骨架
     * <p>
     * 对指定目录执行多语言 AST 深度解析，构建层级代码树 (HCT) 与
     * 多类型模块依赖图 (MDG)，通过 PageRank + 复合评分智能剪枝，
     * 以 Markdown+XML 混合格式返回精简骨架。
     *
     * @param projectPath  项目目录绝对路径
     * @param detailLevel  详情级别 1–4：1=目录概览，2=类骨架(推荐)，3=方法签名+docstring，4=完整
     * @param focusPath    可选，聚焦子目录相对路径（如 src/main），仅解析该目录
     * @return Markdown+XML 格式的项目骨架
     */
    public String extractProjectSkeleton(
            @McpToolParam(description = "项目目录绝对路径") String projectPath,
            @McpToolParam(required = false, description = "详情级别 1-4: 1=目录概览, 2=类骨架(推荐), 3=方法签名, 4=完整") Integer detailLevel,
            @McpToolParam(required = false, description = "可选: 聚焦的子目录路径(如 src/main)，仅解析该目录") String focusPath,
            @McpToolParam(required = false, description = "强制刷新缓存(默认false)") Boolean forceRefresh) {
        try {
            if (projectPath == null || projectPath.isBlank()) {
                return "✗ 缺少参数 projectPath（项目目录绝对路径）";
            }
            boolean refresh = forceRefresh != null && forceRefresh;
            if (!refresh) {
                SkeletonCacheService.CachedSkeleton existing = skeletonCacheService.get(projectPath);
                if (existing != null && existing.skeleton() != null) {
                    ProjectSkeleton sk = existing.skeleton();
                    int files = sk.getTotalFiles();
                    int classes = sk.getTotalClasses();
                    int methods = sk.getTotalMethods();
                    long ageSec = (System.currentTimeMillis() - existing.buildTime()) / 1000;
                    List<String> topClasses = extractTopClassNames(sk, 10);
                    List<String> topMethods = extractTopMethodNames(sk, 5);
                    List<String> fileNames = extractFileNames(sk, 8);
                    return String.format(
                            "[cached] 骨架已缓存 (%d文件/%d类/%d方法, %.0f秒前构建)%n" +
                            "核心类: %s%n" +
                            "核心方法: %s%n" +
                            "文件: %s%n" +
                            "--- 查询示例 ---%n" +
                            "search(\"类名\") | expand(\"文件路径\") | expand(\"类名\") | trace(\"类名\")%n" +
                            "expand 支持 lineRange 参数精确定位，如 lineRange=\"100-200\"。如需重新解析请传 forceRefresh=true",
                            files, classes, methods, (double) ageSec,
                            topClasses.isEmpty() ? "(无)" : String.join(", ", topClasses),
                            topMethods.isEmpty() ? "(无)" : String.join(", ", topMethods),
                            fileNames.isEmpty() ? "(无)" : String.join(", ", fileNames));
                }
            }
            int level = (detailLevel != null && detailLevel >= 1 && detailLevel <= 4) ? detailLevel : 2;

            // Phase 1: AST 解析（可选 focusPath）
            ProjectSkeleton skeleton = astParsingService.parseProject(projectPath, focusPath);

            // Phase 2: 富依赖图构建
            top.codestyle.mcp.model.ast.DependencyGraph graph = dependencyGraphBuilder.build(skeleton);

            // Phase 3: 智能剪枝（层级化）
            ProjectSkeleton pruned = scoringService.prune(skeleton, level, graph);

            // Phase 4: 取 Top 文件用于标注 ★
            List<RepoMasterScoringService.NodeScore> scores = scoringService.scoreNodes(pruned, graph);
            List<String> topPaths = scores.stream().limit(3).map(RepoMasterScoringService.NodeScore::filePath).toList();

            // 写入缓存供 exploreCodeContext 使用：缓存完整骨架（非 pruned）与 NameIndex
            NameIndex nameIndex = NameIndex.from(graph.getNodeIndex());
            skeletonCacheService.put(projectPath, skeleton, graph, nameIndex);

            // 输出 Markdown+XML
            String formatted = skeletonFormatterService.format(pruned, level, topPaths, MCP_MAX_INLINE_CHARS);
            return outputGuardService.guard(formatted, projectPath, "skeleton", MCP_MAX_INLINE_CHARS);
        } catch (Exception e) {
            return "✗ 项目骨架提取失败: " + e.getMessage();
        }
    }

    /**
     * 基于已解析的骨架与依赖图，按意图检索精准代码上下文。
     * 需先对该项目调用 extractProjectSkeleton 以填充缓存。
     *
     * @param projectPath 项目目录绝对路径
     * @param mode        expand / trace / search
     * @param query       文件路径、类名、方法名或搜索关键词
     * @param direction   trace 模式方向: upstream / downstream / both
     * @param maxDepth    最大遍历深度（默认 3）
     * @return 格式化后的上下文或错误提示
     */
    public String exploreCodeContext(
            @McpToolParam(description = "项目目录绝对路径（需先调用 extractProjectSkeleton）") String projectPath,
            @McpToolParam(description = "模式: expand / trace / search") String mode,
            @McpToolParam(description = "查询目标: 文件路径、类名、方法名或搜索关键词。expand 支持 | 分隔批量，如 'file1.py:100-200|file2.py:300-400'") String query,
            @McpToolParam(required = false, description = "trace 模式方向: upstream / downstream / both (默认 both)") String direction,
            @McpToolParam(required = false, description = "最大遍历深度 (默认 3)") Integer maxDepth,
            @McpToolParam(required = false, description = "expand 模式可选: 行范围如 '100-200'（1基，含端点）") String lineRange) {
        try {
            if (projectPath == null || projectPath.isBlank()) {
                return "✗ 缺少参数 projectPath（项目目录绝对路径）";
            }
            SkeletonCacheService.CachedSkeleton cached = skeletonCacheService.get(projectPath);
            if (cached == null) {
                return "✗ 未找到该项目的骨架缓存，请先对路径 \"" + projectPath + "\" 调用 extractProjectSkeleton。";
            }
            String m = (mode != null && !mode.isBlank()) ? mode.strip().toLowerCase() : "search";
            int depth = (maxDepth != null && maxDepth > 0) ? Math.min(maxDepth, 20) : 3;
            String dir = (direction != null && !direction.isBlank()) ? direction.strip().toLowerCase() : "both";

            String result = switch (m) {
                case "expand" -> runExpand(cached, projectPath, query, lineRange);
                case "trace" -> runTrace(cached, query, dir, depth);
                case "search" -> runSearch(cached, projectPath, query);
                default -> "✗ 未知模式: " + mode + "，支持: expand, trace, search";
            };
            int callNum = exploreCallCount.incrementAndGet();
            if (result != null && !result.startsWith("✗") && callNum <= 2) {
                result = appendNextStepHint(result, m, query);
            }
            return outputGuardService.guard(result, projectPath, "explore-" + m, MCP_MAX_INLINE_CHARS);
        } catch (Exception e) {
            return "✗ exploreCodeContext 失败: " + e.getMessage();
        }
    }

    private static String appendNextStepHint(String result, String mode, String query) {
        if (result == null || result.isBlank()) return result;
        String hint;
        switch (mode) {
            case "search" -> hint = "[提示] expand(\"类名或文件路径\") 查看完整代码; trace(\"类名\") 追踪依赖链";
            case "expand" -> {
                if (result.contains("lineRange") || result.contains("若要查看特定实现")) {
                    hint = "[提示] 大文件可用 lineRange=\"起-止\" 查看特定段; search(\"关键词\") 搜索该文件";
                } else {
                    hint = "[提示] trace(\"类名\") 查看谁调用了它; search(\"方法名\") 搜索引用";
                }
            }
            case "trace" -> hint = "[提示] expand(\"节点名\") 查看代码实现";
            default -> hint = null;
        }
        if (hint == null) return result;
        return result + "\n\n" + hint;
    }

    private String runExpand(SkeletonCacheService.CachedSkeleton cached, String projectPath, String query, String lineRange) {
        if (query == null || query.isBlank()) return "✗ expand 模式需要提供 query（文件路径或类名）。";
        String q = query.strip();
        if (q.contains("|")) {
            return runBatchExpand(cached, projectPath, q);
        }
        Path root = Paths.get(projectPath).toAbsolutePath().normalize();

        List<AstNode> fileNodes = cached.skeleton() != null && cached.skeleton().getFileNodes() != null
                ? cached.skeleton().getFileNodes()
                : List.of();

        // 1) 优先：按文件路径匹配
        for (AstNode f : fileNodes) {
            String relPath = f.getFilePath();
            if (relPath == null) continue;
            if (matchesFile(relPath, q)) {
                return expandFile(root, f, relPath, lineRange);
            }
        }

        // 2) 其次：基于依赖图 nodeIndex 的 globalNameDict 做名称匹配（类/方法/文件）
        Map<String, AstNode> nodeIndex = cached.graph() != null ? cached.graph().getNodeIndex() : Map.of();
        NameIndex nameIndex = cached.nameIndex() != null ? cached.nameIndex() : NameIndex.from(nodeIndex);
        List<String> candidateIds = nameIndex.lookup(q);
        if (!candidateIds.isEmpty()) {
            String picked = pickBestCandidateId(q, candidateIds);
            if (picked != null) {
                String expanded = expandByNodeId(root, nodeIndex, picked, lineRange);
                if (expanded != null) return expanded;
            }
            return renderAmbiguousExpandCandidates(q, candidateIds, nodeIndex);
        }

        // 3) 兼容路径：回退到骨架 children 精确匹配（当缓存中仍保留了类子节点时）
        for (AstNode f : fileNodes) {
            if (f.getChildren() == null) continue;
            String relPath = f.getFilePath();
            if (relPath == null) continue;
            for (AstNode c : f.getChildren()) {
                if (!isTypeNode(c)) continue;
                if (c.getName() != null && c.getName().equalsIgnoreCase(q)) {
                    return expandTypeInFile(root, f, relPath, c);
                }
                if (c.getChildren() != null) {
                    for (AstNode m : c.getChildren()) {
                        if (!"method".equals(m.getNodeType())) continue;
                        if (m.getName() != null && m.getName().equalsIgnoreCase(q)) {
                            return expandMethodInFile(root, f, relPath, m);
                        }
                    }
                }
            }
        }

        return "✗ 未找到匹配的文件或类: " + query;
    }

    /** P3: 批量 expand，query 格式为 "file1:start-end|file2:start-end|..." 或 "file1|file2"，减少 LLM 轮次 */
    private String runBatchExpand(SkeletonCacheService.CachedSkeleton cached, String projectPath, String multiQuery) {
        String[] targets = multiQuery.split("\\|");
        if (targets.length == 0) return "✗ 批量 expand 需要至少一个目标，用 | 分隔。";
        int perTargetBudget = Math.max(500, MCP_MAX_INLINE_CHARS / targets.length);
        StringBuilder sb = new StringBuilder();
        sb.append("## batch expand (").append(targets.length).append(" targets)\n\n");
        for (String t : targets) {
            String part = t != null ? t.strip() : "";
            if (part.isEmpty()) continue;
            String filePart = part;
            String lrPart = null;
            if (part.matches(".*:\\d+-\\d+$")) {
                int idx = part.lastIndexOf(':');
                filePart = part.substring(0, idx).trim();
                lrPart = part.substring(idx + 1).trim();
            }
            String result = runExpand(cached, projectPath, filePart, lrPart);
            if (result != null && !result.startsWith("✗")) {
                if (result.length() > perTargetBudget) {
                    result = result.substring(0, perTargetBudget) + "\n... [截断]";
                }
                sb.append(result).append("\n---\n");
            } else {
                sb.append(part).append(": ").append(result != null ? result : "失败").append("\n---\n");
            }
        }
        return sb.toString().trim();
    }

    private static String pickBestCandidateId(String q, List<String> candidateIds) {
        if (candidateIds == null || candidateIds.isEmpty()) return null;
        String nq = q.strip();
        String qLower = nq.toLowerCase();

        boolean looksLikePath = nq.contains("/") || nq.contains("\\") || qLower.endsWith(".java") || qLower.endsWith(".py")
                || qLower.endsWith(".ts") || qLower.endsWith(".tsx") || qLower.endsWith(".js") || qLower.endsWith(".go");
        boolean looksLikeMethod = !looksLikePath && nq.contains(".");

        if (looksLikePath) {
            for (String id : candidateIds) if (id != null && id.startsWith("file:")) return id;
        }
        if (looksLikeMethod) {
            for (String id : candidateIds) if (id != null && id.startsWith("method:")) return id;
        }
        for (String id : candidateIds) if (id != null && id.startsWith("class:")) return id;
        for (String id : candidateIds) if (id != null && id.startsWith("method:")) return id;
        for (String id : candidateIds) if (id != null && id.startsWith("file:")) return id;
        return candidateIds.get(0);
    }

    private String expandByNodeId(Path root, Map<String, AstNode> nodeIndex, String nodeId, String lineRange) {
        if (nodeId == null || nodeId.isBlank()) return null;
        AstNode node = nodeIndex.get(nodeId);
        if (node == null) return null;

        if (nodeId.startsWith("file:")) {
            String relPath = node.getFilePath();
            if (relPath == null || relPath.isBlank()) {
                relPath = nodeId.substring("file:".length());
            }
            AstNode fileNode = node;
            return expandFile(root, fileNode, relPath, lineRange);
        }

        if (nodeId.startsWith("class:")) {
            String relPath = node.getFilePath();
            if (relPath == null || relPath.isBlank()) {
                // class:<filePath>:<ClassName>
                String body = nodeId.substring("class:".length());
                int idx = body.lastIndexOf(':');
                relPath = idx > 0 ? body.substring(0, idx) : body;
            }
            AstNode fileNode = nodeIndex.get("file:" + relPath);
            if (fileNode == null) {
                // 兜底：构造一个最小 fileNode 供渲染骨架
                fileNode = AstNode.builder().nodeType("file").filePath(relPath).children(List.of()).build();
            }
            return expandTypeInFile(root, fileNode, relPath, node);
        }

        if (nodeId.startsWith("method:")) {
            String relPath = node.getFilePath();
            if (relPath == null || relPath.isBlank()) {
                // method:<filePath>:<...>
                String body = nodeId.substring("method:".length());
                int idx = body.indexOf(':');
                relPath = idx > 0 ? body.substring(0, idx) : body;
            }
            AstNode fileNode = nodeIndex.get("file:" + relPath);
            if (fileNode == null) {
                fileNode = AstNode.builder().nodeType("file").filePath(relPath).children(List.of()).build();
            }
            return expandMethodInFile(root, fileNode, relPath, node);
        }

        return null;
    }

    private String renderAmbiguousExpandCandidates(String query, List<String> candidateIds, Map<String, AstNode> nodeIndex) {
        StringBuilder sb = new StringBuilder();
        sb.append("✗ query 命中多个候选实体，请更精确地指定：\n");
        sb.append("- 建议：传完整文件路径（如 `lightrag/kg/postgres_impl.py`），或先 `mode=search` 缩小范围。\n\n");
        int limit = Math.min(12, candidateIds.size());
        for (int i = 0; i < limit; i++) {
            String id = candidateIds.get(i);
            AstNode n = nodeIndex.get(id);
            String name = n != null ? n.getName() : null;
            String fp = n != null ? n.getFilePath() : null;
            int line = n != null ? (n.getStartLine() + 1) : -1;
            sb.append("  - ").append(id);
            if (name != null && !name.isBlank()) sb.append(" | ").append(name);
            if (fp != null && !fp.isBlank()) sb.append(" @ ").append(fp);
            if (line > 0) sb.append(":").append(line);
            sb.append("\n");
        }
        if (candidateIds.size() > limit) {
            sb.append("  ... 还有 ").append(candidateIds.size() - limit).append(" 个候选\n");
        }
        return sb.toString().trim();
    }

    private String expandFile(Path root, AstNode fileNode, String relPath, String lineRange) {
        Path absPath = resolveProjectFile(root, relPath);
        if (absPath == null) return "✗ 路径不安全或无法解析: " + relPath;
        String content;
        try {
            content = Files.readString(absPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "✗ 读取文件失败: " + relPath + " - " + e.getMessage();
        }
        String[] lines = splitLinesPreserve(content);

        if (lineRange != null && !lineRange.isBlank()) {
            LineRange r = parseLineRange(lineRange, lines.length);
            if (r == null) {
                return "✗ lineRange 格式错误: " + lineRange + "（期望如 '100-200'，1基，含端点）";
            }
            String slice = renderSlice(lines, r.startIdxInclusive, r.endIdxInclusive);
            String pathSlash = relPath.replace("\\", "/");
            return "## " + pathSlash + " L" + lineRange.strip() + "\n" + slice;
        }

        // 小文件：直接返回全文
        if (lines.length <= 200) {
            String pathSlash = relPath.replace("\\", "/");
            return "## " + pathSlash + "\n" + content;
        }

        // 大文件：返回文件骨架 + 头尾预览（带行号），引导用户用 lineRange 精确提取
        String skeleton = renderFileSkeleton(fileNode);
        String head = renderSliceWithLineNumbers(lines, 0, Math.min(lines.length - 1, 49));
        String tail = renderSliceWithLineNumbers(lines, Math.max(0, lines.length - 20), lines.length - 1);
        String pathSlash = relPath.replace("\\", "/");
        return "## " + pathSlash + " (" + lines.length + " lines)\n"
                + "### File Skeleton (from cached AST)\n"
                + "```text\n" + skeleton + "\n```\n\n"
                + "### Head (1-50)\n"
                + "```text\n" + head + "\n```\n\n"
                + "### Tail (last 20)\n"
                + "```text\n" + tail + "\n```\n\n"
                + "提示：该文件约 " + lines.length + " 行。若要查看特定实现，请用 `lineRange`（例如 \"3000-3400\"）。";
    }

    private String expandTypeInFile(Path root, AstNode fileNode, String relPath, AstNode typeNode) {
        Path absPath = resolveProjectFile(root, relPath);
        if (absPath == null) return "✗ 路径不安全或无法解析: " + relPath;
        String content;
        try {
            content = Files.readString(absPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "✗ 读取文件失败: " + relPath + " - " + e.getMessage();
        }
        String[] lines = splitLinesPreserve(content);

        int start = clamp(typeNode.getStartLine(), 0, Math.max(0, lines.length - 1));
        int end = clamp(typeNode.getEndLine(), start, Math.max(0, lines.length - 1));

        String slice = renderSliceWithLineNumbers(lines, start, end);
        int classCount = countTypesInFile(fileNode);
        String pathSlash = relPath.replace("\\", "/");
        return "## " + pathSlash + " L" + (start + 1) + "-" + (end + 1) + "\n"
                + "(文件共 " + lines.length + " 行, 含 " + classCount + " 个类/接口, 当前展开 " + typeNode.getName() + ")\n\n"
                + "```text\n" + slice + "\n```";
    }

    private String expandMethodInFile(Path root, AstNode fileNode, String relPath, AstNode methodNode) {
        Path absPath = resolveProjectFile(root, relPath);
        if (absPath == null) return "✗ 路径不安全或无法解析: " + relPath;
        String content;
        try {
            content = Files.readString(absPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "✗ 读取文件失败: " + relPath + " - " + e.getMessage();
        }
        String[] lines = splitLinesPreserve(content);

        int start = clamp(methodNode.getStartLine(), 0, Math.max(0, lines.length - 1));
        int end = clamp(methodNode.getEndLine(), start, Math.max(0, lines.length - 1));

        String slice = renderSliceWithLineNumbers(lines, start, end);
        int classCount = countTypesInFile(fileNode);
        String pathSlash = relPath.replace("\\", "/");
        return "## " + pathSlash + " L" + (start + 1) + "-" + (end + 1) + "\n"
                + "(文件共 " + lines.length + " 行, 含 " + classCount + " 个类/接口, 当前展开方法 " + methodNode.getName() + ")\n\n"
                + "```text\n" + slice + "\n```";
    }

    private static int countTypesInFile(AstNode fileNode) {
        if (fileNode == null || fileNode.getChildren() == null) return 0;
        int n = 0;
        for (AstNode c : fileNode.getChildren()) {
            String t = c.getNodeType();
            if ("class".equals(t) || "interface".equals(t) || "enum".equals(t)) n++;
        }
        return n;
    }

    private boolean matchesFile(String relPath, String q) {
        if (relPath.equals(q)) return true;
        String norm = relPath.replace("\\", "/");
        String nq = q.replace("\\", "/");
        return norm.endsWith("/" + nq) || norm.endsWith(nq);
    }

    private boolean isTypeNode(AstNode n) {
        if (n == null) return false;
        String t = n.getNodeType();
        return "class".equals(t) || "interface".equals(t) || "enum".equals(t);
    }

    private Path resolveProjectFile(Path root, String relPath) {
        // 防止 path traversal：只允许 root 下的规范化路径
        String rp = relPath.replace("\\", "/");
        while (rp.startsWith("/")) rp = rp.substring(1);
        Path p = root.resolve(rp.replace("/", root.getFileSystem().getSeparator())).normalize();
        if (!p.startsWith(root)) return null;
        return p;
    }

    private String renderFileSkeleton(AstNode fileNode) {
        if (fileNode == null || fileNode.getChildren() == null || fileNode.getChildren().isEmpty()) return "(no AST children)";
        StringBuilder sb = new StringBuilder();
        for (AstNode c : fileNode.getChildren()) {
            String nt = c.getNodeType();
            if ("import".equals(nt) || "import_summary".equals(nt) || "package".equals(nt)) continue;
            if (isTypeNode(c)) {
                sb.append(c.getSignature() != null ? c.getSignature() : (nt + " " + c.getName())).append("\n");
                if (c.getChildren() != null) {
                    for (AstNode m : c.getChildren()) {
                        if ("method".equals(m.getNodeType())) {
                            sb.append("  - ").append(m.getSignature() != null ? m.getSignature() : m.getName()).append("\n");
                        } else if ("field".equals(m.getNodeType())) {
                            sb.append("  - ").append(m.getSignature() != null ? m.getSignature() : m.getName()).append("\n");
                        }
                    }
                }
                sb.append("\n");
            } else if ("method".equals(nt)) {
                sb.append(c.getSignature() != null ? c.getSignature() : c.getName()).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private static String[] splitLinesPreserve(String content) {
        // 保持与 startLine/endLine 的“行”语义一致：按 \n 分割
        return content.split("\n", -1);
    }

    private static String renderSlice(String[] lines, int start, int end) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i <= end && i < lines.length; i++) {
            sb.append(lines[i]);
            if (i < end) sb.append("\n");
        }
        return sb.toString();
    }

    private static String renderSliceWithLineNumbers(String[] lines, int start, int end) {
        StringBuilder sb = new StringBuilder();
        int s = Math.max(0, start);
        int e = Math.min(lines.length - 1, end);
        for (int i = s; i <= e; i++) {
            sb.append(String.format("%5d|", i + 1)).append(lines[i]).append("\n");
        }
        return sb.toString().replaceAll("\n$", "");
    }

    private record LineRange(int startIdxInclusive, int endIdxInclusive) {}

    private static LineRange parseLineRange(String lineRange, int totalLines) {
        String s = lineRange.strip();
        int dash = s.indexOf('-');
        if (dash <= 0 || dash >= s.length() - 1) return null;
        try {
            int start1 = Integer.parseInt(s.substring(0, dash).trim());
            int end1 = Integer.parseInt(s.substring(dash + 1).trim());
            if (start1 <= 0 || end1 <= 0) return null;
            int a = Math.min(start1, end1);
            int b = Math.max(start1, end1);
            int startIdx = clamp(a - 1, 0, Math.max(0, totalLines - 1));
            int endIdx = clamp(b - 1, startIdx, Math.max(0, totalLines - 1));
            return new LineRange(startIdx, endIdx);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int clamp(int v, int min, int max) {
        if (v < min) return min;
        return Math.min(v, max);
    }

    private static String escapeXmlAttr(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String runTrace(SkeletonCacheService.CachedSkeleton cached, String query, String direction, int maxDepth) {
        if (query == null || query.isBlank()) return "✗ trace 模式需要提供 query（类名或方法名）。";
        DependencyGraph graph = cached.graph();
        if (graph == null || graph.getGraph() == null) return "✗ 依赖图不可用。";
        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> g = graph.getGraph();
        Map<String, AstNode> index = graph.getNodeIndex();

        NameIndex nameIndex = cached.nameIndex() != null ? cached.nameIndex() : NameIndex.from(index);
        List<String> candidates = nameIndex.lookup(query.strip());
        if (candidates.isEmpty()) return "✗ 未找到节点: " + query;
        String startId = pickBestCandidateId(query.strip(), candidates);
        if (startId == null) startId = candidates.get(0);
        if (!g.containsVertex(startId)) return "✗ 节点不在依赖图中: " + startId;

        Set<String> visited = new HashSet<>();
        List<String> lines = new ArrayList<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(startId);
        visited.add(startId);
        Map<String, Integer> depthMap = new HashMap<>();
        depthMap.put(startId, 0);
        lines.add(prettyNodeLabel(startId, index));

        while (!queue.isEmpty()) {
            String v = queue.poll();
            int d = depthMap.getOrDefault(v, 0);
            if (d >= maxDepth) continue;
            if ("both".equals(direction) || "downstream".equals(direction)) {
                for (DefaultWeightedEdge e : g.outgoingEdgesOf(v)) {
                    String t = g.getEdgeTarget(e);
                    if (visited.add(t)) {
                        depthMap.put(t, d + 1);
                        queue.add(t);
                        String et = graph.getEdgeType(v, t);
                        if (et == null || et.isBlank()) et = "depends";
                        lines.add("  ".repeat(d + 1) + "→ [" + et + "] " + prettyNodeLabel(t, index));
                    }
                }
            }
            if ("both".equals(direction) || "upstream".equals(direction)) {
                for (DefaultWeightedEdge e : g.incomingEdgesOf(v)) {
                    String s = g.getEdgeSource(e);
                    if (visited.add(s)) {
                        depthMap.put(s, d + 1);
                        queue.add(s);
                        String et = graph.getEdgeType(s, v);
                        if (et == null || et.isBlank()) et = "depends";
                        lines.add("  ".repeat(d + 1) + "← [" + et + "] " + prettyNodeLabel(s, index));
                    }
                }
            }
        }
        return "<trace_result start=\"" + startId + "\" direction=\"" + direction + "\">\n" + String.join("\n", lines) + "\n</trace_result>";
    }

    private static String prettyNodeLabel(String id, Map<String, AstNode> index) {
        if (id == null) return "";
        if (index == null) return id;
        AstNode n = index.get(id);
        if (n == null) return id;
        String fp = n.getFilePath();
        int line = n.getStartLine() + 1;
        String kind = id.startsWith("file:") ? "file" : (id.startsWith("class:") ? "class" : (id.startsWith("method:") ? "method" : "node"));
        String name = n.getName() != null ? n.getName() : id;
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(kind).append("] ").append(name);
        if (fp != null && !fp.isBlank()) {
            sb.append(" -- ").append(fp.replace("\\", "/"));
            if (line > 0) sb.append(":").append(line);
        }
        return sb.toString();
    }

    private static final int SEARCH_MERGE_THRESHOLD = 10;

    private String runSearch(SkeletonCacheService.CachedSkeleton cached, String projectPath, String query) {
        if (query == null || query.isBlank()) return "✗ search 模式需要提供 query。";
        Map<String, AstNode> index = cached.graph() != null ? cached.graph().getNodeIndex() : Map.of();
        if (index.isEmpty()) return "✗ 依赖图索引为空，无法 search。";

        String qRaw = query.strip();
        // 多关键词自动拆分：空格/逗号分隔，每个词独立搜索后合并
        String[] parts = qRaw.split("[\\s,]+");
        if (parts.length > 1) {
            return runMultiSearch(cached, projectPath, parts);
        }
        return runSingleSearch(cached, projectPath, qRaw);
    }

    private String runMultiSearch(SkeletonCacheService.CachedSkeleton cached, String projectPath, String[] keywords) {
        StringBuilder sb = new StringBuilder();
        sb.append("## batch search (").append(keywords.length).append(" keywords)\n");
        int totalHits = 0;
        for (String kw : keywords) {
            String t = kw != null ? kw.strip() : "";
            if (t.length() < 2) continue;
            String result = runSingleSearch(cached, projectPath, t);
            if (result != null && !result.startsWith("✗")) {
                sb.append(result).append("\n");
                totalHits++;
            }
        }
        if (totalHits == 0) {
            sb.append("✗ 所有关键词均未找到匹配\n");
            sb.append(suggestAvailableEntities(cached, keywords));
        }
        return sb.toString().trim();
    }

    private String suggestAvailableEntities(SkeletonCacheService.CachedSkeleton cached, String[] keywords) {
        List<String> samples = sampleEntityNames(cached, 8);
        if (samples.isEmpty()) return "";
        return "可用实体示例: " + String.join(", ", samples) + "\n";
    }

    private String runSingleSearch(SkeletonCacheService.CachedSkeleton cached, String projectPath, String qRaw) {
        Map<String, AstNode> index = cached.graph() != null ? cached.graph().getNodeIndex() : Map.of();
        String qLower = qRaw.toLowerCase();
        NameIndex nameIndex = cached.nameIndex() != null ? cached.nameIndex() : NameIndex.from(index);

        // Step 1: 精确匹配（nodeId / name 精确）— 命中则直接返回
        List<String> strictIds = nameIndex.lookupStrict(qRaw);
        if (!strictIds.isEmpty()) {
            return renderSearchResult(projectPath, qRaw, "strict", strictIds, index, List.of());
        }

        List<String> allNodeIds = new ArrayList<>();
        List<GrepHit> allGrepHits = new ArrayList<>();
        String bestStage = "none";

        // Step 2: name 匹配
        List<String> nameIds = nameIndex.lookup(qRaw);
        if (!nameIds.isEmpty()) {
            for (String id : nameIds) {
                if (!allNodeIds.contains(id)) allNodeIds.add(id);
            }
            bestStage = "name";
        }

        // Step 3: 若总数 < 阈值，继续 AST contains 扫描
        if (allNodeIds.size() < SEARCH_MERGE_THRESHOLD) {
            List<String> astIds = new ArrayList<>();
            for (Map.Entry<String, AstNode> e : index.entrySet()) {
                String id = e.getKey();
                AstNode n = e.getValue();
                if (id == null || n == null) continue;
                String nm = n.getName();
                String sig = n.getSignature();
                if ((nm != null && nm.toLowerCase().contains(qLower)) ||
                        (sig != null && sig.toLowerCase().contains(qLower)) ||
                        id.toLowerCase().contains(qLower)) {
                    if (!allNodeIds.contains(id)) astIds.add(id);
                    if (astIds.size() + allNodeIds.size() >= 60) break;
                }
            }
            for (String id : astIds) {
                if (!allNodeIds.contains(id)) allNodeIds.add(id);
            }
            if ("none".equals(bestStage) && !astIds.isEmpty()) bestStage = "ast";
        }

        // Step 4: 若总数仍 < 阈值，继续 grep
        if (allNodeIds.size() < SEARCH_MERGE_THRESHOLD) {
            Path root = (projectPath != null && !projectPath.isBlank())
                    ? Paths.get(projectPath).toAbsolutePath().normalize()
                    : Paths.get(".").toAbsolutePath().normalize();
            List<String> fileRelPaths = new ArrayList<>();
            for (Map.Entry<String, AstNode> e : index.entrySet()) {
                String id = e.getKey();
                AstNode n = e.getValue();
                if (id != null && id.startsWith("file:")) {
                    String p = n != null ? n.getFilePath() : id.substring("file:".length());
                    if (p != null && !p.isBlank()) fileRelPaths.add(p);
                }
            }
            List<GrepHit> hits = grepCodeContent(root, fileRelPaths, qRaw, 25);
            allGrepHits.addAll(hits);
            if ("none".equals(bestStage) && !hits.isEmpty()) bestStage = "grep";
        }

        if (!allNodeIds.isEmpty() || !allGrepHits.isEmpty()) {
            return renderSearchResult(projectPath, qRaw, bestStage, allNodeIds, index, allGrepHits);
        }

        // Step 5: 轻量模糊匹配候选（token Jaccard）
        List<String> fuzzy = fuzzyNameCandidates(nameIndex, qRaw, 12);
        if (!fuzzy.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("✗ 未找到直接匹配: ").append(qRaw).append("\n");
            sb.append("可能的相近候选（请复制其中一个再 search/expand）：\n");
            for (String s : fuzzy) sb.append("  - ").append(s).append("\n");
            return sb.toString().trim();
        }

        return buildSearchNotFoundMessage(cached, qRaw);
    }

    private String buildSearchNotFoundMessage(SkeletonCacheService.CachedSkeleton cached, String query) {
        StringBuilder sb = new StringBuilder();
        sb.append("✗ 未找到匹配: ").append(query).append("\n");
        if (query.contains(" ") || query.contains(",")) {
            String first = query.split("[\\s,]+")[0].strip();
            if (!first.isEmpty()) {
                sb.append("[提示] 请用单个关键词搜索，不要组合多个词。例如: search(\"").append(first).append("\")\n");
            }
        }
        List<String> samples = sampleEntityNames(cached, 8);
        if (!samples.isEmpty()) {
            sb.append("可用实体示例: ").append(String.join(", ", samples));
        }
        return sb.toString().trim();
    }

    private List<String> sampleEntityNames(SkeletonCacheService.CachedSkeleton cached, int limit) {
        NameIndex nameIndex = cached.nameIndex();
        if (nameIndex == null) {
            Map<String, AstNode> index = cached.graph() != null ? cached.graph().getNodeIndex() : Map.of();
            nameIndex = NameIndex.from(index);
        }
        Set<String> keys = nameIndex.lowerKeys();
        if (keys == null || keys.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        for (String k : keys) {
            if (k == null || k.length() < 2 || k.length() > 64) continue;
            out.add(k);
            if (out.size() >= limit) break;
        }
        return out;
    }

    private static List<String> extractTopClassNames(ProjectSkeleton sk, int limit) {
        List<String> out = new ArrayList<>();
        if (sk == null || sk.getFileNodes() == null) return out;
        for (AstNode fileNode : sk.getFileNodes()) {
            if (fileNode.getChildren() == null) continue;
            for (AstNode child : fileNode.getChildren()) {
                String type = child.getNodeType();
                if (type == null) continue;
                String t = type.toLowerCase();
                if ("class".equals(t) || "interface".equals(t) || "enum".equals(t)) {
                    String name = child.getName();
                    if (name != null && !name.isBlank() && !out.contains(name)) {
                        out.add(name);
                        if (out.size() >= limit) return out;
                    }
                }
            }
        }
        return out;
    }

    private static List<String> extractTopMethodNames(ProjectSkeleton sk, int limit) {
        List<String> out = new ArrayList<>();
        if (sk == null || sk.getFileNodes() == null) return out;
        for (AstNode fileNode : sk.getFileNodes()) {
            if (fileNode.getChildren() == null) continue;
            for (AstNode child : fileNode.getChildren()) {
                if ("method".equals(child.getNodeType()) || "function".equals(child.getNodeType())) {
                    String name = child.getName();
                    if (name != null && !name.isBlank() && !out.contains(name)) {
                        out.add(name);
                        if (out.size() >= limit) return out;
                    }
                }
                if (child.getChildren() != null) {
                    for (AstNode m : child.getChildren()) {
                        if ("method".equals(m.getNodeType()) || "function".equals(m.getNodeType())) {
                            String name = m.getName();
                            if (name != null && !name.isBlank() && !out.contains(name)) {
                                out.add(name);
                                if (out.size() >= limit) return out;
                            }
                        }
                    }
                }
            }
        }
        return out;
    }

    private static List<String> extractFileNames(ProjectSkeleton sk, int limit) {
        List<String> out = new ArrayList<>();
        if (sk == null || sk.getFileNodes() == null) return out;
        for (AstNode fileNode : sk.getFileNodes()) {
            String fp = fileNode.getFilePath();
            if (fp == null || fp.isBlank()) continue;
            String norm = fp.replace("\\", "/");
            String base = norm.contains("/") ? norm.substring(norm.lastIndexOf('/') + 1) : norm;
            if (!base.isBlank() && !out.contains(base)) {
                out.add(base);
                if (out.size() >= limit) return out;
            }
        }
        return out;
    }

    private record GrepHit(String filePath, int line, String snippet) {}

    private List<GrepHit> grepCodeContent(Path projectRoot, List<String> fileRelPaths, String query, int maxHits) {
        if (projectRoot == null || fileRelPaths == null || query == null) return List.of();
        String q = query.strip();
        if (q.length() < 3) return List.of();

        List<GrepHit> hits = new ArrayList<>();
        int scannedFiles = 0;
        for (String rel : fileRelPaths) {
            if (hits.size() >= maxHits) break;
            if (rel == null || rel.isBlank()) continue;
            scannedFiles++;
            if (scannedFiles > 200) break;

            Path abs = resolveProjectFile(projectRoot, rel);
            if (abs == null) continue;
            try {
                if (Files.size(abs) > 1024 * 1024) continue;
                List<String> lines = Files.readAllLines(abs, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line == null) continue;
                    if (line.toLowerCase().contains(q.toLowerCase())) {
                        String snip = line.strip();
                        if (snip.length() > 240) snip = snip.substring(0, 237) + "...";
                        hits.add(new GrepHit(rel.replace("\\", "/"), i + 1, snip));
                        if (hits.size() >= maxHits) break;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return hits;
    }

    private static String renderSearchResult(String projectPath, String query, String stage, List<String> nodeIds,
                                            Map<String, AstNode> index, List<GrepHit> grepHits) {
        List<String> uniqueIds = nodeIds != null ? new ArrayList<>(new LinkedHashSet<>(nodeIds)) : List.of();
        int nodeCount = uniqueIds.size();
        int grepCount = grepHits != null ? grepHits.size() : 0;
        int total = nodeCount + grepCount;
        boolean preview = nodeCount > 0 && nodeCount <= 3;

        StringBuilder sb = new StringBuilder();
        String stageLabel = (nodeCount > 0 && grepCount > 0) ? (stage + "+grep") : stage;
        sb.append("## search \"").append(query.replace("\"", "\\\"")).append("\" (").append(stageLabel).append(", ").append(total).append(" hits)\n");

        int written = 0;
        int previewsAdded = 0;
        final int maxCodePreviews = 12;
        if (!uniqueIds.isEmpty()) {
            for (String id : uniqueIds) {
                if (id == null) continue;
                AstNode n = index.get(id);
                String kind = id.startsWith("file:") ? "file" : (id.startsWith("class:") ? "class" : (id.startsWith("method:") ? "method" : "node"));
                String name = n != null && n.getName() != null ? n.getName() : id;
                String fp = n != null ? n.getFilePath() : null;
                int line = n != null ? (n.getStartLine() + 1) : -1;

                sb.append("[").append(kind).append("] ").append(name);
                if (fp != null && !fp.isBlank()) {
                    sb.append(" -- ").append(fp.replace("\\", "/"));
                    if (line > 0) sb.append(":").append(line);
                }
                sb.append("\n");

                // P1: 内嵌代码预览 — 从源文件读取 3-5 行函数体，减少 expand 调用
                if (previewsAdded < maxCodePreviews && projectPath != null && !projectPath.isBlank() && n != null
                        && ("method".equals(kind) || "class".equals(kind))) {
                    String codePreview = extractCodePreview(projectPath, n, 5);
                    if (codePreview != null && !codePreview.isBlank()) {
                        sb.append("  ").append(codePreview.replace("\n", "\n  ")).append("\n");
                        previewsAdded++;
                    }
                }

                if (preview && n != null) {
                    String sig = compactOneLine(n.getSignature(), n.getNodeType() + " " + n.getName());
                    if (sig != null && !sig.isBlank()) sb.append("  ").append(sig).append("\n");
                    if ("class".equals(n.getNodeType()) || "interface".equals(n.getNodeType()) || "enum".equals(n.getNodeType())) {
                        if (n.getChildren() != null && !n.getChildren().isEmpty()) {
                            int m = 0;
                            for (AstNode ch : n.getChildren()) {
                                if (ch == null || !"method".equals(ch.getNodeType())) continue;
                                String ms = compactOneLine(ch.getSignature(), ch.getName());
                                if (ms != null && !ms.isBlank()) {
                                    sb.append("    - ").append(ms).append("\n");
                                    if (++m >= 8) break;
                                }
                            }
                            if (n.getChildren().size() > 8) sb.append("    ...\n");
                        }
                    } else if (("file".equals(n.getNodeType()) || id.startsWith("file:")) && n.getChildren() != null && !n.getChildren().isEmpty()) {
                        int c = 0;
                        for (AstNode ch : n.getChildren()) {
                            if (ch == null) continue;
                            String t = ch.getNodeType();
                            if (!"class".equals(t) && !"interface".equals(t) && !"enum".equals(t) && !"method".equals(t)) continue;
                            String cs = compactOneLine(ch.getSignature(), t + " " + ch.getName());
                            if (cs != null && !cs.isBlank()) {
                                sb.append("    - ").append(cs).append("\n");
                                if (++c >= 8) break;
                            }
                        }
                        if (n.getChildren().size() > 8) sb.append("    ...\n");
                    }
                }

                written++;
                if (written >= 30) break;
            }
        }
        if (grepHits != null && !grepHits.isEmpty()) {
            int grepWritten = 0;
            for (GrepHit h : grepHits) {
                sb.append("[grep] ").append(h.filePath).append(":").append(h.line).append(" | ").append(h.snippet).append("\n");
                if (++grepWritten >= 25) break;
            }
        }
        if (total > written + (grepHits != null ? Math.min(grepHits.size(), 25) : 0)) {
            sb.append("... 还有 ").append(Math.max(0, total - written - (grepHits != null ? Math.min(grepHits.size(), 25) : 0))).append(" 条, 用更精确的关键词缩小范围\n");
        } else if (nodeCount > written && grepCount == 0) {
            sb.append("... 还有 ").append(nodeCount - written).append(" 条, 用更精确的关键词缩小范围\n");
        }
        return sb.toString();
    }

    /**
     * 从源文件读取节点处 3-5 行代码预览（跳过空行和纯注释），用于 search 结果内嵌，减少 expand 调用。
     */
    private static String extractCodePreview(String projectPath, AstNode node, int maxLines) {
        if (node == null || projectPath == null || projectPath.isBlank()) return null;
        String relPath = node.getFilePath();
        if (relPath == null || relPath.isBlank()) return null;
        Path root = Paths.get(projectPath).toAbsolutePath().normalize();
        Path file = root.resolve(relPath.replace("\\", "/"));
        if (!Files.isRegularFile(file)) return null;
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            int start = Math.max(0, node.getStartLine());
            if (start >= lines.size()) return null;
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (int i = start; i < lines.size() && count < maxLines; i++) {
                String line = lines.get(i);
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                if (trimmed.startsWith("//") || trimmed.startsWith("#") || trimmed.startsWith("*")
                        || trimmed.startsWith("/*") || trimmed.startsWith("*/") || trimmed.startsWith("* ")) continue;
                sb.append(trimmed);
                if (i + 1 < lines.size()) sb.append("\n");
                count++;
            }
            return sb.toString().trim();
        } catch (IOException | SecurityException ignored) {
            return null;
        }
    }

    private static String compactOneLine(String signature, String fallback) {
        String s = signature != null && !signature.isBlank() ? signature : (fallback != null ? fallback : "");
        s = s.replace("\r", "");
        int nl = s.indexOf('\n');
        if (nl >= 0) s = s.substring(0, nl);
        s = s.replaceAll("\\s+", " ").trim();
        if (s.endsWith("{")) s = s.substring(0, s.length() - 1).trim();
        if (s.length() > 200) s = s.substring(0, 197) + "...";
        return s;
    }

    private static List<String> fuzzyNameCandidates(NameIndex nameIndex, String query, int limit) {
        if (nameIndex == null || query == null) return List.of();
        String q = query.strip().toLowerCase();
        if (q.length() < 3) return List.of();

        List<String> qTokens = tokenize(q);
        if (qTokens.isEmpty()) return List.of();

        record Scored(String key, double score) {}
        List<Scored> scored = new ArrayList<>();
        for (String key : nameIndex.lowerKeys()) {
            if (key == null || key.isBlank()) continue;
            List<String> kTokens = tokenize(key);
            if (kTokens.isEmpty()) continue;
            double j = jaccard(qTokens, kTokens);
            if (j >= 0.55) scored.add(new Scored(key, j));
        }
        scored.sort((a, b) -> Double.compare(b.score, a.score));

        List<String> out = new ArrayList<>();
        for (Scored s : scored) {
            out.add(s.key);
            if (out.size() >= limit) break;
        }
        return out;
    }

    private static List<String> tokenize(String s) {
        if (s == null) return List.of();
        String norm = s.replace("_", " ").replace("-", " ").replace(":", " ");
        String[] parts = norm.split("[^a-z0-9\\.]+");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            if (p == null) continue;
            String t = p.trim();
            if (t.isEmpty()) continue;
            out.add(t);
        }
        return out;
    }

    private static double jaccard(List<String> a, List<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        Set<String> sa = new HashSet<>(a);
        Set<String> sb = new HashSet<>(b);
        int inter = 0;
        for (String x : sa) if (sb.contains(x)) inter++;
        int union = sa.size() + sb.size() - inter;
        return union <= 0 ? 0.0 : ((double) inter) / union;
    }

    /**
     * 分析代码风格与修改意图
     * <p>
     * 启动/查询文件监听器，获取开发过程中的实时修改轨迹，
     * 供大模型推断设计意图并更新模板规范。
     *
     * @param directoryPath 要监听的目录路径
     * @param action        操作类型：start（启动监听）、stop（停止监听）、drain（获取变更事件）
     * @return 操作结果或变更事件列表
     */
    public String analyzeStyleAndIntent(
            @McpToolParam(description = "目录路径（start 时必填）") String directoryPath,
            @McpToolParam(description = "操作类型: start, stop, drain") String action) {
        try {
            return switch (action) {
                case "start" -> {
                    if (directoryPath == null || directoryPath.isBlank()) {
                        yield "✗ 启动监听需要提供目录路径";
                    }
                    watcherService.startWatching(directoryPath);
                    yield "✓ 开始监听目录: " + directoryPath;
                }
                case "stop" -> {
                    watcherService.stopWatching();
                    yield "✓ 已停止监听";
                }
                case "drain" -> {
                    var events = watcherService.drainEvents(50);
                    if (events.isEmpty()) {
                        yield "暂无新的文件变更事件（队列待处理: " + watcherService.pendingEventCount() + "）";
                    }
                    StringBuilder sb = new StringBuilder();
                    sb.append("捕获到 ").append(events.size()).append(" 个变更事件:\n");
                    for (var event : events) {
                        sb.append("  - [").append(event.eventType()).append("] ")
                                .append(event.filePath()).append("\n");
                    }
                    yield sb.toString();
                }
                default -> "✗ 未知操作: " + action + "，支持: start, stop, drain";
            };
        } catch (Exception e) {
            return "✗ 意图分析操作失败: " + e.getMessage();
        }
    }

    /**
     * 物化并验证模板
     * <p>
     * 将大模型生成的代码文件写入沙盒，执行编译验证命令。
     * 验证通过后可持久化至本地模板库。
     *
     * @param workspaceName 沙盒工作区名称
     * @param filePath      沙盒内的文件路径
     * @param fileContent   文件内容
     * @param verifyCommand 验证命令（如 "mvn clean compile"）
     * @return 验证结果（成功或精简的错误摘要）
     */
    public String materializeAndVerifyTemplate(
            @McpToolParam(description = "沙盒工作区名称，如: verify-crud") String workspaceName,
            @McpToolParam(description = "沙盒内文件路径，如: src/Main.java") String filePath,
            @McpToolParam(description = "文件内容") String fileContent,
            @McpToolParam(description = "验证命令，如: mvn clean compile") String verifyCommand) {
        try {
            // 1. 写入文件到沙盒
            String fullPath = workspaceName + "/" + filePath;
            sandboxService.writeFile(fullPath, fileContent);

            // 2. 执行验证命令
            SandboxVerificationService.ExecutionResult result = sandboxService.executeCommand(verifyCommand,
                    workspaceName, 120);

            if (result.success()) {
                return String.format("""
                        ✓ 编译验证通过
                        - 工作区: %s
                        - 文件: %s
                        - 命令: %s
                        - 输出: %s

                        模板已就绪，可使用 uploadTemplate 持久化至模板库。
                        """,
                        workspaceName, filePath, verifyCommand,
                        result.stdout().length() > 500 ? result.stdout().substring(0, 500) + "..." : result.stdout());
            } else {
                // 提取精简的错误摘要（借鉴 RepoMaster 的 Information Selection）
                String errorSummary = sandboxService.extractErrorSummary(result.stderr(), 20);

                return String.format("""
                        ✗ 编译验证失败 (exit code: %d)
                        - 工作区: %s
                        - 文件: %s
                        - 命令: %s

                        错误摘要:
                        %s

                        请根据以上错误修正代码后重试。
                        """,
                        result.exitCode(), workspaceName, filePath, verifyCommand, errorSummary);
            }
        } catch (Exception e) {
            return "✗ 沙盒验证失败: " + e.getMessage();
        }
    }
}
