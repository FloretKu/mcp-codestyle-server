package top.codestyle.mcp.util;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import top.codestyle.mcp.model.meta.LocalMetaConfig;
import top.codestyle.mcp.model.sdk.MetaInfo;
import top.codestyle.mcp.model.sdk.RemoteMetaConfig;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * CodeStyle 统一客户端
 * 提供本地仓库管理、远程检索、模板下载等功能
 *
 * @author CodeStyle Team
 * @since 2.0.0
 */
public class CodestyleClient {

    // ==================== 本地仓库管理 ====================

    /**
     * 根据groupId和artifactId搜索指定模板组
     *
     * @param groupId          组ID
     * @param artifactId       项目ID
     * @param templateBasePath 模板基础路径
     * @return 匹配的模板元信息列表
     */
    public static List<MetaInfo> searchLocalRepository(String groupId, String artifactId, String templateBasePath) {
        List<MetaInfo> result = new ArrayList<>();
        try {
            templateBasePath = normalizePath(templateBasePath);
            File metaFile = new File(templateBasePath + File.separator + groupId + File.separator + artifactId
                    + File.separator + "meta.json");
            if (!metaFile.exists()) {
                return result;
            }
            List<MetaInfo> metaInfoList = MetaInfoConvertUtil.parseMetaJsonLatestOnly(metaFile);
            for (MetaInfo metaInfo : metaInfoList) {
                if (isTemplateFileExists(templateBasePath, metaInfo)) {
                    result.add(metaInfo);
                }
            }
        } catch (Exception e) {
            // 搜索失败,返回空结果
        }
        return result;
    }

    /**
     * 根据精确路径搜索模板
     *
     * @param exactPath        精确路径,格式: groupId/artifactId/version/filePath/filename
     * @param templateBasePath 模板基础路径
     * @return 匹配的模板元信息,未找到返回null
     */
    public static MetaInfo searchByPath(String exactPath, String templateBasePath) {
        try {
            // 规范化路径
            templateBasePath = normalizePath(templateBasePath);
            String normalizedExactPath = normalizePath(exactPath);

            // 从路径解析 groupId 和 artifactId，直接定位 meta.json
            // 路径格式: groupId/artifactId/version/filePath/filename
            String[] parts = normalizedExactPath.split(Pattern.quote(File.separator));
            if (parts.length < 3) {
                return null;
            }
            String groupId = parts[0];
            String artifactId = parts[1];

            // 直接定位 meta.json 文件
            File metaFile = new File(templateBasePath + File.separator + groupId + File.separator + artifactId
                    + File.separator + "meta.json");
            if (!metaFile.exists()) {
                return null;
            }

            // 在匹配的 meta.json 中查找模板
            List<MetaInfo> metaInfoList = MetaInfoConvertUtil.parseMetaJsonLatestOnly(metaFile);
            for (MetaInfo metaInfo : metaInfoList) {
                String fullPath = metaInfo.getGroupId() + File.separator + metaInfo.getArtifactId() + File.separator +
                        metaInfo.getVersion() + metaInfo.getFilePath() + File.separator + metaInfo.getFilename();
                if (normalizePath(fullPath).equals(normalizedExactPath)) {
                    return isTemplateFileExists(templateBasePath, metaInfo) ? metaInfo : null;
                }
            }
        } catch (Exception e) {
            // 搜索失败
        }
        return null;
    }

    // ==================== 远程检索（新增 MCP 支持）====================

