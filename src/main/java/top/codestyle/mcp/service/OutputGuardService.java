package top.codestyle.mcp.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * MCP 输出守卫：
 * - 控制单次 MCP tool 返回的文本长度，避免 Cursor/聊天窗口截断
 * - 超过阈值时，将完整内容写入被分析项目目录下的 .codestyle/output/
 * - MCP 返回：可读摘要 + 输出文件绝对路径
 */
@Service
public class OutputGuardService {

    /**
     * P2: 下调以配合骨架压缩，减少单次响应体积与后续轮次重读。
     */
    public static final int DEFAULT_MAX_INLINE_CHARS = 6_000;

    private static final int DEFAULT_SUMMARY_HEAD_CHARS = 7_000;
    private static final int DEFAULT_SUMMARY_TAIL_CHARS = 1_200;

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    public String guard(String content, String projectPath, String label) {
        return guard(content, projectPath, label, DEFAULT_MAX_INLINE_CHARS);
    }

    public String guard(String content, String projectPath, String label, int maxInlineChars) {
        if (content == null) return "";
        int limit = maxInlineChars > 0 ? maxInlineChars : DEFAULT_MAX_INLINE_CHARS;
        if (content.length() <= limit) return content;

        String safeLabel = sanitizeLabel(label);
        Path outFile = null;
        Exception writeError = null;

        if (projectPath != null && !projectPath.isBlank()) {
            try {
                Path root = Paths.get(projectPath).toAbsolutePath().normalize();
                Path outDir = root.resolve(Paths.get(".codestyle", "output"));
                Files.createDirectories(outDir);
                String fileName = safeLabel + "-" + TS.format(LocalDateTime.now()) + "-" + UUID.randomUUID()
                        .toString().substring(0, 8) + ".md";
                outFile = outDir.resolve(fileName);
                Files.writeString(outFile, content, StandardCharsets.UTF_8);
            } catch (Exception e) {
                writeError = e;
                outFile = null;
            }
        }

        String summary = buildSummary(content, limit);
        int lines = countLines(content);

        StringBuilder out = new StringBuilder();
        out.append(summary);
        out.append("\n\n---\n");
        out.append("⚠ 输出过长已被服务端裁剪（约 ").append(lines).append(" 行）。\n");

        if (outFile != null) {
            out.append("完整内容已写入: ").append(outFile.toAbsolutePath()).append("\n");
            out.append("建议：用编辑器直接打开该文件，或再次调用工具并使用更精确的 query/lineRange。");
        } else {
            out.append("未能写入项目目录的 .codestyle/output/（");
            out.append(writeError != null ? writeError.getClass().getSimpleName() + ": " + safeMsg(writeError.getMessage()) : "未知原因");
            out.append("）。\n");
            out.append("建议：降低 detailLevel / 缩小 focusPath / 使用 exploreCodeContext 的 search/trace/expand+lineRange。");
        }

        return out.toString();
    }

    private static String buildSummary(String content, int limit) {
        int head = Math.min(DEFAULT_SUMMARY_HEAD_CHARS, Math.max(1, limit / 2));
        int tail = Math.min(DEFAULT_SUMMARY_TAIL_CHARS, Math.max(1, limit / 8));

        if (content.length() <= head + tail + 64) {
            return content.substring(0, Math.min(content.length(), limit));
        }

        String headPart = content.substring(0, Math.min(head, content.length()));
        String tailPart = content.substring(Math.max(0, content.length() - tail));
        return headPart
                + "\n\n...（中间内容省略，使用文件查看全文）...\n\n"
                + tailPart;
    }

    private static int countLines(String s) {
        if (s == null || s.isEmpty()) return 0;
        int lines = 1;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') lines++;
        }
        return lines;
    }

    private static String sanitizeLabel(String label) {
        String l = (label == null || label.isBlank()) ? "mcp-output" : label.strip();
        l = l.replaceAll("[^a-zA-Z0-9._-]+", "-");
        if (l.length() > 48) l = l.substring(0, 48);
        if (l.isBlank()) l = "mcp-output";
        return l;
    }

    private static String safeMsg(String msg) {
        if (msg == null) return "";
        return msg.replace('\n', ' ').replace('\r', ' ').strip();
    }
}

