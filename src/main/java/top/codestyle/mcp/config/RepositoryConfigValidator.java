package top.codestyle.mcp.config;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 仓库配置验证器
 * 在应用启动时验证配置的有效性
 *
 * @author CodeStyle Team
 * @since 2.0.0
 */
@Component
@RequiredArgsConstructor
public class RepositoryConfigValidator implements ApplicationRunner {

    private final RepositoryConfig config;

    @Override
    public void run(ApplicationArguments args) {
        validateLocalPath();
        
        if (config.getRemote().isEnabled()) {
            validateRemoteConfig();
        }
    }

    private void validateLocalPath() {
        String localPath = config.getLocalPath();
        if (StrUtil.isBlank(localPath)) {
            throw new IllegalStateException(
                "本地缓存路径未配置\n" +
                "请设置 repository.local-path 或环境变量 CODESTYLE_CACHE_PATH"
            );
        }
    }

    private void validateRemoteConfig() {
        RepositoryConfig.RemoteConfig remote = config.getRemote();
        String baseUrl = remote.getBaseUrl();
        String accessKey = remote.getAccessKey();
        String secretKey = remote.getSecretKey();

        if (StrUtil.isBlank(baseUrl)) {
            throw new IllegalStateException(
                "远程检索已启用，但未配置 base-url\n" +
                "请在 application.yml 中设置 repository.remote.base-url"
            );
        }

        if (StrUtil.isBlank(accessKey)) {
            throw new IllegalStateException(
                "远程检索已启用，但未配置 access-key\n" +
                "请在 CodeStyle 管理后台创建 Open API 应用获取 Access Key\n" +
                "然后在 application.yml 中设置 repository.remote.access-key"
            );
        }

        if (StrUtil.isBlank(secretKey)) {
            throw new IllegalStateException(
                "远程检索已启用，但未配置 secret-key\n" +
                "请在 CodeStyle 管理后台创建 Open API 应用获取 Secret Key\n" +
                "然后在 application.yml 中设置 repository.remote.secret-key"
            );
        }

        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            throw new IllegalStateException(
                "base-url 格式错误，必须以 http:// 或 https:// 开头\n" +
                "当前值: " + baseUrl
            );
        }
    }
}

