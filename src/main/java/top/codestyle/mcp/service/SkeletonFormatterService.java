package top.codestyle.mcp.service;

import org.springframework.stereotype.Service;
import top.codestyle.mcp.model.ast.AstNode;
import top.codestyle.mcp.model.ast.ProjectSkeleton;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Markdown + XML 混合输出格式化器
 * <p>
 * 将 ProjectSkeleton 格式化为「Markdown 外框 + XML 标签定界」的高效格式，
 * 支持 detailLevel 1–4 控制输出粒度。
 *
 * @since 2.1.0
 */
@Service
public class SkeletonFormatterService {

    /** P2: 骨架极致压缩，目标 ~4K chars，减少后续轮次重读消耗 */
    public static final int DEFAULT_MAX_OUTPUT_CHARS = 5_000;

    /**
     * 格式化为 Markdown+XML 混合文档。
     *
     * @param skeleton    剪枝后的骨架
     * @param detailLevel 1=目录概览, 2=类骨架, 3=方法签名+docstring, 4=完整
     * @param topFilePaths 可选，得分 Top 文件路径列表，用于标注 ★
     * @param maxChars    最大输出字符数（建议 ~16k），超过则提前截断并提示用 exploreCodeContext 深入
     * @return 格式化后的字符串
     */
    public String format(ProjectSkeleton skeleton, int detailLevel,
                         List<String> topFilePaths, int maxChars) {
        int budget = maxChars > 0 ? maxChars : DEFAULT_MAX_OUTPUT_CHARS;
        StringBuilder out = new StringBuilder();
        String projectName = skeleton.getProjectPath();
        if (projectName.contains("/") || projectName.contains("\\")) {
            projectName = projectName.substring(Math.max(projectName.lastIndexOf('/'), projectName.lastIndexOf('\\')) + 1);
        }

        out.append("# Project Skeleton: ").append(escapeMd(projectName)).append("\n\n");
        out.append("## Summary\n");
        out.append("- Files: ").append(skeleton.getTotalFiles());
        out.append(" | Classes: ").append(skeleton.getTotalClasses());
        out.append(" | Interfaces/Enums: included");
        out.append(" | Methods: ").append(skeleton.getTotalMethods()).append("\n");
        out.append("- Compression: Level ").append(detailLevel);
        out.append(" (").append(levelLabel(detailLevel)).append(")\n\n");

        Set<String> topSet = topFilePaths != null ? new HashSet<>(topFilePaths) : Set.of();

        out.append("## Directory Structure\n");
        out.append("<directory_tree>\n");
        appendDirectoryTree(out, skeleton, topSet);
        out.append("</directory_tree>\n\n");

        out.append("## Code Skeleton\n");
        List<AstNode> files = skeleton.getFileNodes() != null
                ? skeleton.getFileNodes().stream()
                .sorted((a, b) -> {
                    String pa = a != null ? a.getFilePath() : "";
                    String pb = b != null ? b.getFilePath() : "";
                    int sa = filePriorityScore(pa, topSet);
                    int sb = filePriorityScore(pb, topSet);
                    if (sa != sb) return Integer.compare(sb, sa);
                    return pa.compareTo(pb);
                })
                .collect(Collectors.toList())
                : List.of();
        int shownFiles = 0;
        boolean truncated = false;
        for (AstNode fileNode : files) {
            if (out.length() >= budget) {
                truncated = true;
                break;
            }
            String path = fileNode.getFilePath();
            String lang = langFromPath(path);
            boolean star = topSet.contains(path);
            out.append("<ast_skeleton lang=\"").append(lang).append("\" file=\"")
                    .append(escapeXml(path)).append("\"");
            if (star) out.append(" importance=\"high\"");
            out.append(">\n");
            int perFileLineBudget;
            if (detailLevel >= 4) {
                perFileLineBudget = star ? 60 : 30;
            } else if (detailLevel == 3) {
                perFileLineBudget = star ? 25 : 15;
            } else {
                perFileLineBudget = star ? 12 : 8;
            }
            appendAstSkeleton(out, fileNode, detailLevel, perFileLineBudget);
            out.append("</ast_skeleton>\n\n");
            shownFiles++;
        }

        if (shownFiles < files.size()) {
            truncated = true;
        }
        if (truncated) {
            int omitted = Math.max(0, files.size() - shownFiles);
            out.append("> ... 由于输出预算限制，已省略 ").append(omitted)
                    .append(" 个文件的骨架。请使用 `exploreCodeContext(search/expand/trace)` 精准展开。\n\n");
        }

        out.append("## Dependency Graph\n");
        out.append("<dependency_graph format=\"edge_list\">\n");
        appendDependencyGraph(out, skeleton, budget, topSet);
        out.append("</dependency_graph>\n");

        out.append("\n---\n[后续操作]\n");
        out.append("- search(\"关键词\") 搜索类/方法/代码\n");
        out.append("- expand(\"类名\") 或 expand(\"文件路径\") 查看代码，大文件用 lineRange=\"起-止\"\n");
        out.append("- trace(\"类名\") 追踪上下游依赖\n");

        return out.toString();
    }

