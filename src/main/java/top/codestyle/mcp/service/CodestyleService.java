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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    private final TemplateService templateService;
    private final PromptService promptService;
    private final LuceneIndexService luceneIndexService;
    private final RepositoryConfig repositoryConfig;
    private final AstParsingService astParsingService;
    private final RepoMasterScoringService scoringService;
    private final TemplateWatcherService watcherService;
    private final SandboxVerificationService sandboxService;

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
    @McpTool(name = "codestyleSearch", description = """
            根据模板提示词搜索代码模板库，返回匹配的模板目录树和模板组介绍。
            支持以下搜索格式：
            1. 关键词搜索：CRUD, frontend, backend 等
            2. 精确搜索：groupId/artifactId 格式
            """)
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
    @McpTool(name = "getTemplateByPath", description = "传入模板文件路径,获取模板文件的详细内容(包括变量说明和模板代码)")
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
    @McpTool(name = "uploadTemplateFromFileSystem", description = """
            从文件系统上传模板到本地仓库或远程服务器。
            本地模式：复制到本地缓存并重建索引。
            远程模式：复制到本地缓存、上传到远程服务器并重建索引。
            """)
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
            return "✗ 上传失败: " + e.getMessage();
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
    @McpTool(name = "uploadTemplate", description = """
            上传模板到本地仓库或远程服务器。
            本地模式：保存到本地缓存并重建索引。
            远程模式：保存到本地缓存、上传到远程服务器并重建索引。
            """)
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
    @McpTool(name = "deleteTemplate", description = """
            删除指定版本的模板。
            本地模式：删除本地缓存并重建索引。
            远程模式：删除本地缓存、删除远程模板并重建索引。
            """)
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
     * 对指定目录执行 Tree-sitter AST 全量扫描，构建层级代码树 (HCT) 与
     * 模块依赖图 (MDG)，并通过 RepoMaster 打分算法进行智能剪枝后以 XML 格式返回。
     *
     * @param projectPath      项目目录路径
     * @param compressionRatio 压缩比 (0.0~1.0)，如 0.8 表示只保留最重要的 20% 的代码骨架
     * @return XML 格式的项目骨架
     */
    @McpTool(name = "extractProjectSkeleton", description = """
            对指定项目目录执行 Tree-sitter 多语言 AST 解析，
            构建层级代码树(HCT)和模块依赖图(MDG)。
            通过 RepoMaster 打分算法智能剪枝后返回 XML 格式的精简项目骨架。
            支持 Java, Python, JavaScript, TypeScript, Go 语言。
            """)
    public String extractProjectSkeleton(
            @McpToolParam(description = "项目目录绝对路径") String projectPath,
            @McpToolParam(description = "压缩比(0.0~1.0)，推荐 0.8，表示只保留最重要的 20% 骨架") Double compressionRatio) {
        try {
            double ratio = (compressionRatio != null) ? compressionRatio : 0.8;

            // Phase 1: AST 解析
            ProjectSkeleton skeleton = astParsingService.parseProject(projectPath);

            // Phase 2: 智能剪枝
            ProjectSkeleton pruned = scoringService.prune(skeleton, ratio);

            // 输出 XML（借鉴 Repomix XML 输出格式）
            return astParsingService.toXmlSkeleton(pruned, ratio);
        } catch (Exception e) {
            return "✗ 项目骨架提取失败: " + e.getMessage();
        }
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
    @McpTool(name = "analyzeStyleAndIntent", description = """
            管理代码修改实时监听器，捕获开发过程中的每一次文件保存事件。
            action 参数：
            - start: 启动对指定目录的监听
            - stop: 停止监听
            - drain: 获取累积的变更事件列表
            """)
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
    @McpTool(name = "materializeAndVerifyTemplate", description = """
            在隔离沙盒中写入代码文件并执行编译验证命令。
            用于验证大模型生成的模板骨架是否能通过编译。
            验证通过后，生成的代码可通过 uploadTemplate 持久化至模板库。
            """)
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
