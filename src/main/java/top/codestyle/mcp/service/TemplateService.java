package top.codestyle.mcp.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.config.RepositoryConfig;
import top.codestyle.mcp.model.meta.LocalMetaInfo;
import top.codestyle.mcp.model.sdk.MetaInfo;
import top.codestyle.mcp.model.sdk.RemoteMetaConfig;
import top.codestyle.mcp.model.tree.TreeNode;
import top.codestyle.mcp.util.CodestyleClient;
import top.codestyle.mcp.util.MetaInfoConvertUtil;
import top.codestyle.mcp.util.PromptUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 模板服务
 * 提供模板搜索、远程配置获取和智能下载功能
 *
 * @author 小航love666, Kanttha, movclantian
 * @since 2025-09-29
 */
@Service
@RequiredArgsConstructor
public class TemplateService {

    private final RepositoryConfig repositoryConfig;

    @Lazy
    private final LuceneIndexService luceneIndexService;

    @Lazy
    private final PromptService promptService;

    /**
     * 根据groupId和artifactId搜索指定模板组
     *
     * @param groupId    组ID
     * @param artifactId 项目ID
     * @return 匹配的模板元信息列表
     */
    public List<MetaInfo> searchLocalRepository(String groupId, String artifactId) {
        String localRepoPath = repositoryConfig.getRepositoryDir();
        return CodestyleClient.searchLocalRepository(groupId, artifactId, localRepoPath);
    }

    /**
     * 根据精确路径搜索模板
     * 本地未找到时尝试从远程下载
     *
     * @param exactPath 精确路径,格式: groupId/artifactId/version/filePath/filename
     * @return 模板元信息,未找到返回null
     * @throws IOException 文件读取异常
     */
    public LocalMetaInfo searchByPath(String exactPath) throws IOException {
        String localRepoPath = repositoryConfig.getRepositoryDir();

        // 从本地仓库中查找模板
        MetaInfo localResult = CodestyleClient.searchByPath(exactPath, localRepoPath);
        if (localResult != null) {
            LocalMetaInfo result = MetaInfoConvertUtil.convert(localResult);
            result.setTemplateContent(readTemplateContent(localResult));
            return result;
        }

        // 本地未找到,尝试智能下载
        try {
            // 解析路径获取artifactId(格式: groupId/artifactId/version/filePath/filename)
            String[] parts = exactPath.split("/");
            if (parts.length >= 2) {
                String groupId = parts[0];
                String artifactId = parts[1];

                // 使用新版远程检索
                List<CodestyleClient.RemoteSearchResult> remoteResults = searchFromRemote(artifactId);
                if (remoteResults.isEmpty()) {
                    return null;
                }

                // 触发下载（取第一个匹配结果）
                boolean downloadSuccess = downloadTemplate(remoteResults.get(0));

                // 下载成功后重新搜索
                if (downloadSuccess) {
                    localResult = CodestyleClient.searchByPath(exactPath, localRepoPath);
                    if (localResult != null) {
                        LocalMetaInfo result = MetaInfoConvertUtil.convert(localResult);
                        result.setTemplateContent(readTemplateContent(localResult));
                        return result;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    /**
     * 从远程仓库搜索模板（使用 Open API 签名认证）
     *
     * @param templateKeyword 模板关键词
     * @return 远程检索结果列表
     */
    public List<CodestyleClient.RemoteSearchResult> searchFromRemote(String templateKeyword) {
        RepositoryConfig.RemoteConfig remote = repositoryConfig.getRemote();
        return CodestyleClient.searchFromRemote(
            remote.getBaseUrl(), 
            templateKeyword, 
            remote.getAccessKey(), 
            remote.getSecretKey(), 
            remote.getTimeoutMs()
        );
    }

    /**
     * 下载远程模板
     * 
     * @param result 远程检索结果
     * @return 是否成功
     */
    public boolean downloadTemplate(CodestyleClient.RemoteSearchResult result) {
        String localRepoPath = repositoryConfig.getRepositoryDir();
        String remoteBaseUrl = repositoryConfig.getRemote().getBaseUrl();
        
        return CodestyleClient.downloadTemplate(localRepoPath, remoteBaseUrl, result);
    }

    /**
     * 读取模板文件内容
     * <p>从本地缓存目录读取模板文件的完整内容
     *
     * @param info 模板元信息
     * @return 模板文件内容字符串
     * @throws IOException 文件不存在或读取失败
     */
    private String readTemplateContent(MetaInfo info) throws IOException {
        String localCachePath = repositoryConfig.getRepositoryDir();

        // 拼装模板文件绝对路径(本地缓存根目录 + groupId + artifactId + version + filePath + filename)
        Path templatePath = Paths.get(localCachePath,
                info.getGroupId(),
                info.getArtifactId(),
                info.getVersion(),
                info.getFilePath(),
                info.getFilename())
                .toAbsolutePath()
                .normalize();

        // 校验文件是否存在
        if (!Files.exists(templatePath)) {
            throw new IOException("模板文件不存在: " + templatePath);
        }

        // 读取文件内容(一次性读入,文件通常几十KB以内,性能足够)
        return Files.readString(templatePath, StandardCharsets.UTF_8);
    }

    /**
     * 是否启用远程检索
     *
     * @return true-远程检索, false-本地Lucene检索
     */
    public boolean isRemoteSearchEnabled() {
        return repositoryConfig.getRemote().isEnabled();
    }

    /**
     * 判断是否为同一groupId的搜索（命名空间搜索）
     *
     * @param results 搜索结果
     * @return true表示所有结果属于同一groupId
     */
    public boolean isGroupIdSearch(List<LuceneIndexService.SearchResult> results) {
        if (results.size() <= 1) return false;
        String firstGroupId = results.get(0).groupId();
        return results.stream().allMatch(r -> r.groupId().equals(firstGroupId));
    }

    /**
     * 构建按groupId聚合的结果
     * <p>展示该命名空间下所有模板的目录树和聚合描述
     *
     * @param keyword 搜索关键词
     * @param results 同一groupId的所有模板搜索结果
     * @return 聚合后的目录树字符串
     */
    public String buildGroupAggregatedResult(String keyword, List<LuceneIndexService.SearchResult> results) {
        String groupId = results.get(0).groupId();
        List<MetaInfo> allMetaInfos = new ArrayList<>();

        for (LuceneIndexService.SearchResult result : results) {
            List<MetaInfo> metaInfos = searchLocalRepository(result.groupId(), result.artifactId());
            allMetaInfos.addAll(metaInfos);
        }

        if (allMetaInfos.isEmpty()) {
            return "本地仓库模板文件不完整,请检查模板目录";
        }

        TreeNode treeNode = PromptUtils.buildTree(allMetaInfos);
        String treeStr = PromptUtils.buildTreeStr(treeNode, "").trim();

        StringBuilder artifactList = new StringBuilder();
        for (LuceneIndexService.SearchResult r : results) {
            artifactList.append("  - ").append(r.artifactId()).append("\n");
        }

        String description = promptService.buildGroupAggregated(
                groupId,
                String.valueOf(results.size()),
                artifactList.toString());

        return promptService.buildSearchResult(groupId, treeStr, description);
    }

    /**
     * 构建多结果响应
     * <p>当搜索匹配多个不同的模板时，返回格式化的模板列表
     *
     * @param keyword 搜索关键词
     * @param results 搜索结果列表
     * @return 格式化的多结果响应字符串
     */
    public String buildMultiResultResponse(String keyword, List<LuceneIndexService.SearchResult> results) {
        return promptService.buildLocalMultiResultResponse(keyword, results);
    }
}
