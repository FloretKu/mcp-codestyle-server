package top.codestyle.mcp.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 沙盒验证服务。
 *
 * <p>核心能力：
 * 1) 命令白名单与 Shell 控制符拦截；
 * 2) 本地模式与 Docker 模式路由（支持自动回退）；
 * 3) 基于 Testcontainers 的容器生命周期管理（不依赖 Docker CLI）；
 * 4) 超时控制与输出截断保护。
 *
 * @since 2.3.0
 */
@Slf4j
@Service
public class SandboxVerificationService {

    /** 沙盒根目录。 */
    private static final String SANDBOX_ROOT = System.getProperty("java.io.tmpdir")
            + File.separator + "mcp-sandbox";

    private static final int DEFAULT_TIMEOUT_SECS = 120;
    private static final int MAX_TIMEOUT_SECS = 600;
    private static final int MAX_OUTPUT_CHARS = 64_000;
    private static final int HELPER_COMMAND_TIMEOUT_SECS = 5;
    private static final String OUTPUT_TRUNCATED_MARK = "\n... [output truncated]";
    private static final Pattern MAVEN_OFFLINE_FLAG_PATTERN = Pattern.compile("(^|\\s)(-o|--offline)(\\s|$)");

    private static final String SYS_MODE = "codestyle.sandbox.mode";
    private static final String ENV_MODE = "CODESTYLE_SANDBOX_MODE";
    private static final String SYS_DOCKER_IMAGE = "codestyle.sandbox.docker.image";
    private static final String ENV_DOCKER_IMAGE = "CODESTYLE_SANDBOX_DOCKER_IMAGE";
    private static final String SYS_DOCKER_WORKDIR = "codestyle.sandbox.docker.workdir";
    private static final String ENV_DOCKER_WORKDIR = "CODESTYLE_SANDBOX_DOCKER_WORKDIR";
    private static final String SYS_DOCKER_MEMORY = "codestyle.sandbox.docker.memory";
    private static final String ENV_DOCKER_MEMORY = "CODESTYLE_SANDBOX_DOCKER_MEMORY";
    private static final String SYS_DOCKER_CPUS = "codestyle.sandbox.docker.cpus";
    private static final String ENV_DOCKER_CPUS = "CODESTYLE_SANDBOX_DOCKER_CPUS";
    private static final String SYS_DOCKER_NETWORK_NONE = "codestyle.sandbox.docker.network.none";
    private static final String ENV_DOCKER_NETWORK_NONE = "CODESTYLE_SANDBOX_DOCKER_NETWORK_NONE";
    private static final String SYS_DOCKER_FALLBACK_LOCAL = "codestyle.sandbox.docker.fallback.local";
    private static final String ENV_DOCKER_FALLBACK_LOCAL = "CODESTYLE_SANDBOX_DOCKER_FALLBACK_LOCAL";
    private static final String SYS_DOCKER_M2_ENABLED = "codestyle.sandbox.docker.mount.m2";
    private static final String ENV_DOCKER_M2_ENABLED = "CODESTYLE_SANDBOX_DOCKER_MOUNT_M2";
    private static final String SYS_DOCKER_M2_HOST_PATH = "codestyle.sandbox.docker.m2.host.path";
    private static final String ENV_DOCKER_M2_HOST_PATH = "CODESTYLE_SANDBOX_DOCKER_M2_HOST_PATH";
    private static final String SYS_DOCKER_M2_CONTAINER_PATH = "codestyle.sandbox.docker.m2.container.path";
    private static final String ENV_DOCKER_M2_CONTAINER_PATH = "CODESTYLE_SANDBOX_DOCKER_M2_CONTAINER_PATH";
    private static final String SYS_DOCKER_MAP_HOST_USER = "codestyle.sandbox.docker.map.host.user";
    private static final String ENV_DOCKER_MAP_HOST_USER = "CODESTYLE_SANDBOX_DOCKER_MAP_HOST_USER";