    /**
     * 从远程 CodeStyle 仓库检索模板（使用 ContiNew Open API 签名认证）
     * 
     * @param remoteBaseUrl 远程仓库基础URL
     * @param query         检索关键词
     * @param accessKey     Access Key（AK）
     * @param secretKey     Secret Key（SK）
     * @param timeoutMs     超时时间（毫秒）
     * @return 远程检索结果列表
     * @throws top.codestyle.mcp.exception.RemoteSearchException 检索失败时抛出
     */
    public static List<RemoteSearchResult> searchFromRemote(String remoteBaseUrl, String query, 
                                                            String accessKey, String secretKey, int timeoutMs) {
        if (StrUtil.isBlank(accessKey) || StrUtil.isBlank(secretKey)) {
            throw new top.codestyle.mcp.exception.RemoteSearchException(
                top.codestyle.mcp.exception.RemoteSearchException.ErrorCode.CONFIG_ERROR,
                "Access Key 或 Secret Key 未配置"
            );
        }

        try {
            // 1. 构建请求参数
            long timestamp = System.currentTimeMillis();
            String nonce = UUID.randomUUID().toString().replace("-", "");
            
            Map<String, String> params = new TreeMap<>();
            params.put("query", query);
            params.put("timestamp", String.valueOf(timestamp));
            params.put("nonce", nonce);
            params.put("accessKey", accessKey);

            // 2. 生成签名（ContiNew 标准）
            String sign = generateSignature(params, secretKey);
            params.put("sign", sign);

            // 3. 发送请求（使用 GET 方法）
            Map<String, Object> formParams = new HashMap<>(params);
            HttpRequest request = HttpRequest.get(remoteBaseUrl + "/open-api/search")
                    .form(formParams)
                    .timeout(timeoutMs)
                    .header("User-Agent", "MCP-CodeStyle-Server/2.0.0");

            HttpResponse response = request.execute();
            int status = response.getStatus();

            if (status == 401) {
                throw new top.codestyle.mcp.exception.RemoteSearchException(
                    top.codestyle.mcp.exception.RemoteSearchException.ErrorCode.SIGNATURE_FAILED,
                    "签名验证失败，请检查 Access Key 和 Secret Key 是否正确"
                );
            }
            
            if (status == 403) {
                throw new top.codestyle.mcp.exception.RemoteSearchException(
                    top.codestyle.mcp.exception.RemoteSearchException.ErrorCode.ACCESS_DENIED,
                    "访问被拒绝，请检查应用是否已启用且未过期"
                );
            }
            
            if (status >= 500) {
                throw new top.codestyle.mcp.exception.RemoteSearchException(
                    top.codestyle.mcp.exception.RemoteSearchException.ErrorCode.SERVER_ERROR,
                    "服务器错误: HTTP " + status
                );
            }
            
            if (status != 200) {
                throw new top.codestyle.mcp.exception.RemoteSearchException(
                    top.codestyle.mcp.exception.RemoteSearchException.ErrorCode.SERVER_ERROR,
                    "请求失败: HTTP " + status
                );
            }

            // 4. 解析响应
            String body = response.body();
            Map<String, Object> result = JSONUtil.toBean(body, Map.class);
            
            // ContiNew 框架返回的成功码是 "0"，不是 "200"
            String code = String.valueOf(result.get("code"));
            Boolean success = (Boolean) result.get("success");
            
            // 优先判断 success 字段，兼容判断 code
            if ((success != null && !success) || (!"0".equals(code) && !"200".equals(code))) {
                throw new top.codestyle.mcp.exception.RemoteSearchException(
                    top.codestyle.mcp.exception.RemoteSearchException.ErrorCode.SERVER_ERROR,
                    "检索失败: " + result.get("msg")
                );
            }

            List<Map<String, Object>> dataList = (List<Map<String, Object>>) result.get("data");
            return dataList.stream()
                    .map(CodestyleClient::convertToRemoteSearchResult)
                    .collect(Collectors.toList());

        } catch (top.codestyle.mcp.exception.RemoteSearchException e) {
            throw e;
        } catch (Exception e) {
            // 检查是否是超时异常
            if (e.getCause() instanceof java.net.SocketTimeoutException || 
                e instanceof java.net.SocketTimeoutException) {
                throw new top.codestyle.mcp.exception.RemoteSearchException(
                    top.codestyle.mcp.exception.RemoteSearchException.ErrorCode.TIMEOUT,
                    "请求超时，请检查网络连接或增加超时时间",
                    e
                );
            }
            throw new top.codestyle.mcp.exception.RemoteSearchException(
                top.codestyle.mcp.exception.RemoteSearchException.ErrorCode.NETWORK_ERROR,
                "网络错误: " + e.getMessage(),
                e
            );
        }
    }

