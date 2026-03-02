package top.codestyle.mcp.model.ast;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * AST 节点模型
 * <p>
 * 表示通过 Tree-sitter 解析后的代码结构节点。
 * 涵盖类定义、方法签名、接口、导入声明等。
 *
 * @since 2.1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AstNode {

    /**
     * 节点类型 (class, method, interface, import, package)
     */
    private String nodeType;

    /**
     * 节点名称 (类名、方法名、包名等)
     */
    private String name;

    /**
     * 节点所在文件路径 (相对路径)
     */
    private String filePath;

    /**
     * 起始行号
     */
    private int startLine;

    /**
     * 结束行号
     */
    private int endLine;

    /**
     * 签名摘要 (方法签名、类声明头等，不含实现体)
     */
    private String signature;

    /**
     * 子节点列表 (类下的方法等)
     */
    @Builder.Default
    private List<AstNode> children = new ArrayList<>();

    /**
     * 引用列表 (该节点引用了哪些其他类/方法)
     */
    @Builder.Default
    private List<String> references = new ArrayList<>();
}