    public String format(ProjectSkeleton skeleton, int detailLevel, List<String> topFilePaths) {
        return format(skeleton, detailLevel, topFilePaths, DEFAULT_MAX_OUTPUT_CHARS);
    }

    public String format(ProjectSkeleton skeleton, int detailLevel) {
        return format(skeleton, detailLevel, null, DEFAULT_MAX_OUTPUT_CHARS);
    }

    private String levelLabel(int level) {
        return switch (level) {
            case 1 -> "目录概览";
            case 2 -> "类骨架";
            case 3 -> "方法签名+docstring";
            case 4 -> "完整";
            default -> "Level " + level;
        };
    }

    private void appendDirectoryTree(StringBuilder out, ProjectSkeleton skeleton, Set<String> topSet) {
        List<AstNode> files = skeleton.getFileNodes() != null ? skeleton.getFileNodes() : List.of();
        Map<String, List<AstNode>> byDir = new TreeMap<>();
        for (AstNode f : files) {
            String p = f.getFilePath();
            String dir = p.contains("/") ? p.substring(0, p.lastIndexOf('/')) : "";
            byDir.computeIfAbsent(dir, k -> new ArrayList<>()).add(f);
        }
        for (Map.Entry<String, List<AstNode>> e : byDir.entrySet()) {
            String dir = e.getKey();
            if (!dir.isEmpty()) {
                out.append(dir).append("/\n");
            }
            for (AstNode f : e.getValue()) {
                String name = f.getFilePath().contains("/") ? f.getFilePath().substring(f.getFilePath().lastIndexOf('/') + 1) : f.getFilePath();
                out.append("  ").append(name);
                if (topSet.contains(f.getFilePath())) out.append(" ★");
                out.append("\n");
            }
        }
    }

