package top.codestyle.mcp.service;

import lombok.RequiredArgsConstructor;

import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.config.RepositoryConfig;
import top.codestyle.mcp.model.meta.LocalMetaInfo;
import top.codestyle.mcp.model.sdk.MetaInfo;
import top.codestyle.mcp.model.tree.TreeNode;
import top.codestyle.mcp.util.CodestyleClient;
import top.codestyle.mcp.util.PromptUtils;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 代码模板搜索和内容获取服务
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

    /**
     * 搜索代码模板
     * <p>根据模板提示词搜索模板信息，返回目录树和模板组介绍。
     * 支持本地Lucene检索和远程检索两种模式。
     *
     * @param templateKeyword 模板提示词，支持关键词或 groupId/artifactId 格式，如: CRUD, backend, frontend, continew/DatabaseConfig
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
                List<CodestyleClient.RemoteSearchResult> remoteResults = templateService.searchFromRemote(templateKeyword);

                // 远程返回空结果，自动fallback到本地Lucene检索
                if (remoteResults.isEmpty()) {
                    return searchLocalFallback(templateKeyword, true);
                }

                // 单个结果：精确匹配，下载到本地并返回目录树
                if (remoteResults.size() == 1) {
                    CodestyleClient.RemoteSearchResult result = remoteResults.get(0);
                    
                    // 下载模板
                    templateService.downloadTemplate(result);

                    List<MetaInfo> metaInfos = templateService.searchLocalRepository(
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
     * 本地Lucene检索（兜底策略）
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
        List<MetaInfo> metaInfos = templateService.searchLocalRepository(
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
     * <p>根据完整的模板文件路径获取详细内容，包括变量说明和模板代码
     *
     * @param templatePath 完整模板文件路径（包含版本号和.ftl扩展名），如: backend/CRUD/1.0.0/src/main/java/com/air/controller/Controller.ftl
     * @return 模板文件的详细信息字符串（包含变量说明和模板内容）
     * @throws IOException 文件读取异常
     */
    @McpTool(name = "getTemplateByPath", description = "传入模板文件路径,获取模板文件的详细内容(包括变量说明和模板代码)")
    public String getTemplateByPath(
            @McpToolParam(description = "模板文件路径,如:backend/CRUD/1.0.0/src/main/java/com/air/controller/Controller.ftl") String templatePath)
            throws IOException {

        // 使用精确路径搜索模板
        LocalMetaInfo matchedTemplate = templateService.searchByPath(templatePath);

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
}