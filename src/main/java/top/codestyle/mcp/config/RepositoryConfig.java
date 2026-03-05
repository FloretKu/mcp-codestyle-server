package top.codestyle.mcp.config;

import cn.hutool.core.io.FileUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 仓库配置类
 * <p>管理本地仓库路径和远程检索配置。
 *
 * <p>配置优先级（从高到低）：
 * <ol>
 *   <li>环境变量: {@code CODESTYLE_CACHE_PATH}, {@code CODESTYLE_REMOTE_ENABLED}</li>
 *   <li>cfg.json: 所有配置字段</li>
 *   <li>application.yml: 默认值</li>
 * </ol>
 *
 * @author CodeStyle Team
 * @since 2.0.0
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "repository")
public class RepositoryConfig {

    /**
     * 本地基础路径
     * 优先级: 环境变量 CODESTYLE_CACHE_PATH > cfg.json > application.yml
     */
    private String localPath;

    /**
     * 远程仓库配置
     */
    private RemoteConfig remote = new RemoteConfig();

    /**
     * 远程仓库配置类
     */
    @Data
    public static class RemoteConfig {
        /**
         * 是否启用远程检索
         * 优先级: 环境变量 CODESTYLE_REMOTE_ENABLED > cfg.json > application.yml
         */
        private boolean enabled = false;

        /**
         * 远程仓库基础 URL
         * 优先级: cfg.json > application.yml
         */
        private String baseUrl;

        /**
         * Access Key（AK）
         * 优先级: cfg.json > application.yml
         */
        private String accessKey;

        /**
         * Secret Key（SK）
         * 优先级: cfg.json > application.yml
         */
        private String secretKey;

        /**
         * 超时时间（毫秒）
         * 优先级: cfg.json > application.yml
         */
        private int timeoutMs = 10000;
    }

    /**
     * 获取仓库目录路径
     * 
     * @return 仓库目录完整路径
     */
    public String getRepositoryDir() {
        String basePath = localPath != null ? localPath : System.getProperty("java.io.tmpdir");
        return basePath + File.separator + "codestyle-cache";
    }

    /**
     * 创建仓库目录Bean
     * 确保仓库目录存在
     *
     * @return 仓库目录路径
     */
    @Bean
    public Path repositoryDirectory() {
        log.info("=== 实际生效的配置 ===");
        log.info("localPath: {}", localPath);
        log.info("remote.enabled: {}", remote.enabled);
        log.info("remote.baseUrl: {}", remote.baseUrl);
        log.info("remote.accessKey: {}", remote.accessKey != null ? "已配置(" + remote.accessKey + ")" : "未配置");
        log.info("remote.secretKey: {}", remote.secretKey != null ? "已配置" : "未配置");
        log.info("remote.timeoutMs: {}", remote.timeoutMs);
        log.info("=====================");
        
        try {
            String normalizedRepoDir = FileUtil.normalize(getRepositoryDir());
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
