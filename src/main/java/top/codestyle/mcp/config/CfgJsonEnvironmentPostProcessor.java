package top.codestyle.mcp.config;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class CfgJsonEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String CFG_JSON = "cfg.json";
    private static final String PROJECT_CFG_DIR = ".codestyle";
    private static final String PROPERTY_SOURCE_NAME = "cfgJsonPropertySource";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> properties = new HashMap<>();

        File globalCfg = findGlobalCfgJson();
        if (globalCfg != null && globalCfg.exists()) {
            loadConfig(globalCfg, properties);
        }

        File projectCfg = findProjectCfgJson();
        if (projectCfg != null && projectCfg.exists()) {
            loadConfig(projectCfg, properties);
        }

        if (!properties.isEmpty()) {
            MutablePropertySources sources = environment.getPropertySources();
            if (sources.contains(PROPERTY_SOURCE_NAME)) {
                sources.remove(PROPERTY_SOURCE_NAME);
            }
            MapPropertySource propertySource = new MapPropertySource(PROPERTY_SOURCE_NAME, properties);
            sources.addFirst(propertySource);
        }
    }

    private void loadConfig(File cfgFile, Map<String, Object> properties) {
        try {
            String content = FileUtil.readUtf8String(cfgFile);
            JSONObject json = JSONUtil.parseObj(content);

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
        } catch (Exception e) {
            throw new RuntimeException("cfg.json 解析失败: " + cfgFile.getAbsolutePath(), e);
        }
    }

    private File findGlobalCfgJson() {
        String classpath = System.getProperty("java.class.path");
        if (classpath != null && classpath.endsWith(".jar")) {
            File jarFile = new File(classpath);
            if (jarFile.exists()) {
                File cfgFile = new File(jarFile.getParentFile(), CFG_JSON);
                if (cfgFile.exists()) {
                    return cfgFile;
                }
            }
        }
        return null;
    }

    private File findProjectCfgJson() {
        String userDir = System.getProperty("user.dir");
        if (userDir != null) {
            File projectCfg = new File(userDir, PROJECT_CFG_DIR + File.separator + CFG_JSON);
            if (projectCfg.exists()) {
                return projectCfg;
            }
        }
        return null;
    }

    private String resolvePath(File baseDir, String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        if (path.startsWith("~")) {
            String userHome = System.getProperty("user.home");
            path = new File(userHome, path.substring(2)).getAbsolutePath();
        }
        File file = new File(path);
        if (file.isAbsolute()) {
            return file.getAbsolutePath();
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
