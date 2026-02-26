package top.codestyle.mcp.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.config.RepositoryConfig;
import top.codestyle.mcp.model.template.TemplateContent;
import top.codestyle.mcp.model.template.TemplateMetaInfo;
import top.codestyle.mcp.model.remote.RemoteSearchResult;
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
    public List<TemplateMetaInfo> searchLocalRepository(String groupId, String artifactId) {
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
    public TemplateContent searchByPath(String exactPath) throws IOException {
        String localRepoPath = repositoryConfig.getRepositoryDir();

        // 从本地仓库中查找模板
        TemplateMetaInfo localResult = CodestyleClient.searchByPath(exactPath, localRepoPath);
        if (localResult != null) {
            TemplateContent result = MetaInfoConvertUtil.convert(localResult);
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
                List<RemoteSearchResult> remoteResults = searchFromRemote(artifactId);
                if (remoteResults.isEmpty()) {
                    return null;
                }

                // 触发下载（取第一个匹配结果）
                boolean downloadSuccess = downloadTemplate(remoteResults.get(0));

                // 下载成功后重新搜索
                if (downloadSuccess) {
                    localResult = CodestyleClient.searchByPath(exactPath, localRepoPath);
                    if (localResult != null) {
                        TemplateContent result = MetaInfoConvertUtil.convert(localResult);
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
    public List<RemoteSearchResult> searchFromRemote(String templateKeyword) {
        RepositoryConfig.RemoteConfig remote = repositoryConfig.getRemote();
        return CodestyleClient.searchFromRemote(
            remote.getBaseUrl(), 
            templateKeyword,
            10,
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
    public boolean downloadTemplate(RemoteSearchResult result) {
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
    private String readTemplateContent(TemplateMetaInfo info) throws IOException {
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
        List<TemplateMetaInfo> allMetaInfos = new ArrayList<>();

        for (LuceneIndexService.SearchResult result : results) {
            List<TemplateMetaInfo> metaInfos = searchLocalRepository(result.groupId(), result.artifactId());
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

    // ==================== 模板上传和删除（新增）====================

    /**
     * 从文件系统复制模板到本地缓存
     * 
     * @param sourcePath 文件系统路径
     * @param groupId 组ID
     * @param artifactId 项目ID
     * @param version 版本号
     * @param overwrite 是否覆盖已存在的版本
     * @return 保存结果
     * @throws IOException 文件操作异常
     * @throws IllegalArgumentException 参数验证失败
     */
    public top.codestyle.mcp.model.local.LocalUploadResult saveTemplateFromFileSystemLocal(
            String sourcePath, String groupId, String artifactId, String version, boolean overwrite) 
        throws IOException {
        
        // 1. 验证源路径
        Path sourceDir = Paths.get(sourcePath);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            throw new IllegalArgumentException("源路径不存在或不是目录: " + sourcePath);
        }
        
        // 2. 智能检测版本目录
        // 如果源路径下有版本号子目录，自动使用
        Path versionSubDir = sourceDir.resolve(version);
        if (Files.exists(versionSubDir) && Files.isDirectory(versionSubDir)) {
            Path metaInSubDir = versionSubDir.resolve("meta.json");
            if (Files.exists(metaInSubDir)) {
                sourceDir = versionSubDir;
            }
        }
        
        // 3. 验证版本号格式
        if (!CodestyleClient.isValidSemanticVersion(version)) {
            throw new IllegalArgumentException(
                "版本号格式错误，应为语义化版本: MAJOR.MINOR.PATCH"
            );
        }
        
        // 4. 定位目标目录
        String localRepoPath = repositoryConfig.getRepositoryDir();
        Path targetDir = Paths.get(localRepoPath, groupId, artifactId, version);
        
        // 5. 检查版本是否已存在
        if (Files.exists(targetDir)) {
            if (!overwrite) {
                throw new IOException(
                    "版本已存在: " + groupId + "/" + artifactId + "/" + version + "，使用 overwrite=true 覆盖"
                );
            }
            // 覆盖模式：删除旧版本
            cn.hutool.core.io.FileUtil.del(targetDir.toFile());
        }
        
        // 6. 复制文件
        cn.hutool.core.io.FileUtil.copyContent(sourceDir.toFile(), targetDir.toFile(), true);
        
        // 7. 更新 meta.json 中的 groupId/artifactId/version
        Path metaPath = targetDir.resolve("meta.json");
        if (Files.exists(metaPath)) {
            try {
                String content = new String(Files.readAllBytes(metaPath), java.nio.charset.StandardCharsets.UTF_8);
                top.codestyle.mcp.model.template.TemplateMetaConfig metaConfig = 
                    cn.hutool.json.JSONUtil.toBean(content, top.codestyle.mcp.model.template.TemplateMetaConfig.class);
                
                // 更新 groupId/artifactId/version
                metaConfig.setGroupId(groupId);
                metaConfig.setArtifactId(artifactId);
                metaConfig.setVersion(version);
                
                // 写回文件
                String updatedContent = cn.hutool.json.JSONUtil.toJsonPrettyStr(metaConfig);
                Files.write(metaPath, updatedContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new IOException("更新 meta.json 失败: " + e.getMessage());
            }
        }
        
        // 8. 验证模板
        CodestyleClient.validateTemplate(targetDir);
        
        // 9. 统计文件数
        int fileCount = 0;
        try (java.util.stream.Stream<Path> stream = Files.walk(targetDir)) {
            fileCount = (int) stream
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".ftl"))
                .count();
        }
        
        // 10. 重建索引
        luceneIndexService.rebuildIndex();
        
        // 11. 返回结果
        top.codestyle.mcp.model.local.LocalUploadResult result = new top.codestyle.mcp.model.local.LocalUploadResult();
        result.setSuccess(true);
        result.setGroupId(groupId);
        result.setArtifactId(artifactId);
        result.setVersion(version);
        result.setFileCount(fileCount);
        result.setUploadedToRemote(false);
        result.setLocalPath(targetDir.toString());
        
        return result;
    }

    /**
     * 从文件系统上传模板到远程服务器
     * 
     * @param sourcePath 文件系统路径
     * @param groupId 组ID
     * @param artifactId 项目ID
     * @param version 版本号
     * @param overwrite 是否覆盖已存在的版本
     * @return 上传结果
     * @throws IOException 文件操作或网络异常
     * @throws IllegalArgumentException 参数验证失败
     */
    public top.codestyle.mcp.model.local.LocalUploadResult uploadTemplateFromFileSystemRemote(
            String sourcePath, String groupId, String artifactId, String version, boolean overwrite) 
        throws IOException {
        
        // 1. 先保存到本地
        top.codestyle.mcp.model.local.LocalUploadResult localResult = 
            saveTemplateFromFileSystemLocal(sourcePath, groupId, artifactId, version, overwrite);
        
        // 2. 打包为 ZIP
        File zipFile = CodestyleClient.packTemplate(
            repositoryConfig.getRepositoryDir(),
            localResult.getGroupId(),
            localResult.getArtifactId(),
            localResult.getVersion()
        );
        
        try {
            // 3. 上传到远程
            RepositoryConfig.RemoteConfig remote = repositoryConfig.getRemote();
            top.codestyle.mcp.model.remote.RemoteUploadResponse response = CodestyleClient.uploadTemplateToRemote(
                remote.getBaseUrl(),
                zipFile,
                localResult.getGroupId(),
                localResult.getArtifactId(),
                localResult.getVersion(),
                overwrite,
                remote.getAccessKey(),
                remote.getSecretKey(),
                remote.getTimeoutMs()
            );
            
            // 4. 更新结果
            localResult.setUploadedToRemote(true);
            localResult.setRemoteId(
                localResult.getGroupId() + "/" + 
                localResult.getArtifactId() + "/" + 
                localResult.getVersion()
            );
            
            return localResult;
            
        } finally {
            // 5. 清理临时文件
            cn.hutool.core.io.FileUtil.del(zipFile);
        }
    }

    /**
     * 保存模板到本地缓存
     * 
     * @param templatePath 模板路径（groupId/artifactId/version）
     * @param overwrite 是否覆盖已存在的版本
     * @return 保存结果
     * @throws IOException 文件操作异常
     * @throws IllegalArgumentException 参数验证失败
     */
    public top.codestyle.mcp.model.local.LocalUploadResult saveTemplateLocal(String templatePath, boolean overwrite) 
        throws IOException {
        
        // 1. 解析路径
        String[] parts = CodestyleClient.parseTemplatePath(templatePath);
        String groupId = parts[0];
        String artifactId = parts[1];
        String version = parts[2];
        
        // 2. 验证版本号格式
        if (!CodestyleClient.isValidSemanticVersion(version)) {
            throw new IllegalArgumentException(
                "版本号格式错误，应为语义化版本: MAJOR.MINOR.PATCH"
            );
        }
        
        // 3. 定位版本目录
        String localRepoPath = repositoryConfig.getRepositoryDir();
        Path versionDir = Paths.get(localRepoPath, groupId, artifactId, version);
        
        // 4. 验证模板
        CodestyleClient.validateTemplate(versionDir);
        
        // 5. 检查版本是否已存在
        if (Files.exists(versionDir)) {
            if (!overwrite) {
                throw new IOException(
                    "版本已存在: " + templatePath + "，使用 overwrite=true 覆盖"
                );
            }
            // 覆盖模式：不需要删除，直接覆盖即可
        }
        
        // 6. 创建目录（如果不存在）
        Files.createDirectories(versionDir);
        
        // 7. 统计文件数
        int fileCount = 0;
        try (java.util.stream.Stream<Path> stream = Files.walk(versionDir)) {
            fileCount = (int) stream
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".ftl"))
                .count();
        }
        
        // 8. 重建索引
        luceneIndexService.rebuildIndex();
        
        // 9. 返回结果
        top.codestyle.mcp.model.local.LocalUploadResult result = new top.codestyle.mcp.model.local.LocalUploadResult();
        result.setSuccess(true);
        result.setGroupId(groupId);
        result.setArtifactId(artifactId);
        result.setVersion(version);
        result.setFileCount(fileCount);
        result.setUploadedToRemote(false);
        result.setLocalPath(versionDir.toString());
        
        return result;
    }

    /**
     * 上传模板到远程服务器
     * 
     * @param templatePath 模板路径（groupId/artifactId/version）
     * @param overwrite 是否覆盖已存在的版本
     * @return 上传结果
     * @throws IOException 文件操作或网络异常
     * @throws IllegalArgumentException 参数验证失败
     */
    public top.codestyle.mcp.model.local.LocalUploadResult uploadTemplateRemote(String templatePath, boolean overwrite) 
        throws IOException {
        
        // 1. 先保存到本地
        top.codestyle.mcp.model.local.LocalUploadResult localResult = saveTemplateLocal(templatePath, overwrite);
        
        // 2. 打包为 ZIP
        File zipFile = CodestyleClient.packTemplate(
            repositoryConfig.getRepositoryDir(),
            localResult.getGroupId(),
            localResult.getArtifactId(),
            localResult.getVersion()
        );
        
        try {
            // 3. 上传到远程
            RepositoryConfig.RemoteConfig remote = repositoryConfig.getRemote();
            top.codestyle.mcp.model.remote.RemoteUploadResponse response = CodestyleClient.uploadTemplateToRemote(
                remote.getBaseUrl(),
                zipFile,
                localResult.getGroupId(),
                localResult.getArtifactId(),
                localResult.getVersion(),
                overwrite,
                remote.getAccessKey(),
                remote.getSecretKey(),
                remote.getTimeoutMs()
            );
            
            // 4. 更新结果
            localResult.setUploadedToRemote(true);
            localResult.setRemoteId(
                localResult.getGroupId() + "/" + 
                localResult.getArtifactId() + "/" + 
                localResult.getVersion()
            );
            
            return localResult;
            
        } finally {
            // 5. 清理临时文件
            cn.hutool.core.io.FileUtil.del(zipFile);
        }
    }

    /**
     * 删除本地模板
     * 
     * @param templatePath 模板路径（groupId/artifactId/version）
     * @return 删除结果
     * @throws IOException 文件操作异常
     * @throws IllegalArgumentException 参数验证失败
     */
    public top.codestyle.mcp.model.local.LocalDeleteResult deleteTemplateLocal(String templatePath) 
        throws IOException {
        
        // 1. 解析路径
        String[] parts = CodestyleClient.parseTemplatePath(templatePath);
        String groupId = parts[0];
        String artifactId = parts[1];
        String version = parts[2];
        
        // 2. 定位版本目录
        String localRepoPath = repositoryConfig.getRepositoryDir();
        Path versionDir = Paths.get(localRepoPath, groupId, artifactId, version);
        
        // 3. 检查是否存在
        if (!Files.exists(versionDir)) {
            throw new IOException("模板不存在: " + templatePath);
        }
        
        // 4. 删除目录
        cn.hutool.core.io.FileUtil.del(versionDir.toFile());
        
        // 5. 重建索引
        luceneIndexService.rebuildIndex();
        
        // 6. 返回结果
        top.codestyle.mcp.model.local.LocalDeleteResult result = new top.codestyle.mcp.model.local.LocalDeleteResult();
        result.setSuccess(true);
        result.setGroupId(groupId);
        result.setArtifactId(artifactId);
        result.setVersion(version);
        result.setDeletedFromRemote(false);
        
        return result;
    }

    /**
     * 删除远程模板
     * 
     * @param templatePath 模板路径（groupId/artifactId/version）
     * @return 删除结果
     * @throws IOException 网络异常
     * @throws IllegalArgumentException 参数验证失败
     */
    public top.codestyle.mcp.model.local.LocalDeleteResult deleteTemplateRemote(String templatePath) 
        throws IOException {
        
        // 1. 先删除本地
        top.codestyle.mcp.model.local.LocalDeleteResult localResult = deleteTemplateLocal(templatePath);
        
        // 2. 删除远程
        RepositoryConfig.RemoteConfig remote = repositoryConfig.getRemote();
        top.codestyle.mcp.model.remote.RemoteDeleteResponse response = CodestyleClient.deleteTemplateFromRemote(
            remote.getBaseUrl(),
            localResult.getGroupId(),
            localResult.getArtifactId(),
            localResult.getVersion(),
            remote.getAccessKey(),
            remote.getSecretKey(),
            remote.getTimeoutMs()
        );
        
        // 3. 更新结果
        localResult.setDeletedFromRemote(true);
        
        return localResult;
    }
}
