package top.codestyle.mcp.util;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import top.codestyle.mcp.exception.RemoteDeleteException;
import top.codestyle.mcp.exception.RemoteSearchException;
import top.codestyle.mcp.exception.RemoteUploadException;
import top.codestyle.mcp.model.remote.RemoteDeleteResponse;
import top.codestyle.mcp.model.remote.RemoteSearchResult;
import top.codestyle.mcp.model.remote.RemoteUploadResponse;
import top.codestyle.mcp.model.template.TemplateMetaConfig;
import top.codestyle.mcp.model.template.TemplateMetaInfo;

import java.io.*;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * CodeStyle 统一客户端（重构版 - 单版本格式）
 * <p>提供本地仓库管理、远程检索、模板下载等功能
 *
 * @author CodeStyle Team
 * @since 2.0.0
 */
@Slf4j
public class CodestyleClient {

    /** 语义化版本号正则：MAJOR.MINOR.PATCH */
    private static final Pattern SEMVER_PATTERN =
            Pattern.compile("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$");

    // ---------- 本地仓库管理 ----------

    /**
     * 根据 groupId 和 artifactId 搜索指定模板组（返回最新版本）
     * 
     * 目录结构: groupId/artifactId/version/meta.json
     *
     * @param groupId          组ID
     * @param artifactId       项目ID
     * @param templateBasePath 模板基础路径
     * @return 匹配的模板元信息列表（最新版本）
     */
    public static List<TemplateMetaInfo> searchLocalRepository(String groupId, String artifactId, String templateBasePath) {
        List<TemplateMetaInfo> result = new ArrayList<>();
        try {
            templateBasePath = FileUtil.normalize(templateBasePath);
            
            // 查找 groupId/artifactId 目录
            Path artifactPath = Paths.get(templateBasePath, groupId, artifactId);
            if (!Files.exists(artifactPath) || !Files.isDirectory(artifactPath)) {
                return result;
            }
            
            // 查找所有版本目录
            List<String> versions = new ArrayList<>();
            try (Stream<Path> stream = Files.list(artifactPath)) {
                versions = stream
                    .filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .sorted(Comparator.reverseOrder()) // 降序排序，最新版本在前
                    .collect(Collectors.toList());
            }
            
            if (versions.isEmpty()) {
                return result;
            }
            
            // 读取最新版本的 meta.json
            String latestVersion = versions.get(0);
            Path metaPath = artifactPath.resolve(latestVersion).resolve("meta.json");
            
            if (!Files.exists(metaPath)) {
                return result;
            }
            
            // 解析 meta.json
            List<TemplateMetaInfo> metaInfoList = MetaInfoConvertUtil.parseMetaJson(metaPath.toFile());
            
            // 验证模板文件是否存在
            for (TemplateMetaInfo metaInfo : metaInfoList) {
                if (isTemplateFileExists(templateBasePath, metaInfo)) {
                    result.add(metaInfo);
                }
            }
        } catch (Exception e) {
            log.warn("本地仓库搜索失败 [{}/{}]: {}", groupId, artifactId, e.getMessage());
        }
        return result;
    }

