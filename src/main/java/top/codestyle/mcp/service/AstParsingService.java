package top.codestyle.mcp.service;

import org.treesitter.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.model.ast.AstNode;
import top.codestyle.mcp.model.ast.ProjectSkeleton;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AST 解析微服务
 * <p>
 * 利用 Tree-sitter JNI 绑定对多语言项目执行全量 AST 扫描，
 * 抽取类/接口/方法签名与 import 依赖关系，构建 ProjectSkeleton。
 *
 * <p>
 * 借鉴 repomix 的 parseFile.ts 与 queryJava.ts 中的 AST query 模式，
 * 以及 RepoMaster 论文中 HCT / MDG 的构建理念。
 *
 * @since 2.1.0
 */
@Slf4j
@Service
public class AstParsingService {

    /** 支持的语言与对应的 Tree-sitter TSLanguage 实例 (io.github.bonede API) */
    private static final Map<String, TSLanguage> LANGUAGE_MAP = new LinkedHashMap<>();

    static {
        LANGUAGE_MAP.put("java", new TreeSitterJava());
        LANGUAGE_MAP.put("py", new TreeSitterPython());
        LANGUAGE_MAP.put("js", new TreeSitterJavascript());
        LANGUAGE_MAP.put("ts", new TreeSitterTypescript());
        LANGUAGE_MAP.put("tsx", new TreeSitterTypescript());
        LANGUAGE_MAP.put("go", new TreeSitterGo());
    }