    private static final String CFG_MODE = "sandbox.mode";
    private static final String CFG_DOCKER_IMAGE = "sandbox.docker.image";
    private static final String CFG_DOCKER_WORKDIR = "sandbox.docker.workdir";
    private static final String CFG_DOCKER_MEMORY = "sandbox.docker.memory";
    private static final String CFG_DOCKER_CPUS = "sandbox.docker.cpus";
    private static final String CFG_DOCKER_NETWORK_NONE = "sandbox.docker.network-none";
    private static final String CFG_DOCKER_FALLBACK_LOCAL = "sandbox.docker.fallback-local";
    private static final String CFG_DOCKER_M2_ENABLED = "sandbox.docker.mount-m2";
    private static final String CFG_DOCKER_M2_HOST_PATH = "sandbox.docker.m2-host-path";
    private static final String CFG_DOCKER_M2_CONTAINER_PATH = "sandbox.docker.m2-container-path";
    private static final String CFG_DOCKER_MAP_HOST_USER = "sandbox.docker.map-host-user";

    /**
     * 命令白名单（按首个命令词匹配）。
     */
    private static final Set<String> ALLOWED_COMMANDS = Set.of(
            "mvn", "mvnw",
            "gradle", "gradlew",
            "npm", "pnpm", "yarn",
            "go",
            "java");

    private enum ExecutionMode {
        LOCAL, DOCKER
    }

    @Autowired(required = false)
    private Environment environment;

    public record ExecutionResult(
            int exitCode,
            String stdout,
            String stderr,
            boolean success) {
    }

    /**
     * 在沙盒中执行命令。
     *
     * TODO(vNext): 当前 Docker 执行为按请求冷启动生命周期
     * （create -> start -> exec -> destroy）。为达到百毫秒级验证响应，
     * 后续将演进为 Daemon Sandbox 温池化模型：预热并保持只读基础容器，
     * 每次请求仅派生 exec 子进程完成编译验证。
     */
    public ExecutionResult executeCommand(String command, String workDir, int timeoutSecs) {
        if (command == null || command.isBlank()) {
            return new ExecutionResult(-1, "", "Command must not be blank", false);
        }
        if (containsShellControlOperators(command)) {
            return new ExecutionResult(-1, "", "Command contains disallowed shell control operators", false);
        }
        if (!isCommandAllowed(command)) {
            return new ExecutionResult(-1, "",
                    "Command not in whitelist. Allowed: " + String.join(", ", ALLOWED_COMMANDS), false);
        }

        final int safeTimeout = timeoutSecs > 0 ? Math.min(timeoutSecs, MAX_TIMEOUT_SECS) : DEFAULT_TIMEOUT_SECS;

        final Path sandboxWorkDir;
        try {
            sandboxWorkDir = resolveSandboxPath(workDir);
            Files.createDirectories(sandboxWorkDir);
        } catch (Exception e) {
            return new ExecutionResult(-1, "", "Failed to initialize sandbox workspace: " + e.getMessage(), false);
        }

        ExecutionMode mode = resolveExecutionMode();
        boolean fallbackLocal = resolveBoolean(
                SYS_DOCKER_FALLBACK_LOCAL,
                ENV_DOCKER_FALLBACK_LOCAL,
                CFG_DOCKER_FALLBACK_LOCAL,
                true);

        if (mode == ExecutionMode.DOCKER) {
            if (!isDockerAvailable()) {
                if (fallbackLocal) {
                    log.warn("Docker is unavailable, fallback to local mode.");
                    return executeLocalCommand(command, sandboxWorkDir, safeTimeout);
                }
                return new ExecutionResult(-1, "", "Docker is unavailable and local fallback is disabled", false);
            }

            try {
                return executeDockerCommand(command, sandboxWorkDir, safeTimeout);
            } catch (DockerInfrastructureException e) {
                if (fallbackLocal) {
                    log.warn("Docker sandbox failed ({}), fallback to local mode.", e.getMessage());
                    return executeLocalCommand(command, sandboxWorkDir, safeTimeout);
                }
                return new ExecutionResult(-1, "", "Docker sandbox failed: " + e.getMessage(), false);
            }
        }

        return executeLocalCommand(command, sandboxWorkDir, safeTimeout);
    }