    /**
     * 根据精确路径搜索模板
     * 
     * 路径格式: groupId/artifactId/version/filePath/filename
     *
     * @param exactPath        精确路径
     * @param templateBasePath 模板基础路径
     * @return 匹配的模板元信息,未找到返回null
     */
    public static TemplateMetaInfo searchByPath(String exactPath, String templateBasePath) {
        try {
            // 规范化路径
            templateBasePath = FileUtil.normalize(templateBasePath);
            String normalizedExactPath = FileUtil.normalize(exactPath);

            // 从路径解析 groupId、artifactId、version
            // 路径格式: groupId/artifactId/version/filePath/filename
            String[] parts = normalizedExactPath.split(Pattern.quote(File.separator));
            if (parts.length < 3) {
                return null;
            }
            
            String groupId = parts[0];
            String artifactId = parts[1];
            String version = parts[2];

            // 定位 meta.json 文件（新位置：groupId/artifactId/version/meta.json）
            Path metaPath = Paths.get(templateBasePath, groupId, artifactId, version, "meta.json");
            if (!Files.exists(metaPath)) {
                return null;
            }

            // 解析 meta.json 并查找匹配的模板
            List<TemplateMetaInfo> metaInfoList = MetaInfoConvertUtil.parseMetaJson(metaPath.toFile());
            for (TemplateMetaInfo metaInfo : metaInfoList) {
                // 规范化 filePath，移除前导斜杠
                String filePath = metaInfo.getFilePath();
                if (filePath.startsWith("/") || filePath.startsWith("\\")) {
                    filePath = filePath.substring(1);
                }
                
                String fullPath = metaInfo.getGroupId() + File.separator + 
                                 metaInfo.getArtifactId() + File.separator +
                                 metaInfo.getVersion() + File.separator +
                                 filePath + File.separator + 
                                 metaInfo.getFilename();
                                 
                if (FileUtil.normalize(fullPath).equals(normalizedExactPath)) {
                    return isTemplateFileExists(templateBasePath, metaInfo) ? metaInfo : null;
                }
            }
        } catch (Exception e) {
            log.warn("路径搜索失败 [{}]: {}", exactPath, e.getMessage());
        }
        return null;
    }

    // ---------- 远程检索 ----------