    /**
     * 解析指定目录下的所有受支持语言的源码文件，构建 ProjectSkeleton。
     *
     * @param projectPath 项目根目录绝对路径
     * @return 项目骨架模型
     * @throws IOException 文件读取异常
     */
    public ProjectSkeleton parseProject(String projectPath) throws IOException {
        Path root = Paths.get(projectPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("路径不是目录: " + projectPath);
        }

        List<AstNode> fileNodes = new ArrayList<>();
        List<ProjectSkeleton.DependencyEdge> dependencies = new ArrayList<>();
        int[] totalClasses = { 0 };
        int[] totalMethods = { 0 };
        int[] totalFiles = { 0 };

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String ext = getExtension(file);
                if (ext != null && LANGUAGE_MAP.containsKey(ext)) {
                    try {
                        String content = Files.readString(file, StandardCharsets.UTF_8);
                        String relativePath = root.relativize(file).toString().replace('\\', '/');

                        AstNode fileNode = parseFileAst(content, relativePath, ext);
                        if (fileNode != null) {
                            fileNodes.add(fileNode);
                            totalFiles[0]++;

                            // 统计子节点
                            for (AstNode child : fileNode.getChildren()) {
                                if ("class".equals(child.getNodeType()) || "interface".equals(child.getNodeType())) {
                                    totalClasses[0]++;
                                    totalMethods[0] += child.getChildren().size();
                                }
                            }

                            // 提取 import 依赖
                            for (AstNode child : fileNode.getChildren()) {
                                if ("import".equals(child.getNodeType())) {
                                    dependencies.add(ProjectSkeleton.DependencyEdge.builder()
                                            .source(relativePath)
                                            .target(child.getName())
                                            .type("import")
                                            .build());
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("解析文件失败 [{}]: {}", file, e.getMessage());
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String name = dir.getFileName().toString();
                // 跳过隐藏目录和常见构建/依赖目录
                if (name.startsWith(".") || "node_modules".equals(name) ||
                        "target".equals(name) || "build".equals(name) ||
                        "__pycache__".equals(name) || "vendor".equals(name)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return ProjectSkeleton.builder()
                .projectPath(projectPath)
                .totalFiles(totalFiles[0])
                .totalClasses(totalClasses[0])
                .totalMethods(totalMethods[0])
                .fileNodes(fileNodes)
                .dependencies(dependencies)
                .build();
    }

    /**
     * 解析单个文件的 AST，返回该文件的顶级节点。
     * <p>
     * 参考 repomix/src/core/treeSitter/parseFile.ts 中的解析流程。
     *
     * @param content      文件内容
     * @param relativePath 文件相对路径
     * @param ext          文件扩展名
     * @return 代表该文件的 AstNode（children 为类/方法/import 等）
     */
    private AstNode parseFileAst(String content, String relativePath, String ext) {
        TSLanguage language = LANGUAGE_MAP.get(ext);
        if (language == null)
            return null;

        TSParser parser = new TSParser();
        parser.setLanguage(language);
        TSTree tree = parser.parseString(null, content);
        if (tree == null) {
            log.debug("Tree-sitter 解析返回 null: {}", relativePath);
            return null;
        }
        try {
            TSNode rootNode = tree.getRootNode();
                List<AstNode> children = new ArrayList<>();
                String[] lines = content.split("\n");

                // 遍历顶层子节点
                for (int i = 0; i < rootNode.getChildCount(); i++) {
                    TSNode child = rootNode.getChild(i);
                    AstNode parsed = visitNode(child, lines, relativePath, ext);
                    if (parsed != null) {
                        children.add(parsed);
                    }
                }

            return AstNode.builder()
                    .nodeType("file")
                    .name(relativePath)
                    .filePath(relativePath)
                    .startLine(0)
                    .endLine(lines.length)
                    .children(children)
                    .build();
        } catch (Exception e) {
            log.warn("Tree-sitter 解析异常 [{}]: {}", relativePath, e.getMessage());
            return null;
        }
    }

    /**
     * 递归访问 AST 节点并提取有意义的结构信息。
     * <p>
     * 借鉴 repomix queryJava.ts 中的 capture 模式：
     * class_declaration, method_declaration, interface_declaration,
     * import_declaration, method_invocation 等。
     */
    private AstNode visitNode(TSNode node, String[] lines, String filePath, String ext) {
        String type = node.getType();

        return switch (type) {
            // Java / TypeScript / Go 类定义
            case "class_declaration", "class_definition" -> {
                String name = extractChildName(node, "name", lines);
                List<AstNode> methods = new ArrayList<>();
                List<String> refs = new ArrayList<>();

                for (int i = 0; i < node.getChildCount(); i++) {
                    TSNode child = node.getChild(i);
                    String childType = child.getType();
                    if ("method_declaration".equals(childType) || "method_definition".equals(childType) ||
                            "function_definition".equals(childType)) {
                        String methodName = extractChildName(child, "name", lines);
                        String sig = extractSignatureLine(child, lines);
                        methods.add(AstNode.builder()
                                .nodeType("method")
                                .name(methodName != null ? methodName : "anonymous")
                                .filePath(filePath)
                                .startLine(child.getStartPoint().getRow())
                                .endLine(child.getEndPoint().getRow())
                                .signature(sig)
                                .build());
                    }
                    // 提取父类/接口引用
                    if ("superclass".equals(childType) || "type_list".equals(childType)) {
                        refs.add(extractNodeText(child, lines));
                    }
                }

                yield AstNode.builder()
                        .nodeType("class")
                        .name(name != null ? name : "AnonymousClass")
                        .filePath(filePath)
                        .startLine(node.getStartPoint().getRow())
                        .endLine(node.getEndPoint().getRow())
                        .signature(extractSignatureLine(node, lines))
                        .children(methods)
                        .references(refs)
                        .build();
            }

            case "interface_declaration" -> {
                String name = extractChildName(node, "name", lines);
                List<AstNode> methods = new ArrayList<>();
                for (int i = 0; i < node.getChildCount(); i++) {
                    TSNode child = node.getChild(i);
                    if ("method_declaration".equals(child.getType())) {
                        String methodName = extractChildName(child, "name", lines);
                        methods.add(AstNode.builder()
                                .nodeType("method")
                                .name(methodName != null ? methodName : "anonymous")
                                .filePath(filePath)
                                .startLine(child.getStartPoint().getRow())
                                .endLine(child.getEndPoint().getRow())
                                .signature(extractSignatureLine(child, lines))
                                .build());
                    }
                }
                yield AstNode.builder()
                        .nodeType("interface")
                        .name(name != null ? name : "AnonymousInterface")
                        .filePath(filePath)
                        .startLine(node.getStartPoint().getRow())
                        .endLine(node.getEndPoint().getRow())
                        .signature(extractSignatureLine(node, lines))
                        .children(methods)
                        .build();
            }

            case "import_declaration", "import_statement" -> {
                String importText = extractNodeText(node, lines).trim();
                yield AstNode.builder()
                        .nodeType("import")
                        .name(importText)
                        .filePath(filePath)
                        .startLine(node.getStartPoint().getRow())
                        .endLine(node.getEndPoint().getRow())
                        .build();
            }

            case "package_declaration" -> {
                String pkgText = extractNodeText(node, lines).trim();
                yield AstNode.builder()
                        .nodeType("package")
                        .name(pkgText)
                        .filePath(filePath)
                        .startLine(node.getStartPoint().getRow())
                        .endLine(node.getEndPoint().getRow())
                        .build();
            }

            // 顶层函数 (Python, Go, JS/TS)
            case "function_declaration", "function_definition" -> {
                String name = extractChildName(node, "name", lines);
                yield AstNode.builder()
                        .nodeType("method")
                        .name(name != null ? name : "anonymous")
                        .filePath(filePath)
                        .startLine(node.getStartPoint().getRow())
                        .endLine(node.getEndPoint().getRow())
                        .signature(extractSignatureLine(node, lines))
                        .build();
            }

            default -> null;
        };
    }

    /**
     * 从子节点中提取命名标识符的文本
     */
    private String extractChildName(TSNode node, String fieldName, String[] lines) {
        TSNode nameNode = node.getChildByFieldName(fieldName);
        if (nameNode != null && !nameNode.isNull()) {
            return extractNodeText(nameNode, lines).trim();
        }
        return null;
    }

    /**
     * 提取节点所覆盖的完整文本
     */
    private String extractNodeText(TSNode node, String[] lines) {
        int startRow = node.getStartPoint().getRow();
        int endRow = node.getEndPoint().getRow();
        if (startRow >= lines.length)
            return "";
        if (startRow == endRow) {
            return lines[startRow];
        }
        StringBuilder sb = new StringBuilder();
        for (int i = startRow; i <= Math.min(endRow, lines.length - 1); i++) {
            sb.append(lines[i]);
            if (i < endRow)
                sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 仅提取签名行（第一行），不包含方法体
     */
    private String extractSignatureLine(TSNode node, String[] lines) {
        int startRow = node.getStartPoint().getRow();
        if (startRow < lines.length) {
            return lines[startRow].trim();
        }
        return "";
    }

    /**
     * 获取文件扩展名（不含点号）
     */
    private String getExtension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            return name.substring(dot + 1);
        }
        return null;
    }

    // ========================= XML 输出 =========================

    /**
     * 将 ProjectSkeleton 转换为 XML 格式的摘要（借鉴 Repomix XML 输出模式）
     *
     * @param skeleton         项目骨架
     * @param compressionRatio 压缩比（0.0~1.0），0.9 表示只保留前 10% 的重要节点
     * @return XML 格式的项目骨架字符串
     */
    public String toXmlSkeleton(ProjectSkeleton skeleton, double compressionRatio) {
        StringBuilder xml = new StringBuilder();
        xml.append("<project_skeleton>\n");
        xml.append("  <summary>\n");
        xml.append("    <path>").append(escapeXml(skeleton.getProjectPath())).append("</path>\n");
        xml.append("    <total_files>").append(skeleton.getTotalFiles()).append("</total_files>\n");
        xml.append("    <total_classes>").append(skeleton.getTotalClasses()).append("</total_classes>\n");
        xml.append("    <total_methods>").append(skeleton.getTotalMethods()).append("</total_methods>\n");
        xml.append("  </summary>\n");

        xml.append("  <directory_structure>\n");
        // 按文件路径排序输出
        List<AstNode> sortedFiles = skeleton.getFileNodes().stream()
                .sorted(Comparator.comparing(AstNode::getFilePath))
                .collect(Collectors.toList());

        for (AstNode fileNode : sortedFiles) {
            xml.append("    <file path=\"").append(escapeXml(fileNode.getFilePath())).append("\">\n");
            for (AstNode child : fileNode.getChildren()) {
                if ("import".equals(child.getNodeType()) || "package".equals(child.getNodeType())) {
                    continue; // imports 放在 dependencies 区块
                }
                appendNodeXml(xml, child, "      ");
            }
            xml.append("    </file>\n");
        }
        xml.append("  </directory_structure>\n");

        // 依赖图
        if (!skeleton.getDependencies().isEmpty()) {
            xml.append("  <dependency_graph>\n");
            for (ProjectSkeleton.DependencyEdge edge : skeleton.getDependencies()) {
                xml.append("    <edge source=\"").append(escapeXml(edge.getSource()))
                        .append("\" target=\"").append(escapeXml(edge.getTarget()))
                        .append("\" type=\"").append(edge.getType()).append("\" />\n");
            }
            xml.append("  </dependency_graph>\n");
        }

        xml.append("</project_skeleton>");
        return xml.toString();
    }

    private void appendNodeXml(StringBuilder xml, AstNode node, String indent) {
        String tag = node.getNodeType();
        xml.append(indent).append("<").append(tag);
        if (node.getName() != null) {
            xml.append(" name=\"").append(escapeXml(node.getName())).append("\"");
        }
        if (node.getSignature() != null && !node.getSignature().isEmpty()) {
            xml.append(" signature=\"").append(escapeXml(node.getSignature())).append("\"");
        }

        if (node.getChildren() != null && !node.getChildren().isEmpty()) {
            xml.append(">\n");
            for (AstNode child : node.getChildren()) {
                appendNodeXml(xml, child, indent + "  ");
            }
            xml.append(indent).append("</").append(tag).append(">\n");
        } else {
            xml.append(" />\n");
        }
    }

    private String escapeXml(String text) {
        if (text == null)
            return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
