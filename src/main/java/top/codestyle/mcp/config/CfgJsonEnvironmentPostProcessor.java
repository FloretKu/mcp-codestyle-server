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
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class CfgJsonEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String CFG_JSON = "cfg.json";
    private static final String PROJECT_CFG_DIR = ".codestyle";
    private static final String PROPERTY_SOURCE_NAME = "cfgJsonPropertySource";
    private static final String SYSTEM_ENVIRONMENT = "systemEnvironment";
    private static final String SYSTEM_PROPERTIES = "systemProperties";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> properties = new HashMap<>();

        // 优先级从低到高：classpath(jar内) < jar同目录 < 项目根目录
        // 后加载的会覆盖前面的，实现优先级从低到高
        
        // 1. 从 classpath 读取（IDE 和 jar 打包都支持，优先级最低）
        loadConfigFromClasspath(properties);

        // 2. jar 包同目录
        File globalCfg = findGlobalCfgJson();
        if (globalCfg != null && globalCfg.exists()) {
            loadConfig(globalCfg, properties);
        }

        // 3. 项目根目录 .codestyle/（优先级最高）
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
            // 让环境变量/系统属性优先于 cfg.json（符合文档优先级：env > cfg.json > application.yml）
            if (sources.contains(SYSTEM_ENVIRONMENT)) {
                sources.addAfter(SYSTEM_ENVIRONMENT, propertySource);
            } else if (sources.contains(SYSTEM_PROPERTIES)) {
                sources.addAfter(SYSTEM_PROPERTIES, propertySource);
            } else {
                sources.addLast(propertySource);
            }
        }
    }

    private void loadConfigFromClasspath(Map<String, Object> properties) {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(CFG_JSON)) {
            if (inputStream == null) {
                return;
            }
            String content = new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            parseConfigJson(properties, content, null);
        } catch (Exception e) {
            throw new RuntimeException("从 classpath 加载 cfg.json 失败", e);
        }
    }

    private void loadConfig(File cfgFile, Map<String, Object> properties) {
        try {
            String content = FileUtil.readUtf8String(cfgFile);
            parseConfigJson(properties, content, cfgFile.getParentFile());
        } catch (Exception e) {
            throw new RuntimeException("cfg.json 解析失败: " + cfgFile.getAbsolutePath(), e);
        }
    }

    private void parseConfigJson(Map<String, Object> properties, String content, File baseDir) {
        JSONObject json = JSONUtil.parseObj(content);

        JSONObject repository = json.getJSONObject("repository");
        if (repository != null) {
            // local-path
            String localPath = repository.getStr("local-path");
            if (localPath != null) {
                String resolvedPath = resolvePath(baseDir, localPath);
                properties.put("repository.local-path", resolvedPath);
            }
            // remote 配置
            JSONObject remote = repository.getJSONObject("remote");
            if (remote != null) {
                Boolean enabled = remote.getBool("enabled");
                if (enabled != null) {
                    properties.put("repository.remote.enabled", enabled);
                }
                // base-url
                String baseUrl = remote.getStr("base-url");
                if (baseUrl != null) {
                    properties.put("repository.remote.base-url", baseUrl);
                }
                // access-key
                String accessKey = remote.getStr("access-key");
                if (accessKey != null) {
                    properties.put("repository.remote.access-key", accessKey);
                }
                // secret-key
                String secretKey = remote.getStr("secret-key");
                if (secretKey != null) {
                    properties.put("repository.remote.secret-key", secretKey);
                }
                // timeout-ms
                Integer timeoutMs = remote.getInt("timeout-ms");
                if (timeoutMs != null) {
                    properties.put("repository.remote.timeout-ms", timeoutMs);
                }
            }
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
        File effectiveBaseDir = (baseDir != null) ? baseDir : new File(System.getProperty("user.dir"));

        // 展开 "~"（兼容 Windows：若不展开，"~/.xxx" 可能会被当成字面目录名 "~"）
        String trimmed = path.trim();
        if ("~".equals(trimmed)) {
            path = System.getProperty("user.home");
        } else if (trimmed.startsWith("~/") || trimmed.startsWith("~\\")) {
            path = new File(System.getProperty("user.home"), trimmed.substring(2)).getAbsolutePath();
        } else if (trimmed.startsWith("~")) {
            // 容错：如 "~abc" 这类非法写法，按字面处理，不做展开
            path = trimmed;
        }

        File file = new File(path);
        if (file.isAbsolute()) {
            return file.getAbsolutePath();
        }
        return new File(effectiveBaseDir, path).getAbsolutePath();
    }
}