    private void appendAstSkeleton(StringBuilder out, AstNode fileNode, int detailLevel, int perFileLineBudget) {
        if (fileNode.getChildren() == null) return;

        List<String> lines = new ArrayList<>();
        int classes = 0;
        int methods = 0;
        int fields = 0;
        int constants = 0;
        int omittedMembers = 0;

        List<String> dataClasses = new ArrayList<>();

        // 1) imports/package（尽量压缩为少量行）
        if (detailLevel >= 2) {
            for (AstNode c : fileNode.getChildren()) {
                String nt = c.getNodeType();
                if (!"import".equals(nt) && !"import_summary".equals(nt) && !"package".equals(nt)) continue;
                String name = c.getName();
                if (name == null || name.isBlank()) continue;
                for (String l : name.split("\n")) {
                    if (lines.size() >= Math.max(2, perFileLineBudget / 5)) break;
                    lines.add(l.trim());
                }
            }
        }

        // 2) 结构性节点（类/接口/枚举/顶层方法）
        for (AstNode child : fileNode.getChildren()) {
            if (lines.size() >= perFileLineBudget) break;

            String nt = child.getNodeType();
            if ("import".equals(nt) || "import_summary".equals(nt) || "package".equals(nt)) continue;

            if ("class".equals(nt) || "interface".equals(nt) || "enum".equals(nt)) {
                classes++;

                boolean isEmptyType = isEffectivelyEmptyType(child);
                if (isEmptyType) {
                    dataClasses.add(child.getName() != null ? child.getName() : "(anonymous)");
                    continue;
                }

                if (detailLevel >= 3 && child.getAnnotations() != null && !child.getAnnotations().isEmpty()) {
                    for (String a : child.getAnnotations()) {
                        if (lines.size() >= perFileLineBudget) break;
                        if (a == null || a.isBlank()) continue;
                        lines.add(a.trim());
                    }
                }

                lines.add(compactSignature(child.getSignature(), nt + " " + child.getName()));
                if (detailLevel >= 3) {
                    String doc = compactDoc(child.getDocstring());
                    if (doc != null) lines.add("# Doc: " + doc);
                }

                if (child.getChildren() != null) {
                    int maxMembers = detailLevel >= 4 ? 60 : (detailLevel == 3 ? 18 : 12);
                    int written = 0;
                    for (AstNode m : child.getChildren()) {
                        if (lines.size() >= perFileLineBudget) break;
                        if (written >= maxMembers) {
                            omittedMembers += Math.max(0, child.getChildren().size() - written);
                            break;
                        }

                        String mType = m.getNodeType();
                        if ("method".equals(mType)) {
                            methods++;
                            String sig = compactSignature(m.getSignature(), m.getName());
                            if (detailLevel >= 3) {
                                String mdoc = compactDoc(m.getDocstring());
                                if (mdoc != null) sig = sig + "  # " + mdoc;
                            }
                            lines.add("  " + sig + " { /* ... */ }");
                            written++;
                        } else if ("field".equals(mType)) {
                            fields++;
                            if (detailLevel >= 3) {
                                String sig = compactSignature(m.getSignature(), m.getName());
                                lines.add("  " + sig);
                                written++;
                            }
                        } else if ("constant".equals(mType)) {
                            constants++;
                            if (detailLevel >= 3) {
                                String sig = compactSignature(m.getSignature(), m.getName());
                                lines.add("  " + sig);
                                written++;
                            }
                        }
                    }
                }

                // 分隔空行占预算太多：只在 detailLevel>=3 时留 1 行
                if (detailLevel >= 3 && lines.size() < perFileLineBudget) lines.add("");
                continue;
            }

            if ("method".equals(nt) && "file".equals(fileNode.getNodeType())) {
                methods++;
                String sig = compactSignature(child.getSignature(), child.getName());
                if (detailLevel >= 3) {
                    String doc = compactDoc(child.getDocstring());
                    if (doc != null) sig = sig + "  # " + doc;
                }
                lines.add(sig + " { /* ... */ }");
                if (detailLevel >= 3 && lines.size() < perFileLineBudget) lines.add("");
                continue;
            }

            if ("constant".equals(nt) && detailLevel >= 3) {
                constants++;
                lines.add(compactSignature(child.getSignature(), child.getName()));
            }
        }

        if (!dataClasses.isEmpty() && lines.size() < perFileLineBudget) {
            String joined = String.join(", ", dataClasses);
            if (joined.length() > 160) {
                // 太长就截断，避免一行撑爆预算
                joined = joined.substring(0, 160) + "...(" + dataClasses.size() + ")";
            }
            lines.add(0, "[Data Classes] " + joined);
        }

        if (omittedMembers > 0 && lines.size() < perFileLineBudget) {
            lines.add("// ... 另有 " + omittedMembers + " 个成员未展示");
        }

        if (lines.size() < perFileLineBudget) {
            lines.add("# Total: " + classes + " classes, " + methods + " methods, " + fields + " fields, " + constants + " constants");
        }

        // 输出：严格按行预算截断
        int limit = Math.min(perFileLineBudget, lines.size());
        for (int i = 0; i < limit; i++) {
            out.append(lines.get(i)).append("\n");
        }
    }

