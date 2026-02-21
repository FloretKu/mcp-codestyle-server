package top.codestyle.mcp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import top.codestyle.mcp.util.CodestyleClient;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 仓库配置类
 * 管理仓库路径和远程检索配置
 *
 * @author CodeStyle Team
 * @since 2.0.0
 */
@Data
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
         */
        private boolean enabled = false;

        /**
         * 远程仓库基础 URL
         */
        private String baseUrl;

        /**
         * Access Key（AK）
         */
        private String accessKey;

        /**
         * Secret Key（SK）
         */
        private String secretKey;

        /**
         * 超时时间（毫秒）
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
        try {
            String normalizedRepoDir = CodestyleClient.normalizePath(getRepositoryDir());
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
