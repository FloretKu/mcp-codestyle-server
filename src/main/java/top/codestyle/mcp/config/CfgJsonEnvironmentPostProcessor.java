package top.codestyle.mcp.config;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

import java.io.File;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

public class CfgJsonEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String CFG_JSON = "cfg.json";
    private static final String PROPERTY_SOURCE_NAME = "cfgJsonPropertySource";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        File cfgFile = findCfgJson();
        if (cfgFile == null || !cfgFile.exists()) {
            return;
        }

        try {
            String content = FileUtil.readUtf8String(cfgFile);
            JSONObject json = JSONUtil.parseObj(content);
            Map<String, Object> properties = new HashMap<>();

            JSONObject repository = json.getJSONObject("repository");
            if (repository != null) {
                File baseDir = cfgFile.getParentFile();

                String repositoryDir = firstString(repository, "repository-dir", "repository_dir", "dir");
                if (repositoryDir != null) {
                    properties.put("repository.repository-dir", resolvePath(baseDir, repositoryDir));
                }

                String localPath = firstString(repository, "local-path", "local_path");
                if (localPath != null) {
                    properties.put("repository.local-path", resolvePath(baseDir, localPath));
                }

                String remotePath = firstString(repository, "remote-path", "remote_path", "remote-url", "remote_url");
                if (remotePath != null) {
                    properties.put("repository.remote-path", remotePath);
                }

                Boolean remoteSearchEnabled = firstBoolean(repository, "remote-search-enabled", "remote_search_enabled", "remote_enabled");
                if (remoteSearchEnabled != null) {
                    properties.put("repository.remote-search-enabled", remoteSearchEnabled);
                }

                Integer timeoutMs = firstInteger(repository, "remote-search-timeout-ms", "remote_search_timeout_ms");
                if (timeoutMs != null) {
                    properties.put("repository.remote-search-timeout-ms", timeoutMs);
                }

                String apiKey = firstString(repository, "api-key", "api_key");
                if (apiKey != null && !apiKey.isEmpty()) {
                    properties.put("repository.api-key", apiKey);
                }
            }

            if (!properties.isEmpty()) {
                MutablePropertySources sources = environment.getPropertySources();
                if (sources.contains(PROPERTY_SOURCE_NAME)) {
                    sources.remove(PROPERTY_SOURCE_NAME);
                }
                MapPropertySource propertySource = new MapPropertySource(PROPERTY_SOURCE_NAME, properties);
                if (sources.contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
                    sources.addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, propertySource);
                } else if (sources.contains(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME)) {
                    sources.addAfter(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, propertySource);
                } else {
                    sources.addFirst(propertySource);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("cfg.json 解析失败: " + cfgFile.getAbsolutePath(), e);
        }
    }

    private File findCfgJson() {
        try {
            String jarPath = getClass().getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            File jarFile = new File(jarPath);
            File jarDir = jarFile.isDirectory() ? jarFile : jarFile.getParentFile();
            File cfgFile = new File(jarDir, CFG_JSON);
            if (cfgFile.exists()) {
                return cfgFile;
            }
        } catch (URISyntaxException ignored) {
        }

        return null;
    }

    private String resolvePath(File baseDir, String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        File file = new File(path);
        if (file.isAbsolute()) {
            return path;
        }
        return new File(baseDir, path).getAbsolutePath();
    }

    private String firstString(JSONObject json, String... keys) {
        for (String key : keys) {
            if (json.containsKey(key)) {
                return json.getStr(key);
            }
        }
        return null;
    }

    private Boolean firstBoolean(JSONObject json, String... keys) {
        for (String key : keys) {
            if (json.containsKey(key)) {
                return json.getBool(key);
            }
        }
        return null;
    }

    private Integer firstInteger(JSONObject json, String... keys) {
        for (String key : keys) {
            if (json.containsKey(key)) {
                return json.getInt(key);
            }
        }
        return null;
    }
}