    private static boolean isEffectivelyEmptyType(AstNode typeNode) {
        if (typeNode == null) return true;
        if (typeNode.getChildren() == null || typeNode.getChildren().isEmpty()) return true;
        // 仅有 __init__/构造器/简单字段时仍按“数据类”处理
        for (AstNode c : typeNode.getChildren()) {
            if (c == null) continue;
            String nt = c.getNodeType();
            if ("method".equals(nt)) {
                String n = c.getName();
                if (n == null) return false;
                if (!"__init__".equals(n) && !"<init>".equals(n) && !"constructor".equalsIgnoreCase(n)) {
                    return false;
                }
            } else if ("field".equals(nt) || "constant".equals(nt)) {
                // 字段/常量不视为“有行为”，仍可归入数据类
                continue;
            } else {
                return false;
            }
        }
        return true;
    }

    private static String compactSignature(String signature, String fallback) {
        String s = signature != null && !signature.isBlank() ? signature : (fallback != null ? fallback : "");
        s = s.replace("\r", "");
        int nl = s.indexOf('\n');
        if (nl >= 0) s = s.substring(0, nl);
        s = s.replaceAll("\\s+", " ").trim();
        if (s.endsWith("{")) s = s.substring(0, s.length() - 1).trim();
        if (s.length() > 160) s = s.substring(0, 157) + "...";
        return s;
    }

    private static String compactDoc(String docstring) {
        if (docstring == null) return null;
        String s = docstring.replace("\r", "").trim();
        if (s.isEmpty()) return null;
        // 常见 docstring/注释符号裁剪
        s = s.replaceAll("^[/#\\s\\*\"']+", "").trim();
        int nl = s.indexOf('\n');
        if (nl >= 0) s = s.substring(0, nl).trim();
        if (s.length() > 120) s = s.substring(0, 117) + "...";
        return s.isEmpty() ? null : s;
    }

    private void appendDependencyGraph(StringBuilder out, ProjectSkeleton skeleton, int budget, Set<String> topSet) {
        if (skeleton.getDependencies() == null) return;
        List<ProjectSkeleton.DependencyEdge> edges = skeleton.getDependencies();

        // 只输出“真正的依赖关系”，contains 太多会淹没信息
        List<ProjectSkeleton.DependencyEdge> filtered = edges.stream()
                .filter(e -> e != null && e.getType() != null)
                .filter(e -> !"contains".equals(e.getType()))
                .collect(Collectors.toList());
        if (filtered.isEmpty()) filtered = edges;

        // 基于“度”近似重要性（无 PageRank 时的稳定替代），并用 topSet 加权
        Map<String, Integer> degree = new HashMap<>();
        Set<String> dedup = new HashSet<>();
        for (ProjectSkeleton.DependencyEdge e : filtered) {
            String src = e.getSource();
            String tgt = e.getTarget();
            String type = e.getType() != null ? e.getType() : "import";
            if (src == null || tgt == null) continue;
            String key = src + "|" + type + "|" + tgt;
            if (!dedup.add(key)) continue;
            degree.put(src, degree.getOrDefault(src, 0) + 1);
            degree.put(tgt, degree.getOrDefault(tgt, 0) + 1);
        }

        Set<String> seeds = new HashSet<>();
        if (topSet != null && !topSet.isEmpty()) {
            for (String p : topSet) {
                if (p == null || p.isBlank()) continue;
                seeds.add(p);
                seeds.add("file:" + p);
                seeds.add("dir:" + (p.contains("/") ? p.substring(0, p.lastIndexOf('/')) : ""));
            }
        }

        // 选 Top 节点（默认 10 个），确保包含 seeds
        List<String> nodesByDegree = degree.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        Set<String> topNodes = new LinkedHashSet<>();
        for (String s : seeds) {
            if (s == null || s.isBlank()) continue;
            if (degree.containsKey(s)) topNodes.add(s);
            if (topNodes.size() >= 10) break;
        }
        for (String n : nodesByDegree) {
            topNodes.add(n);
            if (topNodes.size() >= 10) break;
        }

        // 选 Top-K 边（最多 10），P2 减少依赖图体积
        record ScoredEdge(ProjectSkeleton.DependencyEdge edge, double score) {}
        List<ScoredEdge> scored = new ArrayList<>();
        Set<String> usedEdgeKeys = new HashSet<>();
        for (ProjectSkeleton.DependencyEdge e : filtered) {
            if (e == null) continue;
            String src = e.getSource();
            String tgt = e.getTarget();
            if (src == null || tgt == null) continue;
            String type = e.getType() != null ? e.getType() : "import";
            String key = src + "|" + type + "|" + tgt;
            if (!usedEdgeKeys.add(key)) continue;
            boolean inA = topNodes.contains(src);
            boolean inB = topNodes.contains(tgt);
            int da = degree.getOrDefault(src, 0);
            int db = degree.getOrDefault(tgt, 0);
            double s = da + db;
            if (inA && inB) s += 1000;
            else if (inA || inB) s += 200;
            scored.add(new ScoredEdge(e, s));
        }
        scored.sort((a, b) -> Double.compare(b.score, a.score));

        // 分组输出
        Map<String, List<ProjectSkeleton.DependencyEdge>> byType = new LinkedHashMap<>();
        int written = 0;
        for (ScoredEdge se : scored) {
            if (written >= 10) break;
            ProjectSkeleton.DependencyEdge e = se.edge;
            String type = e.getType() != null ? e.getType() : "import";
            byType.computeIfAbsent(type, k -> new ArrayList<>()).add(e);
            written++;
        }

        if (byType.isEmpty()) {
            out.append("(no dependency edges)\n");
            return;
        }

        List<String> typeOrder = List.of("import", "extends", "implements", "invokes", "contains");
        List<String> types = new ArrayList<>(byType.keySet());
        types.sort(Comparator.comparingInt(t -> {
            int i = typeOrder.indexOf(t);
            return i >= 0 ? i : 999;
        }));

        for (String type : types) {
            if (out.length() >= budget) break;
            out.append("[").append(type).append("]\n");
            for (ProjectSkeleton.DependencyEdge e : byType.get(type)) {
                if (out.length() >= budget) break;
                String src = e.getSource();
                String tgt = e.getTarget();
                out.append("  ").append(src).append(" --> ").append(tgt).append("\n");
            }
        }

        int omitted = Math.max(0, filtered.size() - written);
        if (omitted > 0 && out.length() < budget) {
            out.append("...（仅展示 Top-").append(written).append(" 依赖边，另有 ").append(omitted).append(" 条未展示）\n");
        }
    }

