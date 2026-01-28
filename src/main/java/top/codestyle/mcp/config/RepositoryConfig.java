package top.codestyle.mcp.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import top.codestyle.mcp.util.SDKUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 仓库配置类
 * 管理仓库路径和缓存目录配置
 *
 * @author 小航love666, Kanttha, movclantian chonghaoGao
 * @since 2025-09-29
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "repository")
public class RepositoryConfig {

    /**
     * 本地基础路径，默认使用系统临时目录
     * 可通过以下方式覆盖（优先级从高到低）：
     * 1. JVM参数: -Dcache.base-path=自定义路径
     * 2. JVM参数: -Drepository.local-path=自定义路径
     * 3. 环境变量: REPOSITORY_LOCAL_PATH=自定义路径
     * 4. application.yml 中的 repository.local-path
     */
    private String localPath;

    /**
     * 远程仓库地址
     * 可通过 -Drepository.remote-path=xxx 或 REPOSITORY_REMOTE_PATH 环境变量覆盖
     */
    private String remotePath;

    /**
     * 仓库目录路径
     * 默认在基础路径下创建codestyle-cache目录
     * 可通过 -Drepository.repository-dir=xxx 或 REPOSITORY_REPOSITORY_DIR 环境变量覆盖
     */
    private String repositoryDir;

    /**
     * 是否启用远程检索
     * 默认false,使用本地Lucene检索
     * 可通过 -Drepository.remote-search-enabled=true 或 REPOSITORY_REMOTE_SEARCH_ENABLED 环境变量覆盖
     */
    private boolean remoteSearchEnabled = false;

    /**
     * 远程检索超时时间（毫秒）
     * 可通过 -Drepository.remote-search-timeout-ms=10000 或 REPOSITORY_REMOTE_SEARCH_TIMEOUT_MS 环境变量覆盖
     */
    private int remoteSearchTimeoutMs = 30000;

    /**
     * 远程API Key（可选）
     * 可通过 -Drepository.api-key=xxx 或 REPOSITORY_API_KEY 环境变量覆盖
     */
    private String apiKey;

    /**
     * 获取本地基础路径
     * 优先使用 cache.base-path JVM参数，保持向后兼容
     */
    public String getLocalPath() {
        String cacheBasePath = System.getProperty("cache.base-path");
        if (cacheBasePath != null && !cacheBasePath.isEmpty()) {
            return cacheBasePath;
        }
        return localPath != null ? localPath : System.getProperty("java.io.tmpdir");
    }

    /**
     * 获取仓库目录路径
     */
    public String getRepositoryDir() {
        if (repositoryDir == null || repositoryDir.isEmpty()) {
            return getLocalPath() + File.separator + "codestyle-cache";
        }
        return repositoryDir;
    }

    /**
     * 创建仓库目录Bean
     * 确保仓库目录存在,创建失败时自动降级到系统临时目录
     *
     * @return 仓库目录路径
     */
    @Bean
    public Path repositoryDirectory() {
        try {
            String normalizedRepoDir = SDKUtils.normalizePath(getRepositoryDir());
            Path repoPath = Paths.get(normalizedRepoDir);

            if (!Files.exists(repoPath)) {
                Files.createDirectories(repoPath);
            }
            return repoPath;
        } catch (Exception e) {
            String fallbackTempDir = System.getProperty("java.io.tmpdir") + File.separator + "codestyle-cache";
            Path fallbackPath = Paths.get(fallbackTempDir);
            try {
                if (!Files.exists(fallbackPath)) {
                    Files.createDirectories(fallbackPath);
                }
                return fallbackPath;
            } catch (Exception ex) {
                throw new RuntimeException("无法创建仓库目录", ex);
            }
        }
    }

}