    /**
     * 向沙盒写入文件。
     */
    public void writeFile(String relativePath, String content) throws IOException {
        Path filePath = resolveSandboxPath(relativePath);
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content, StandardCharsets.UTF_8);
    }

    /**
     * 从沙盒读取文件。
     */
    public String readFile(String relativePath) throws IOException {
        Path filePath = resolveSandboxPath(relativePath);
        return Files.readString(filePath, StandardCharsets.UTF_8);
    }

    /**
     * 递归清理指定沙盒工作区。
     */
    public void cleanWorkspace(String workDir) {
        try {
            Path targetDir = resolveSandboxPath(workDir);
            if (!Files.exists(targetDir)) {
                return;
            }
            Files.walk(targetDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            log.debug("Failed to delete sandbox path {}: {}", path, e.getMessage(), e);
                        }
                    });
        } catch (Exception e) {
            log.warn("Failed to clean sandbox workspace: {}", e.getMessage());
        }
    }

    /**
     * 提取长错误输出的首尾片段，压缩中间冗余内容。
     */
    public String extractErrorSummary(String fullError, int maxLines) {
        if (fullError == null || fullError.isBlank()) {
            return "";
        }

        String[] lines = fullError.split("\n");
        if (maxLines <= 0 || lines.length <= maxLines) {
            return fullError;
        }

        int halfLines = maxLines / 2;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < halfLines; i++) {
            sb.append(lines[i]).append("\n");
        }
        sb.append("... [omitted ").append(lines.length - maxLines).append(" lines] ...\n");
        for (int i = lines.length - halfLines; i < lines.length; i++) {
            sb.append(lines[i]).append("\n");
        }
        return sb.toString().trim();
    }

    public String getSandboxRoot() {
        return SANDBOX_ROOT;
    }

    private ExecutionResult executeLocalCommand(String command, Path sandboxWorkDir, int timeoutSecs) {
        ExecutorService ioExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "sandbox-io-reader");
            t.setDaemon(true);
            return t;
        });

        try {
            ProcessBuilder pb = buildLocalProcess(command);
            pb.directory(sandboxWorkDir.toFile());
            pb.redirectErrorStream(false);

            Process process = pb.start();
            Future<String> stdoutFuture = ioExecutor.submit(() -> readStreamWithLimit(process.getInputStream()));
            Future<String> stderrFuture = ioExecutor.submit(() -> readStreamWithLimit(process.getErrorStream()));

            boolean finished = process.waitFor(timeoutSecs, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                String stdout = getFutureQuietly(stdoutFuture);
                String stderr = appendLine(getFutureQuietly(stderrFuture),
                        "Command execution timed out (" + timeoutSecs + "s)");
                return new ExecutionResult(-1, stdout, stderr, false);
            }

            int exitCode = process.exitValue();
            String stdout = getFutureQuietly(stdoutFuture);
            String stderr = getFutureQuietly(stderrFuture);
            return new ExecutionResult(exitCode, stdout, stderr, exitCode == 0);
        } catch (Exception e) {
            return new ExecutionResult(-1, "", "Command execution error: " + e.getMessage(), false);
        } finally {
            ioExecutor.shutdownNow();
        }
    }

    /**
     * 使用 Testcontainers 在短生命周期容器内执行命令。
     */
    private ExecutionResult executeDockerCommand(String command, Path sandboxWorkDir, int timeoutSecs) {
        DockerSandboxConfig config = resolveDockerConfig();

        try (GenericContainer<?> container = buildDockerContainer(config, sandboxWorkDir)) {
            boolean m2Mounted = attachMavenCacheIfPresent(container, config);
            container.start();
            return executeInContainer(container, command, timeoutSecs, config, m2Mounted);
        } catch (Exception e) {
            throw new DockerInfrastructureException(e.getMessage(), e);
        }
    }

    private GenericContainer<?> buildDockerContainer(DockerSandboxConfig config, Path sandboxWorkDir) {
        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse(config.image()));
        container.withWorkingDirectory(config.workDir());
        container.withFileSystemBind(sandboxWorkDir.toAbsolutePath().toString(), config.workDir(), BindMode.READ_WRITE);
        container.withCommand("sh", "-lc", "while true; do sleep 3600; done");

        container.withCreateContainerCmdModifier(cmd -> {
            HostConfig hostConfig = cmd.getHostConfig() != null ? cmd.getHostConfig() : HostConfig.newHostConfig();

            Long memoryBytes = parseMemoryBytes(config.memoryLimit());
            if (memoryBytes != null && memoryBytes > 0) {
                hostConfig.withMemory(memoryBytes);
            }

            Long nanoCpus = parseNanoCpus(config.cpuLimit());
            if (nanoCpus != null && nanoCpus > 0) {
                hostConfig.withNanoCPUs(nanoCpus);
            }

            if (config.networkNone()) {
                cmd.withNetworkDisabled(true);
                hostConfig.withNetworkMode("none");
            }

            String userMapping = resolveContainerUser(config.mapHostUser());
            if (userMapping != null && !userMapping.isBlank()) {
                cmd.withUser(userMapping);
            }

            cmd.withHostConfig(hostConfig);
        });
        return container;
    }

    private boolean attachMavenCacheIfPresent(GenericContainer<?> container, DockerSandboxConfig config) {
        if (!config.mountM2()) {
            return false;
        }
        Path hostM2 = resolveExistingDirectory(config.m2HostPath());
        if (hostM2 == null) {
            log.info("Host Maven cache path not found, skip mounting: {}", config.m2HostPath());
            return false;
        }
        container.withFileSystemBind(hostM2.toString(), config.m2ContainerPath(), BindMode.READ_ONLY);
        return true;
    }

    private ExecutionResult executeInContainer(
            GenericContainer<?> container,
            String command,
            int timeoutSecs,
            DockerSandboxConfig config,
            boolean m2Mounted) {

        String effectiveCommand = enforceMavenOfflineIfNeeded(command, config);
        DockerClient dockerClient = DockerClientFactory.instance().client();
        List<String> env = buildExecEnv(effectiveCommand, config, m2Mounted);

        LimitedByteArrayOutputStream stdout = new LimitedByteArrayOutputStream(MAX_OUTPUT_CHARS * 4);
        LimitedByteArrayOutputStream stderr = new LimitedByteArrayOutputStream(MAX_OUTPUT_CHARS * 4);
        CapturingResultCallback callback = new CapturingResultCallback(stdout, stderr);

        try {
            ExecCreateCmdResponse execCreate = dockerClient.execCreateCmd(container.getContainerId())
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withCmd("sh", "-lc", effectiveCommand)
                    .withEnv(env)
                    .exec();

            boolean finished = dockerClient.execStartCmd(execCreate.getId())
                    .exec(callback)
                    .awaitCompletion(timeoutSecs, TimeUnit.SECONDS);

            if (!finished) {
                safeKillContainer(container);
                return new ExecutionResult(
                        -1,
                        stdout.asUtf8Text(),
                        appendLine(stderr.asUtf8Text(), "Command execution timed out (" + timeoutSecs + "s)"),
                        false);
            }

            InspectExecResponse inspect = dockerClient.inspectExecCmd(execCreate.getId()).exec();
            int exitCode = inspect != null && inspect.getExitCodeLong() != null
                    ? inspect.getExitCodeLong().intValue()
                    : -1;
            return new ExecutionResult(exitCode, stdout.asUtf8Text(), stderr.asUtf8Text(), exitCode == 0);
        } catch (Exception e) {
            throw new DockerInfrastructureException("Container exec failed: " + e.getMessage(), e);
        } finally {
            try {
                callback.close();
            } catch (IOException e) {
                log.debug("Failed to close docker exec callback: {}", e.getMessage(), e);
            }
        }
    }

    private String enforceMavenOfflineIfNeeded(String command, DockerSandboxConfig config) {
        if (!config.networkNone() || !isMavenCommand(command) || hasMavenOfflineFlag(command)) {
            return command;
        }
        String trimmed = command == null ? "" : command.trim();
        if (trimmed.isEmpty()) {
            return command;
        }
        String offlineCommand = trimmed + " -o";
        log.debug("Force maven offline mode in network-isolated container: {}", offlineCommand);
        return offlineCommand;
    }

    private boolean hasMavenOfflineFlag(String command) {
        if (command == null || command.isBlank()) {
            return false;
        }
        return MAVEN_OFFLINE_FLAG_PATTERN.matcher(command).find();
    }

    /**
     * 仅在 Maven 命令且容器网络隔离时注入本地仓库路径，
     * 避免对所有命令强制设置只读仓库导致副作用。
     */
    private List<String> buildExecEnv(String command, DockerSandboxConfig config, boolean m2Mounted) {
        if (!m2Mounted || !config.networkNone() || !isMavenCommand(command)) {
            return List.of();
        }
        return List.of("MAVEN_OPTS=-Dmaven.repo.local=" + config.m2ContainerPath());
    }

    private ProcessBuilder buildLocalProcess(String command) {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return new ProcessBuilder("cmd.exe", "/c", command);
        }
        return new ProcessBuilder("sh", "-c", command);
    }

    private String readStreamWithLimit(InputStream stream) throws IOException {
        StringBuilder sb = new StringBuilder(Math.min(MAX_OUTPUT_CHARS, 4096));
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[1024];
            int remain = MAX_OUTPUT_CHARS;
            int n;
            while (remain > 0 && (n = reader.read(buffer, 0, Math.min(buffer.length, remain))) != -1) {
                sb.append(buffer, 0, n);
                remain -= n;
            }
            if (remain <= 0) {
                sb.append(OUTPUT_TRUNCATED_MARK);
            }
        }
        return sb.toString();
    }

    private String getFutureQuietly(Future<String> future) {
        if (future == null) {
            return "";
        }
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return "";
        }
    }

    private String appendLine(String base, String extra) {
        if (base == null || base.isBlank()) {
            return extra;
        }
        return base + "\n" + extra;
    }

    /**
     * 统一通过沙盒根目录解析路径，防止路径穿越。
     */
    private Path resolveSandboxPath(String relativePath) {
        String safeRelative = (relativePath == null || relativePath.isBlank()) ? "default" : relativePath.trim();
        Path root = Paths.get(SANDBOX_ROOT).toAbsolutePath().normalize();
        Path resolved = root.resolve(safeRelative).normalize();
        if (!resolved.startsWith(root)) {
            throw new SecurityException("Illegal path traversal: " + relativePath);
        }
        return resolved;
    }

    private boolean isCommandAllowed(String command) {
        String firstToken = extractFirstToken(command);
        String normalized = normalizeCommandToken(firstToken);
        return ALLOWED_COMMANDS.contains(normalized);
    }

    private boolean isMavenCommand(String command) {
        String firstToken = extractFirstToken(command);
        String normalized = normalizeCommandToken(firstToken);
        return "mvn".equals(normalized) || "mvnw".equals(normalized);
    }

    private String extractFirstToken(String command) {
        String trimmed = command == null ? "" : command.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        int idx = trimmed.indexOf(' ');
        return idx > 0 ? trimmed.substring(0, idx) : trimmed;
    }

    private String normalizeCommandToken(String token) {
        String normalized = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replace("\"", "").replace("'", "");
        if (normalized.startsWith("./") || normalized.startsWith(".\\")) {
            normalized = normalized.substring(2);
        }
        int slashIdx = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'));
        if (slashIdx >= 0 && slashIdx < normalized.length() - 1) {
            normalized = normalized.substring(slashIdx + 1);
        }
        if (normalized.endsWith(".cmd")
                || normalized.endsWith(".bat")
                || normalized.endsWith(".sh")
                || normalized.endsWith(".exe")) {
            normalized = normalized.substring(0, normalized.lastIndexOf('.'));
        }
        return normalized;
    }

    /**
     * 拦截明显的 Shell 控制符，避免将该工具退化为通用命令执行入口。
     */
    private boolean containsShellControlOperators(String command) {
        return command.contains("&&")
                || command.contains("||")
                || command.contains(";")
                || command.contains("|")
                || command.contains(">")
                || command.contains("<")
                || command.contains("$(")
                || command.contains("`");
    }

    private ExecutionMode resolveExecutionMode() {
        String mode = resolveString(SYS_MODE, ENV_MODE, CFG_MODE, "local");
        if ("docker".equalsIgnoreCase(mode)) {
            return ExecutionMode.DOCKER;
        }
        return ExecutionMode.LOCAL;
    }

    private DockerSandboxConfig resolveDockerConfig() {
        return new DockerSandboxConfig(
                resolveString(SYS_DOCKER_IMAGE, ENV_DOCKER_IMAGE, CFG_DOCKER_IMAGE, "maven:3.9.9-eclipse-temurin-17"),
                resolveString(SYS_DOCKER_WORKDIR, ENV_DOCKER_WORKDIR, CFG_DOCKER_WORKDIR, "/workspace"),
                resolveString(SYS_DOCKER_MEMORY, ENV_DOCKER_MEMORY, CFG_DOCKER_MEMORY, "1g"),
                resolveString(SYS_DOCKER_CPUS, ENV_DOCKER_CPUS, CFG_DOCKER_CPUS, "1.0"),
                resolveBoolean(SYS_DOCKER_NETWORK_NONE, ENV_DOCKER_NETWORK_NONE, CFG_DOCKER_NETWORK_NONE, false),
                resolveBoolean(SYS_DOCKER_M2_ENABLED, ENV_DOCKER_M2_ENABLED, CFG_DOCKER_M2_ENABLED, true),
                resolveString(
                        SYS_DOCKER_M2_HOST_PATH,
                        ENV_DOCKER_M2_HOST_PATH,
                        CFG_DOCKER_M2_HOST_PATH,
                        Paths.get(System.getProperty("user.home"), ".m2", "repository").toString()),
                resolveString(
                        SYS_DOCKER_M2_CONTAINER_PATH,
                        ENV_DOCKER_M2_CONTAINER_PATH,
                        CFG_DOCKER_M2_CONTAINER_PATH,
                        "/var/maven/repository"),
                resolveBoolean(
                        SYS_DOCKER_MAP_HOST_USER,
                        ENV_DOCKER_MAP_HOST_USER,
                        CFG_DOCKER_MAP_HOST_USER,
                        true));
    }

    private String resolveString(String sysKey, String envKey, String springKey, String defaultValue) {
        String sys = System.getProperty(sysKey);
        if (sys != null && !sys.isBlank()) {
            return sys.trim();
        }
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        if (environment != null) {
            String springValue = environment.getProperty(springKey);
            if (springValue != null && !springValue.isBlank()) {
                return springValue.trim();
            }
        }
        return defaultValue;
    }

    private boolean resolveBoolean(String sysKey, String envKey, String springKey, boolean defaultValue) {
        String sys = System.getProperty(sysKey);
        if (sys != null && !sys.isBlank()) {
            return Boolean.parseBoolean(sys.trim());
        }
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) {
            return Boolean.parseBoolean(env.trim());
        }
        if (environment != null) {
            String springValue = environment.getProperty(springKey);
            if (springValue != null && !springValue.isBlank()) {
                return Boolean.parseBoolean(springValue.trim());
            }
        }
        return defaultValue;
    }

    /**
     * 供单元测试覆盖（可通过子类桩实现稳定控制）。
     */
    protected boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable e) {
            log.debug("Docker availability check failed: {}", e.getMessage());
            return false;
        }
    }

    private Long parseMemoryBytes(String memoryLimit) {
        if (memoryLimit == null || memoryLimit.isBlank()) {
            return null;
        }
        try {
            return DataSize.parse(memoryLimit.trim()).toBytes();
        } catch (Exception e) {
            log.warn("Invalid docker memory limit '{}', ignore it.", memoryLimit);
            return null;
        }
    }

    private Long parseNanoCpus(String cpuLimit) {
        if (cpuLimit == null || cpuLimit.isBlank()) {
            return null;
        }
        try {
            double cpus = Double.parseDouble(cpuLimit.trim());
            if (cpus <= 0) {
                return null;
            }
            return (long) (cpus * 1_000_000_000L);
        } catch (Exception e) {
            log.warn("Invalid docker cpu limit '{}', ignore it.", cpuLimit);
            return null;
        }
    }

    private Path resolveExistingDirectory(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        try {
            String expanded = expandUserHome(rawPath);
            Path path = Paths.get(expanded).toAbsolutePath().normalize();
            return Files.isDirectory(path) ? path : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 在非 Windows 环境解析 UID/GID，并以宿主机当前用户身份运行容器。
     */
    private String resolveContainerUser(boolean enabled) {
        if (!enabled || isWindows()) {
            return null;
        }
        String uid = runSmallCommandAndReadStdout("id", "-u");
        String gid = runSmallCommandAndReadStdout("id", "-g");
        if (uid == null || uid.isBlank() || gid == null || gid.isBlank()) {
            log.warn("Failed to resolve host uid/gid, fallback to image default user.");
            return null;
        }
        return uid + ":" + gid;
    }

    private String runSmallCommandAndReadStdout(String... cmd) {
        Process process = null;
        try {
            process = new ProcessBuilder(cmd).start();
            boolean finished = process.waitFor(HELPER_COMMAND_TIMEOUT_SECS, TimeUnit.SECONDS);
            if (!finished || process.exitValue() != 0) {
                return null;
            }
            try (InputStream in = process.getInputStream()) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
                return text.isBlank() ? null : text;
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win");
    }

    private String expandUserHome(String path) {
        if (path == null) {
            return "";
        }
        if (path.startsWith("~/") || path.startsWith("~\\")) {
            return Paths.get(System.getProperty("user.home"), path.substring(2)).toString();
        }
        if ("~".equals(path.trim())) {
            return System.getProperty("user.home");
        }
        return path;
    }

    private void safeKillContainer(GenericContainer<?> container) {
        String containerId = null;
        try {
            containerId = container.getContainerId();
            if (containerId != null && !containerId.isBlank()) {
                DockerClientFactory.instance().client().killContainerCmd(containerId).exec();
            }
        } catch (Exception e) {
            if (containerId == null || containerId.isBlank()) {
                log.warn("Failed to kill sandbox container: {}", e.getMessage());
                log.debug("Failed to kill sandbox container", e);
            } else {
                log.warn("Failed to kill sandbox container {}: {}", containerId, e.getMessage());
                log.debug("Failed to kill sandbox container {}", containerId, e);
            }
        }
    }

    private record DockerSandboxConfig(
            String image,
            String workDir,
            String memoryLimit,
            String cpuLimit,
            boolean networkNone,
            boolean mountM2,
            String m2HostPath,
            String m2ContainerPath,
            boolean mapHostUser) {
    }

    private static final class DockerInfrastructureException extends RuntimeException {
        private DockerInfrastructureException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class CapturingResultCallback extends ResultCallback.Adapter<Frame> {
        private final LimitedByteArrayOutputStream stdout;
        private final LimitedByteArrayOutputStream stderr;

        private CapturingResultCallback(
                LimitedByteArrayOutputStream stdout,
                LimitedByteArrayOutputStream stderr) {
            this.stdout = stdout;
            this.stderr = stderr;
        }

        @Override
        public void onNext(Frame frame) {
            if (frame == null || frame.getPayload() == null) {
                return;
            }
            byte[] payload = frame.getPayload();
            StreamType streamType = frame.getStreamType();
            if (streamType == StreamType.STDERR) {
                stderr.write(payload, 0, payload.length);
            } else {
                stdout.write(payload, 0, payload.length);
            }
        }
    }

    /**
     * 带硬上限的字节输出流，防止容器日志无限增长。
     */
    private static final class LimitedByteArrayOutputStream extends OutputStream {
        private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
        private final int maxBytes;
        private boolean truncated;

        private LimitedByteArrayOutputStream(int maxBytes) {
            this.maxBytes = Math.max(1, maxBytes);
        }

        @Override
        public synchronized void write(int b) {
            byte[] one = new byte[] { (byte) b };
            write(one, 0, 1);
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            if (b == null || len <= 0) {
                return;
            }
            int remaining = maxBytes - delegate.size();
            if (remaining > 0) {
                int writable = Math.min(remaining, len);
                delegate.write(b, off, writable);
                if (writable < len) {
                    appendTruncatedMark();
                }
            } else {
                appendTruncatedMark();
            }
        }

        private void appendTruncatedMark() {
            if (truncated) {
                return;
            }
            truncated = true;
            byte[] marker = OUTPUT_TRUNCATED_MARK.getBytes(StandardCharsets.UTF_8);
            int remaining = maxBytes - delegate.size();
            if (remaining <= 0) {
                return;
            }
            delegate.write(marker, 0, Math.min(remaining, marker.length));
        }

        private String asUtf8Text() {
            String value = delegate.toString(StandardCharsets.UTF_8);
            if (value.length() <= MAX_OUTPUT_CHARS) {
                return value;
            }
            return value.substring(0, MAX_OUTPUT_CHARS) + OUTPUT_TRUNCATED_MARK;
        }
    }
}
