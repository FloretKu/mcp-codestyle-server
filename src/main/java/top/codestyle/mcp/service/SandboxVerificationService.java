package top.codestyle.mcp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

/**
 * 沙盒验证服务
 * <p>
 * 提供隔离的命令执行环境，用于验证大模型生成的模板骨架是否可以通过编译。
 *
 * <p>
 * 借鉴 DeerFlow (deer-flow/backend/src/sandbox/sandbox.py) 的 Sandbox 抽象：
 * execute_command, write_file, read_file 三大核心操作。
 * 当前实现使用 ProcessBuilder 在临时目录中执行，
 * 后续可扩展为 Docker 容器化执行。
 *
 * @since 2.1.0
 */
@Slf4j
@Service
public class SandboxVerificationService {

    /** 沙盒工作区根目录 */
    private static final String SANDBOX_ROOT = System.getProperty("java.io.tmpdir")
            + File.separator + "mcp-sandbox";

    /**
     * 命令执行结果
     */
    public record ExecutionResult(
            int exitCode,
            String stdout,
            String stderr,
            boolean success) {
    }

    /**
     * 在沙盒中执行命令
     * <p>
     * 参考 deer-flow sandbox.py 的 execute_command 设计。
     *
     * @param command     要执行的命令
     * @param workDir     工作目录（相对于沙盒根路径）
     * @param timeoutSecs 超时时间（秒）
     * @return 执行结果
     */
    public ExecutionResult executeCommand(String command, String workDir, int timeoutSecs) {
        Path sandboxWorkDir = Paths.get(SANDBOX_ROOT, workDir);
        try {
            Files.createDirectories(sandboxWorkDir);
        } catch (IOException e) {
            return new ExecutionResult(-1, "", "创建沙盒工作目录失败: " + e.getMessage(), false);
        }

        try {
            ProcessBuilder pb;
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd.exe", "/c", command);
            } else {
                pb = new ProcessBuilder("sh", "-c", command);
            }
            pb.directory(sandboxWorkDir.toFile());
            pb.redirectErrorStream(false);

            Process process = pb.start();

            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);

            boolean finished = process.waitFor(timeoutSecs, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ExecutionResult(-1, stdout, "命令执行超时 (" + timeoutSecs + "s)", false);
            }

            int exitCode = process.exitValue();
            return new ExecutionResult(exitCode, stdout, stderr, exitCode == 0);
        } catch (Exception e) {
            return new ExecutionResult(-1, "", "命令执行异常: " + e.getMessage(), false);
        }
    }

    /**
     * 在沙盒中写入文件
     * <p>
     * 参考 deer-flow sandbox.py 的 write_file 设计。
     *
     * @param relativePath 相对于沙盒根路径的文件路径
     * @param content      文件内容
     * @throws IOException 文件写入异常
     */
    public void writeFile(String relativePath, String content) throws IOException {
        Path filePath = Paths.get(SANDBOX_ROOT, relativePath).toAbsolutePath().normalize();

        // 安全校验：不允许路径穿越
        if (!filePath.startsWith(Paths.get(SANDBOX_ROOT).toAbsolutePath().normalize())) {
            throw new SecurityException("非法路径穿越: " + relativePath);
        }

        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content, StandardCharsets.UTF_8);
    }

    /**
     * 从沙盒中读取文件
     * <p>
     * 参考 deer-flow sandbox.py 的 read_file 设计。
     *
     * @param relativePath 相对于沙盒根路径的文件路径
     * @return 文件内容
     * @throws IOException 文件读取异常
     */
    public String readFile(String relativePath) throws IOException {
        Path filePath = Paths.get(SANDBOX_ROOT, relativePath).toAbsolutePath().normalize();

        if (!filePath.startsWith(Paths.get(SANDBOX_ROOT).toAbsolutePath().normalize())) {
            throw new SecurityException("非法路径穿越: " + relativePath);
        }

        return Files.readString(filePath, StandardCharsets.UTF_8);
    }

    /**
     * 清理沙盒工作区
     *
     * @param workDir 工作目录名
     */
    public void cleanWorkspace(String workDir) {
        Path targetDir = Paths.get(SANDBOX_ROOT, workDir);
        if (Files.exists(targetDir)) {
            try {
                Files.walk(targetDir)
                        .sorted((a, b) -> b.compareTo(a)) // 深度优先删除
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException ignored) {
                            }
                        });
            } catch (IOException e) {
                log.warn("清理沙盒工作区失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 提取编译错误的关键摘要（截断中间冗余栈信息）
     * <p>
     * 参考 RepoMaster 论文中 Information Selection 机制。
     *
     * @param fullError 完整的错误输出
     * @param maxLines  最多保留的行数
     * @return 精简后的错误摘要
     */
    public String extractErrorSummary(String fullError, int maxLines) {
        if (fullError == null || fullError.isBlank())
            return "";

        String[] lines = fullError.split("\n");
        if (lines.length <= maxLines)
            return fullError;

        // 保留前半和后半
        int halfLines = maxLines / 2;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < halfLines; i++) {
            sb.append(lines[i]).append("\n");
        }
        sb.append("... [省略 ").append(lines.length - maxLines).append(" 行] ...\n");
        for (int i = lines.length - halfLines; i < lines.length; i++) {
            sb.append(lines[i]).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * 获取沙盒根目录
     */
    public String getSandboxRoot() {
        return SANDBOX_ROOT;
    }
}