    /**
     * 从远程 CodeStyle 仓库检索模板（使用 ContiNew Open API 签名认证）
     * 
     * @param remoteBaseUrl 远程仓库基础URL
     * @param query         检索关键词
     * @param topK          返回结果数量上限
     * @param accessKey     Access Key（AK）
     * @param secretKey     Secret Key（SK）
     * @param timeoutMs     超时时间（毫秒）
     * @return 远程检索结果列表
     * @throws RemoteSearchException 检索失败时抛出
     */
    public static List<RemoteSearchResult> searchFromRemote(String remoteBaseUrl, String query,
                                                            int topK,
                                                            String accessKey, String secretKey, int timeoutMs) {
        if (StrUtil.isBlank(accessKey) || StrUtil.isBlank(secretKey)) {
            throw new RemoteSearchException(
                RemoteSearchException.ErrorCode.CONFIG_ERROR,
                "Access Key 或 Secret Key 未配置"
            );
        }

        try {
            // 1. 构建请求参数
            long timestamp = System.currentTimeMillis();
            String nonce = UUID.randomUUID().toString().replace("-", "");
            
            Map<String, String> params = new TreeMap<>();
            params.put("query", query);
            params.put("topK", String.valueOf(topK));
            params.put("timestamp", String.valueOf(timestamp));
            params.put("nonce", nonce);
            params.put("accessKey", accessKey);

            // 2. 生成签名（ContiNew 标准）
            String sign = generateSignature(params, secretKey);
            params.put("sign", sign);

            // 3. 发送请求（使用 GET 方法）
            Map<String, Object> formParams = new HashMap<>(params);
            HttpRequest httpRequest = HttpRequest.get(remoteBaseUrl + "/open-api/search")
                    .form(formParams)
                    .timeout(timeoutMs)
                    .header("User-Agent", "MCP-CodeStyle-Server/2.0.0");

            try (HttpResponse response = httpRequest.execute()) {
                int status = response.getStatus();

                if (status == 401) {
                    throw new RemoteSearchException(
                        RemoteSearchException.ErrorCode.SIGNATURE_FAILED,
                        "签名验证失败，请检查 Access Key 和 Secret Key 是否正确"
                    );
                }
                
                if (status == 403) {
                    throw new RemoteSearchException(
                        RemoteSearchException.ErrorCode.ACCESS_DENIED,
                        "访问被拒绝，请检查应用是否已启用且未过期"
                    );
                }
                
                if (status >= 500) {
                    throw new RemoteSearchException(
                        RemoteSearchException.ErrorCode.SERVER_ERROR,
                        "服务器错误: HTTP " + status
                    );
                }
                
                if (status != 200) {
                    throw new RemoteSearchException(
                        RemoteSearchException.ErrorCode.SERVER_ERROR,
                        "请求失败: HTTP " + status
                    );
                }

                // 4. 解析响应
                String body = response.body();
                JSONObject result = JSONUtil.parseObj(body);
                
                // ContiNew 框架返回的成功码是 "0"，不是 "200"
                String code = result.getStr("code");
                Boolean success = result.getBool("success");
                
                // 优先判断 success 字段，兼容判断 code
                if ((success != null && !success) || (!"0".equals(code) && !"200".equals(code))) {
                    throw new RemoteSearchException(
                        RemoteSearchException.ErrorCode.SERVER_ERROR,
                        "检索失败: " + result.getStr("msg")
                    );
                }

                return result.getJSONArray("data").stream()
                        .map(item -> convertToRemoteSearchResult((JSONObject) item))
                        .collect(Collectors.toList());
            }

        } catch (RemoteSearchException e) {
            throw e;
        } catch (Exception e) {
            // 检查是否是超时异常
            if (e.getCause() instanceof SocketTimeoutException || 
                e instanceof SocketTimeoutException) {
                throw new RemoteSearchException(
                    RemoteSearchException.ErrorCode.TIMEOUT,
                    "请求超时，请检查网络连接或增加超时时间",
                    e
                );
            }
            throw new RemoteSearchException(
                RemoteSearchException.ErrorCode.NETWORK_ERROR,
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
     * @param params      请求参数（已排序）
     * @param secretKey   Secret Key
     * @param excludeKeys 需排除的参数名（可变参数）
     * @return 签名字符串（32位小写 MD5）
     */
    private static String generateSignature(Map<String, String> params, String secretKey, String... excludeKeys) {
        Map<String, String> allParams = new TreeMap<>(params);
        allParams.put("key", secretKey);
        Set<String> excludes = new HashSet<>(Arrays.asList(excludeKeys));
        excludes.add("sign");
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            if (!excludes.contains(entry.getKey())) {
                if (!first) sb.append("&");
                sb.append(entry.getKey()).append("=").append(entry.getValue());
                first = false;
            }
        }
        return DigestUtil.md5Hex(sb.toString());
    }

    /**
     * 转换远程检索结果为统一格式
     * <p>使用 {@link JSONObject#getStr} 避免 JSONNull 类型转换异常
     *
     * @param json 远程返回的 JSON 数据对象
     * @return 统一格式的 {@link RemoteSearchResult}
     */
    private static RemoteSearchResult convertToRemoteSearchResult(JSONObject json) {
        RemoteSearchResult result = new RemoteSearchResult();
        
        // 使用 JSONObject.getStr 安全获取字符串字段，自动处理 null 和 JSONNull
        result.setId(json.getStr("id"));
        result.setTitle(json.getStr("title"));
        result.setSnippet(json.getStr("snippet"));
        result.setContent(json.getStr("content"));
        result.setHighlight(json.getStr("highlight"));
        result.setScore(json.getDouble("score", 0.0));
        result.setGroupId(json.getStr("groupId"));
        result.setArtifactId(json.getStr("artifactId"));
        result.setVersion(json.getStr("version"));
        result.setFileType(json.getStr("fileType"));
        
        // 从 metadata 提取非索引字段
        JSONObject metadata = json.getJSONObject("metadata");
        if (metadata != null) {
            result.setFilePath(metadata.getStr("filePath"));
            result.setFilename(metadata.getStr("filename"));
            result.setSha256(metadata.getStr("sha256"));
        }
        
        return result;
    }

    // ---------- 远程模板下载 ----------

    /**
     * 下载模板（使用 {@link RemoteSearchResult}）
     * <p>直接解压到 groupId/artifactId/version/ 目录
     *
     * @param localRepoPath 本地仓库路径
     * @param remoteBaseUrl 远程基础URL
     * @param result        远程检索结果
     * @param accessKey     Access Key（AK）
     * @param secretKey     Secret Key（SK）
     * @return 是否成功
     */
    public static boolean downloadTemplate(String localRepoPath, String remoteBaseUrl, 
                                           RemoteSearchResult result,
                                           String accessKey, String secretKey) {
        if (result.getGroupId() == null || result.getArtifactId() == null || result.getVersion() == null) {
            return false;
        }
        
        long timestamp = System.currentTimeMillis();
        String nonce = UUID.randomUUID().toString().replace("-", "");
        
        Map<String, String> params = new TreeMap<>();
        params.put("groupId", result.getGroupId());
        params.put("artifactId", result.getArtifactId());
        params.put("version", result.getVersion());
        params.put("timestamp", String.valueOf(timestamp));
        params.put("nonce", nonce);
        params.put("accessKey", accessKey);
        
        String sign = generateSignature(params, secretKey);
        params.put("sign", sign);
        
        Map<String, Object> formParams = new HashMap<>(params);
        File zipFile = null;
        try (HttpResponse response = HttpRequest.get(remoteBaseUrl + "/open-api/template/download")
                .form(formParams)
                .timeout(60000)
                .header("User-Agent", "MCP-CodeStyle-Server/2.0.0")
                .execute()) {
            
            if (!response.isOk()) {
                return false;
            }
            
            zipFile = FileUtil.createTempFile("template-", ".zip", true);
            IoUtil.copy(response.bodyStream(), FileUtil.getOutputStream(zipFile));
            
            String targetPath = localRepoPath + File.separator + 
                               result.getGroupId() + File.separator + 
                               result.getArtifactId() + File.separator +
                               result.getVersion();
            
            ZipUtil.unzip(zipFile, new File(targetPath));
            return true;
        } catch (Exception e) {
            log.error("下载模板失败 [{}/{}/{}]: {}", result.getGroupId(), result.getArtifactId(), result.getVersion(), e.getMessage());
            return false;
        } finally {
            if (zipFile != null) {
                FileUtil.del(zipFile);
            }
        }
    }

    // ---------- 私有辅助方法 ----------

    /**
     * 验证模板文件是否存在
     *
     * @param templateBasePath 模板基础路径
     * @param metaInfo         模板元信息
     * @return 文件是否存在
     */
    private static boolean isTemplateFileExists(String templateBasePath, TemplateMetaInfo metaInfo) {
        try {
            // 规范化文件路径，移除前导分隔符
            String filePath = metaInfo.getFilePath();
            if (filePath.startsWith("/") || filePath.startsWith("\\")) {
                filePath = filePath.substring(1);
            }
            filePath = FileUtil.normalize(filePath);
            
            // 构建完整路径
            Path templatePath = Paths.get(templateBasePath,
                    metaInfo.getGroupId(),
                    metaInfo.getArtifactId(),
                    metaInfo.getVersion(),
                    filePath,
                    metaInfo.getFilename());
            
            return Files.exists(templatePath);
        } catch (Exception e) {
            return false;
        }
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

    // ---------- 模板上传和删除 ----------

    /**
     * 解析模板路径
     * 
     * @param templatePath 格式: groupId/artifactId/version
     * @return [groupId, artifactId, version]
     * @throws IllegalArgumentException 路径格式错误
     */
    public static String[] parseTemplatePath(String templatePath) {
        if (templatePath == null || templatePath.isEmpty()) {
            throw new IllegalArgumentException("模板路径不能为空");
        }
        
        String normalized = FileUtil.normalize(templatePath);
        String[] parts = normalized.split(Pattern.quote(File.separator));
        
        if (parts.length != 3) {
            throw new IllegalArgumentException(
                "模板路径格式错误，应为: groupId/artifactId/version"
            );
        }
        
        return parts;
    }

    /**
     * 验证版本号格式（语义化版本）
     *
     * @param version 版本号
     * @return 是否符合 MAJOR.MINOR.PATCH 格式
     */
    public static boolean isValidSemanticVersion(String version) {
        if (version == null || version.isEmpty()) {
            return false;
        }
        return SEMVER_PATTERN.matcher(version).matches();
    }

    /**
     * 验证模板完整性
     * 
     * @param versionDir 版本目录
     * @throws IOException 验证失败
     */
    public static void validateTemplate(Path versionDir) throws IOException {
        // 1. 验证目录存在
        if (!Files.exists(versionDir) || !Files.isDirectory(versionDir)) {
            throw new IOException("版本目录不存在: " + versionDir);
        }
        
        // 2. 验证 meta.json 存在
        Path metaPath = versionDir.resolve("meta.json");
        if (!Files.exists(metaPath)) {
            throw new IOException("meta.json 不存在");
        }
        
        // 3. 验证 meta.json 格式
        try {
            String content = new String(Files.readAllBytes(metaPath), StandardCharsets.UTF_8);
            TemplateMetaConfig metaConfig = JSONUtil.toBean(content, TemplateMetaConfig.class);
            
            if (metaConfig.getGroupId() == null || metaConfig.getGroupId().isEmpty()) {
                throw new IOException("meta.json 缺少 groupId 字段");
            }
            if (metaConfig.getArtifactId() == null || metaConfig.getArtifactId().isEmpty()) {
                throw new IOException("meta.json 缺少 artifactId 字段");
            }
            if (metaConfig.getVersion() == null || metaConfig.getVersion().isEmpty()) {
                throw new IOException("meta.json 缺少 version 字段");
            }
            if (metaConfig.getFiles() == null || metaConfig.getFiles().isEmpty()) {
                throw new IOException("meta.json 缺少 files 字段");
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("meta.json 格式错误: " + e.getMessage());
        }
        
        // 4. 验证至少有一个 .ftl 文件
        try (Stream<Path> stream = Files.walk(versionDir)) {
            long ftlCount = stream
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".ftl"))
                .count();
            
            if (ftlCount == 0) {
                throw new IOException("未找到 .ftl 模板文件");
            }
        }
    }

    /**
     * 打包模板为 ZIP 文件
     * 
     * @param localRepoPath 本地仓库路径
     * @param groupId 组织 ID
     * @param artifactId 模板 ID
     * @param version 版本号
     * @return ZIP 文件
     * @throws IOException 文件操作异常
     */
    public static File packTemplate(
        String localRepoPath,
        String groupId,
        String artifactId,
        String version
    ) throws IOException {
        // 1. 定位版本目录
        Path versionDir = Paths.get(
            FileUtil.normalize(localRepoPath),
            groupId,
            artifactId,
            version
        );
        
        // 2. 验证模板
        validateTemplate(versionDir);
        
        // 3. 创建临时 ZIP 文件
        File zipFile = FileUtil.createTempFile("template-", ".zip", true);
        
        // 4. 打包整个版本目录（使用 ZipUtil.zip(File, OutputStream)）
        try (FileOutputStream fos = new FileOutputStream(zipFile)) {
            ZipUtil.zip(fos, StandardCharsets.UTF_8, false, null, versionDir.toFile());
        }
        
        return zipFile;
    }

    /**
     * 上传模板到远程服务器
     * 
     * @param remoteBaseUrl 远程服务器地址
     * @param zipFile ZIP 文件
     * @param groupId 组织 ID
     * @param artifactId 模板 ID
     * @param version 版本号
     * @param overwrite 是否覆盖
     * @param accessKey Access Key
     * @param secretKey Secret Key
     * @param timeoutMs 超时时间
     * @return 上传响应
     * @throws RemoteUploadException 上传异常
     */
    public static RemoteUploadResponse uploadTemplateToRemote(
        String remoteBaseUrl,
        File zipFile,
        String groupId,
        String artifactId,
        String version,
        boolean overwrite,
        String accessKey,
        String secretKey,
        int timeoutMs
    ) throws RemoteUploadException {
        try {
            // 1. 构建请求参数
            long timestamp = System.currentTimeMillis();
            String nonce = UUID.randomUUID().toString().replace("-", "");
            
            Map<String, String> params = new TreeMap<>();
            params.put("groupId", groupId);
            params.put("artifactId", artifactId);
            params.put("version", version);
            params.put("overwrite", String.valueOf(overwrite));
            params.put("timestamp", String.valueOf(timestamp));
            params.put("nonce", nonce);
            params.put("accessKey", accessKey);
            
            // 2. 生成签名
            String sign = generateSignature(params, secretKey, "file");
            
            // 3. 发送请求（使用 Map<String, Object> 以支持文件上传）
            Map<String, Object> formParams = new HashMap<>();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                formParams.put(entry.getKey(), entry.getValue());
            }
            formParams.put("sign", sign);
            
            HttpRequest request = HttpRequest.post(remoteBaseUrl + "/open-api/template/upload")
                .form(formParams)
                .form("file", zipFile)
                .timeout(timeoutMs)
                .header("User-Agent", "MCP-CodeStyle-Server/2.1.0");
            
            // 使用 try-with-resources 确保响应资源释放
            try (HttpResponse response = request.execute()) {
                // 4. 处理响应
                if (!response.isOk()) {
                    throw new RemoteUploadException(
                        "上传失败: HTTP " + response.getStatus()
                    );
                }
                
                String body = response.body();
                RemoteUploadResponse uploadResponse = 
                    JSONUtil.toBean(body, RemoteUploadResponse.class);
                
                if (uploadResponse.getSuccess() == null || !uploadResponse.getSuccess()) {
                    throw new RemoteUploadException(
                        "上传失败: " + uploadResponse.getMsg()
                    );
                }
                
                return uploadResponse;
            }
            
        } catch (RemoteUploadException e) {
            throw e;
        } catch (Exception e) {
            throw new RemoteUploadException("上传异常: " + e.getMessage(), e);
        }
    }



    /**
     * 删除远程模板
     * 
     * @param remoteBaseUrl 远程服务器地址
     * @param groupId 组织 ID
     * @param artifactId 模板 ID
     * @param version 版本号
     * @param accessKey Access Key
     * @param secretKey Secret Key
     * @param timeoutMs 超时时间
     * @return 删除响应
     * @throws RemoteDeleteException 删除异常
     */
    public static RemoteDeleteResponse deleteTemplateFromRemote(
        String remoteBaseUrl,
        String groupId,
        String artifactId,
        String version,
        String accessKey,
        String secretKey,
        int timeoutMs
    ) throws RemoteDeleteException {
        try {
            // 1. 构建请求参数
            long timestamp = System.currentTimeMillis();
            String nonce = UUID.randomUUID().toString().replace("-", "");
            
            Map<String, String> params = new TreeMap<>();
            params.put("groupId", groupId);
            params.put("artifactId", artifactId);
            params.put("version", version);
            params.put("timestamp", String.valueOf(timestamp));
            params.put("nonce", nonce);
            params.put("accessKey", accessKey);
            
            // 2. 生成签名
            String sign = generateSignature(params, secretKey);
            params.put("sign", sign);
            
            // 3. 发送请求
            Map<String, Object> formParams = new HashMap<>(params);
            HttpRequest request = HttpRequest.post(remoteBaseUrl + "/open-api/template/delete")
                .form(formParams)
                .timeout(timeoutMs)
                .header("User-Agent", "MCP-CodeStyle-Server/2.1.0");
            
            // 使用 try-with-resources 确保响应资源释放
            try (HttpResponse response = request.execute()) {
                // 4. 处理响应
                if (!response.isOk()) {
                    throw new RemoteDeleteException(
                        "删除失败: HTTP " + response.getStatus()
                    );
                }
                
                String body = response.body();
                RemoteDeleteResponse deleteResponse = 
                    JSONUtil.toBean(body, RemoteDeleteResponse.class);
                
                if (deleteResponse.getSuccess() == null || !deleteResponse.getSuccess()) {
                    throw new RemoteDeleteException(
                        "删除失败: " + deleteResponse.getMsg()
                    );
                }
                
                return deleteResponse;
            }
            
        } catch (RemoteDeleteException e) {
            throw e;
        } catch (Exception e) {
            throw new RemoteDeleteException("删除异常: " + e.getMessage(), e);
        }
    }


}