    private static String langFromPath(String path) {
        if (path == null) return "java";
        if (path.endsWith(".java")) return "java";
        if (path.endsWith(".py")) return "python";
        if (path.endsWith(".ts") || path.endsWith(".tsx")) return "typescript";
        if (path.endsWith(".js")) return "javascript";
        if (path.endsWith(".go")) return "go";
        return "java";
    }

    private static int filePriorityScore(String path, Set<String> topSet) {
        if (path == null) return 0;
        String p = path.replace("\\", "/");
        String lower = p.toLowerCase();
        int score = 0;
        if (topSet != null && topSet.contains(path)) score += 1000;

        boolean isRoot = !p.contains("/");
        if (isRoot) score += 80;

        String base = p.contains("/") ? p.substring(p.lastIndexOf('/') + 1) : p;
        String baseLower = base.toLowerCase();

        if (baseLower.startsWith("readme")) score += 500;
        if ("main.py".equals(baseLower) || "main.java".equals(baseLower) || "__main__.py".equals(baseLower)) score += 450;
        if ("app.py".equals(baseLower) || "application.yml".equals(baseLower) || "application.yaml".equals(baseLower)) score += 320;
        if (baseLower.contains("config") || baseLower.contains("settings")) score += 220;
        if ("__init__.py".equals(baseLower) || "index.ts".equals(baseLower) || "index.js".equals(baseLower)) score += 180;

        // 路径语义关键词
        if (lower.contains("/core/") || lower.contains("/api/") || lower.contains("/service/")) score += 60;
        if (lower.contains("/controller/") || lower.contains("/handler/") || lower.contains("/router/")) score += 50;

        return score;
    }

    private static String escapeMd(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("#", "\\#");
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