    /**
     * 生成 ContiNew Open API 签名
     * 
     * 签名算法：
     * 1. 将所有参数（除 sign）按 key 字典序排序（TreeMap 自动排序）
     * 2. 添加 key=secretKey 参数
     * 3. 拼接成 key1=value1&key2=value2 格式（最后没有 "&"）
     * 4. MD5 加密（32位小写）
     * 
     * @param params    请求参数（已排序）
     * @param secretKey Secret Key
     * @return 签名字符串（32位小写 MD5）
     */
    private static String generateSignature(Map<String, String> params, String secretKey) {
        // 1. 添加 key 参数（与 ContiNew 标准保持一致）
        Map<String, String> allParams = new TreeMap<>(params);
        allParams.put("key", secretKey);
        
        // 2. 字典序排序并拼接（标准格式：key1=value1&key2=value2）
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            if (!"sign".equals(entry.getKey())) {
                if (!first) {
                    sb.append("&");  // ← 修复：只在非第一个参数前添加 "&"
                }
                sb.append(entry.getKey()).append("=").append(entry.getValue());
                first = false;
            }
        }
        
        // 3. MD5 加密（32位小写）
        return DigestUtil.md5Hex(sb.toString());
    }

    /**
     * 转换远程检索结果为统一格式
     * 安全处理 null 值，避免 JSONNull 类型转换异常
     */
    private static RemoteSearchResult convertToRemoteSearchResult(Map<String, Object> data) {
        RemoteSearchResult result = new RemoteSearchResult();
        
        // 安全获取字符串字段（处理 null 和 JSONNull）
        result.setId(getStringValue(data, "id"));
        result.setTitle(getStringValue(data, "title"));
        result.setSnippet(getStringValue(data, "snippet"));
        result.setContent(getStringValue(data, "content"));
        result.setHighlight(getStringValue(data, "highlight"));
        
        // 安全获取数值字段
        Object scoreObj = data.get("score");
        if (scoreObj instanceof Number) {
            result.setScore(((Number) scoreObj).doubleValue());
        } else {
            result.setScore(0.0);
        }
        
        // MCP 必要字段
        result.setGroupId(getStringValue(data, "groupId"));
        result.setArtifactId(getStringValue(data, "artifactId"));
        result.setVersion(getStringValue(data, "version"));
        result.setFileType(getStringValue(data, "fileType"));
        
        // 从 metadata 提取非索引字段
        Object metadataObj = data.get("metadata");
        if (metadataObj instanceof Map) {
            Map<String, Object> metadata = (Map<String, Object>) metadataObj;
            result.setFilePath(getStringValue(metadata, "filePath"));
            result.setFilename(getStringValue(metadata, "filename"));
            result.setSha256(getStringValue(metadata, "sha256"));
        }
        
        // 注意：不再使用 ES 中的 downloadUrl 字段，统一由 getDownloadUrl() 方法生成
        // 这样可以避免 ES 中存储的旧 URL（/api/file/download）导致的 404 问题
        
        return result;
    }
    
    /**
     * 安全获取字符串值，处理 null 和 JSONNull
     */
    private static String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null || "null".equals(String.valueOf(value))) {
            return null;
        }
        // 处理 JSONNull 类型
        if (value.getClass().getSimpleName().equals("JSONNull")) {
            return null;
        }
        return String.valueOf(value);
    }

    /**
     * 远程检索结果数据模型
     */
    public static class RemoteSearchResult {
        private String id;
        private String title;
        private String snippet;
        private String content;
        private Double score;
        private String highlight;
        
        // MCP 必要字段
        private String groupId;
        private String artifactId;
        private String version;
        private String fileType;
        
        // 非索引字段
        private String filePath;
        private String filename;
        private String sha256;

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        
        public String getSnippet() { return snippet; }
        public void setSnippet(String snippet) { this.snippet = snippet; }
        
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        
        public Double getScore() { return score; }
        public void setScore(Double score) { this.score = score; }
        
        public String getHighlight() { return highlight; }
        public void setHighlight(String highlight) { this.highlight = highlight; }
        
        public String getGroupId() { return groupId; }
        public void setGroupId(String groupId) { this.groupId = groupId; }
        
        public String getArtifactId() { return artifactId; }
        public void setArtifactId(String artifactId) { this.artifactId = artifactId; }
        
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        
        public String getFileType() { return fileType; }
        public void setFileType(String fileType) { this.fileType = fileType; }
        
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        
        public String getFilename() { return filename; }
        public void setFilename(String filename) { this.filename = filename; }
        
        public String getSha256() { return sha256; }
        public void setSha256(String sha256) { this.sha256 = sha256; }
        
        /**
         * 生成下载 URL
         */
        public String getDownloadUrl(String baseUrl) {
            if (groupId != null && artifactId != null && version != null) {
                return String.format("%s/open-api/template/download?groupId=%s&artifactId=%s&version=%s",
                    baseUrl, groupId, artifactId, version);
            }
            return null;
        }
    }

    // ==================== 远程模板下载（新版）====================

    /**
     * 下载模板（使用 RemoteSearchResult）
     * 
     * @param localRepoPath 本地仓库路径
     * @param remoteBaseUrl 远程基础URL
     * @param result 远程检索结果
     * @return 是否成功
     */
    public static boolean downloadTemplate(String localRepoPath, String remoteBaseUrl, 
                                           RemoteSearchResult result) {
        try {
            String downloadUrl = result.getDownloadUrl(remoteBaseUrl);
            if (downloadUrl == null) {
                return false;
            }
            
            HttpResponse response = HttpRequest.get(downloadUrl)
                    .timeout(60000)
                    .header("User-Agent", "MCP-CodeStyle-Server/2.0.0")
                    .execute();
                
            if (!response.isOk()) {
                return false;
            }
            
            File zipFile = FileUtil.createTempFile("template-", ".zip", true);
            IoUtil.copy(response.bodyStream(), FileUtil.getOutputStream(zipFile));
            
            String targetPath = localRepoPath + File.separator + 
                               result.getGroupId() + File.separator + 
                               result.getArtifactId();
            
            ZipUtil.unzip(zipFile, new File(targetPath));
            FileUtil.del(zipFile);
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 远程模板下载（兼容旧版）====================

    /**
     * 从远程仓库搜索模板（兼容旧版 API）
     * 支持重试机制，最多重试3次
     *
     * @param remoteBaseUrl 远程仓库基础URL
     * @param query         模板关键词,如: RuoYi, CRUD, continew/CRUD
     * @param apiKey        API Key（必填）
     * @param timeoutMs     超时时间（毫秒）
     * @return 远程模板配置列表
     */
    public static List<RemoteMetaConfig> fetchRemoteMetaConfig(String remoteBaseUrl, String query, String apiKey, int timeoutMs) {
        if (StrUtil.isBlank(apiKey)) {
            throw new RuntimeException("API Key 未配置");
        }
        int maxRetries = 3;
        int baseDelay = 200;
        for (int i = 0; i < maxRetries; i++) {
            try {
                HttpRequest request = HttpRequest.get(remoteBaseUrl + "/api/mcp/search")
                        .form("query", query)
                        .timeout(timeoutMs)
                        .header("User-Agent", "MCP-CodeStyle-Server/1.0.2")
                        .header("Authorization", "Bearer " + apiKey);
                HttpResponse response = request.execute();
                int status = response.getStatus();
                if (status == 401) {
                    throw new RuntimeException("API Key 无效或已过期");
                }
                if (status == 403) {
                    throw new RuntimeException("API Key 权限不足");
                }
                if (status != 200) {
                    throw new RuntimeException("远程服务器返回错误: " + status);
                }
                return JSONUtil.toList(response.body(), RemoteMetaConfig.class);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                if (i == maxRetries - 1) {
                    return Collections.emptyList();
                }
                ThreadUtil.sleep(baseDelay * (1 << i));
            }
        }
        return Collections.emptyList();
    }

    /**
     * 智能下载或更新模板
     * 通过SHA256哈希值判断是否需要更新,下载ZIP并解压到本地仓库,更新meta.json
     *
     * @param localRepoPath 本地仓库路径
     * @param remoteBaseUrl 远程仓库基础URL
     * @param remoteConfig  远程模板配置
     * @return 是否成功
     */
    public static boolean smartDownloadTemplate(String localRepoPath, String remoteBaseUrl,
            RemoteMetaConfig remoteConfig) {
        try {
            String groupId = remoteConfig.getGroupId();
            String artifactId = remoteConfig.getArtifactId();

            boolean needsUpdate = false;
            try {
                String localMetaPath = localRepoPath + File.separator +
                        groupId + File.separator +
                        artifactId + File.separator +
                        "meta.json";
                File localMetaFile = new File(localMetaPath);

                if (!localMetaFile.exists()) {
                    needsUpdate = true;
                } else {
                    needsUpdate = checkIfNeedsUpdate(localMetaFile, remoteConfig, localRepoPath, groupId, artifactId);
                }
            } catch (Exception e) {
                needsUpdate = true;
            }

            if (needsUpdate) {
                return downloadAndExtractTemplate(localRepoPath, remoteBaseUrl, groupId, artifactId, remoteConfig);
            } else {
                return true;
            }

        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 验证模板文件是否存在
     *
     * @param templateBasePath 模板基础路径
     * @param metaInfo         模板元信息
     * @return 文件是否存在
     */
    private static boolean isTemplateFileExists(String templateBasePath, MetaInfo metaInfo) {
        String normalizedFilePath = StrUtil.removePrefix(normalizePath(metaInfo.getFilePath()), File.separator);
        String versionPath = metaInfo.getVersion();

        String actualFilePath = templateBasePath + File.separator +
                metaInfo.getGroupId() + File.separator +
                metaInfo.getArtifactId() + File.separator +
                versionPath + File.separator +
                normalizedFilePath + File.separator +
                metaInfo.getFilename();
        return new File(actualFilePath).exists();
    }

    /**
     * 检查是否需要更新模板
     *
     * @param localMetaFile 本地meta.json文件
     * @param remoteConfig  远程配置
     * @param localRepoPath 本地仓库路径
     * @param groupId       组ID
     * @param artifactId    项目ID
     * @return 是否需要更新
     */
    private static boolean checkIfNeedsUpdate(File localMetaFile, RemoteMetaConfig remoteConfig,
            String localRepoPath, String groupId, String artifactId) {
        try {
            LocalMetaConfig localConfig = JSONUtil.toBean(FileUtil.readUtf8String(localMetaFile),
                    LocalMetaConfig.class);
            String remoteVersion = remoteConfig.getConfig().getVersion();

            LocalMetaConfig.Config matchedConfig = findMatchedConfig(localConfig, remoteVersion);
            if (matchedConfig == null) {
                return true;
            }

            List<RemoteMetaConfig.FileInfo> remoteFiles = remoteConfig.getConfig().getFiles();
            if (CollUtil.isEmpty(remoteFiles)) {
                return false;
            }

            List<LocalMetaConfig.FileInfo> localFiles = matchedConfig.getFiles();
            String versionPath = remoteVersion;

            for (RemoteMetaConfig.FileInfo remoteFile : remoteFiles) {
                String normalizedFilePath = normalizePath(remoteFile.getFilePath());
                if (normalizedFilePath.startsWith(File.separator)) {
                    normalizedFilePath = normalizedFilePath.substring(1);
                }

                String actualFilePath = localRepoPath + File.separator + groupId + File.separator +
                        artifactId + File.separator + versionPath + File.separator + normalizedFilePath
                        + File.separator + remoteFile.getFilename();

                if (!new File(actualFilePath).exists()) {
                    return true;
                }

                if (isFileShaChanged(localFiles, remoteFile.getFilename(), remoteFile.getFilePath(),
                        StrUtil.emptyToDefault(remoteFile.getSha256(), ""))) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 查找匹配的版本配置
     *
     * @param localConfig 本地配置
     * @param version     版本号
     * @return 匹配的配置,未找到返回null
     */
    private static LocalMetaConfig.Config findMatchedConfig(LocalMetaConfig localConfig, String version) {
        if (localConfig.getConfigs() != null) {
            for (LocalMetaConfig.Config config : localConfig.getConfigs()) {
                if (config.getVersion().equals(version)) {
                    return config;
                }
            }
        }
        return null;
    }

    /**
     * 检查文件SHA256是否变化
     *
     * @param localFiles 本地文件列表
     * @param filename   文件名
     * @param filePath   文件路径
     * @param remoteSha  远程SHA256
     * @return SHA是否变化
     */
    private static boolean isFileShaChanged(List<LocalMetaConfig.FileInfo> localFiles,
            String filename, String filePath, String remoteSha) {
        if (localFiles == null) {
            return true;
        }

        for (LocalMetaConfig.FileInfo localFile : localFiles) {
            if (localFile.getFilename().equals(filename) && localFile.getFilePath().equals(filePath)) {
                String localSha = StrUtil.emptyToDefault(localFile.getSha256(), "");
                return !localSha.equals(remoteSha);
            }
        }
        return true;
    }

    /**
     * 下载并解压模板
     *
     * @param localRepoPath 本地仓库路径
     * @param remoteBaseUrl 远程基础URL
     * @param groupId       组ID
     * @param artifactId    项目ID
     * @param remoteConfig  远程配置
     * @return 是否成功
     */
    private static boolean downloadAndExtractTemplate(String localRepoPath, String remoteBaseUrl,
            String groupId, String artifactId,
            RemoteMetaConfig remoteConfig) {
        String templatePath = File.separator + groupId + File.separator + artifactId;
        String templateDir = localRepoPath + File.separator + groupId + File.separator + artifactId;
        File localMetaFile = new File(templateDir, "meta.json");
        String backupContent = null;
        File zipFile = null;

        try {
            // 备份现有meta.json内容，用于后续版本追加
            if (localMetaFile.exists()) {
                backupContent = FileUtil.readUtf8String(localMetaFile);
            }

            HttpResponse response = HttpRequest.get(remoteBaseUrl + "/api/file/load")
                    .form("paths", templatePath)
                    .timeout(60000)
                    .header("User-Agent", "MCP-CodeStyle-Server/1.0")
                    .execute();

            if (!response.isOk()) {
                return false;
            }

            zipFile = FileUtil.createTempFile("template-", ".zip", true);

            IoUtil.copy(response.bodyStream(), FileUtil.getOutputStream(zipFile));

            // 解压到仓库根目录
            if (extractZipFile(zipFile, localRepoPath, templateDir)) {
                updateLocalMetaJson(localRepoPath, groupId, artifactId, remoteConfig, backupContent);
                // 将远程的description写入README.md（缓存到本地）
                saveDescriptionToReadme(templateDir, remoteConfig);
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            FileUtil.del(zipFile);
        }
    }

    /**
     * 解压ZIP文件
     *
     * @param zipFile     ZIP文件
     * @param targetPath  目标路径
     * @param templateDir 当前模板目录
     * @return 是否成功
     */
    private static boolean extractZipFile(File zipFile, String targetPath, String templateDir) {
        try {
            File targetDir = FileUtil.mkdir(targetPath);
            ZipUtil.unzip(zipFile, targetDir);
            // 仅删除当前模板目录下的meta.json，避免影响其他模板
            File metaFile = new File(templateDir, "meta.json");
            if (metaFile.exists()) {
                FileUtil.del(metaFile);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 更新本地meta.json文件
     *
     * @param localRepoPath 本地仓库路径
     * @param groupId       组ID
     * @param artifactId    项目ID
     * @param remoteConfig  远程配置
     * @param backupContent 备份的meta.json内容（用于版本追加）
     */
    private static void updateLocalMetaJson(String localRepoPath, String groupId,
            String artifactId, RemoteMetaConfig remoteConfig, String backupContent) {

        String newVersion = remoteConfig.getConfig().getVersion();

        String localMetaPath = localRepoPath + File.separator + groupId + File.separator +
                artifactId + File.separator + "meta.json";

        File localMetaFile = new File(localMetaPath);

        LocalMetaConfig localConfig;

        // 优先使用备份内容，确保版本追加正确
        if (StrUtil.isNotBlank(backupContent)) {
            localConfig = JSONUtil.toBean(backupContent, LocalMetaConfig.class);
        } else if (FileUtil.exist(localMetaFile)) {
            localConfig = JSONUtil.toBean(FileUtil.readUtf8String(localMetaFile), LocalMetaConfig.class);
        } else {
            localConfig = new LocalMetaConfig();
            localConfig.setGroupId(groupId);
            localConfig.setArtifactId(artifactId);
            localConfig.setConfigs(new ArrayList<>());
        }

        List<LocalMetaConfig.Config> configs = localConfig.getConfigs();
        if (CollUtil.isEmpty(configs)) {
            configs = new ArrayList<>();
            localConfig.setConfigs(configs);
        }

        configs.removeIf(config -> config.getVersion().equals(newVersion));

        LocalMetaConfig.Config newConfig = MetaInfoConvertUtil.convertRemoteToLocalConfig(remoteConfig);
        configs.add(newConfig);

        FileUtil.writeUtf8String(JSONUtil.toJsonPrettyStr(localConfig), localMetaFile);
    }

    /**
     * 将远程description保存到本地README.md
     * 缓存路径: groupId/artifactId/version/README.md
     *
     * @param templateDir  模板目录 (groupId/artifactId)
     * @param remoteConfig 远程配置
     */
    private static void saveDescriptionToReadme(String templateDir, RemoteMetaConfig remoteConfig) {
        String description = remoteConfig.getDescription();
        if (StrUtil.isBlank(description)) {
            return;
        }
        
        String version = remoteConfig.getConfig().getVersion();
        String readmePath = templateDir + File.separator + version + File.separator + "README.md";
        File readmeFile = new File(readmePath);
        
        // 确保版本目录存在
        FileUtil.mkdir(readmeFile.getParentFile());
        FileUtil.writeUtf8String(description, readmeFile);
    }

    // ==================== 工具方法 ====================

    /**
     * 规范化路径字符串
     * 统一路径分隔符并移除连续分隔符,确保跨平台兼容性
     *
     * @param path 原始路径字符串
     * @return 规范化后的路径字符串
     */
    public static String normalizePath(String path) {
        if (StrUtil.isEmpty(path)) {
            return path;
        }

        // 统一使用系统分隔符
        String normalizedPath = path.replace('/', File.separatorChar).replace('\\', File.separatorChar);

        // 使用 StrUtil 移除连续的分隔符
        String doubleSep = File.separator + File.separator;
        while (StrUtil.contains(normalizedPath, doubleSep)) {
            normalizedPath = normalizedPath.replace(doubleSep, File.separator);
        }

        return normalizedPath;
    }

    /**
     * 从文件路径列表中提取路径关键词
     *
     * @param filePaths 文件路径列表
     * @return 路径关键词字符串（空格分隔）
     */
    public static String extractPathKeywords(List<String> filePaths) {
        HashSet<String> keywords = new HashSet<>();
        for (String path : filePaths) {
            if (path != null && !path.isEmpty()) {
                String[] segments = path.split("[/\\\\]");
                for (String seg : segments) {
                    if (!seg.isEmpty() && !seg.equals(".")) {
                        keywords.add(seg);
                    }
                }
            }
        }
        return String.join(" ", keywords);
    }
}

