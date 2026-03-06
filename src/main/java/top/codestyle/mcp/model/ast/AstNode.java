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
 * 涵盖类定义、方法签名、接口、导入声明、枚举、字段、常量、导出等。
 * 支持 nodeType: class, method, interface, import, package, enum, field,
 * constant, export, decorator, directory, import_summary, file。
 *
 * @since 2.1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AstNode {

    /**
     * 节点类型 (class, method, interface, import, package, enum, field,
     * constant, export, decorator, directory, import_summary, file)
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
     * 引用列表 (该节点引用了哪些其他类/方法，如父类、接口、被调用方法)
     */
    @Builder.Default
    private List<String> references = new ArrayList<>();

    /**
     * Docstring / JavaDoc / JSDoc 文档注释
     */
    private String docstring;

    /**
     * 注解或装饰器列表，如 @Service, @Override, @decorator
     */
    @Builder.Default
    private List<String> annotations = new ArrayList<>();

    /**
     * 可见性: public, private, protected, default (Java) 或空
     */
    private String visibility;

    /**
     * 方法返回类型（仅 method 节点）
     */
    private String returnType;

    /**
     * 方法参数类型列表（仅 method 节点）
     */
    @Builder.Default
    private List<String> parameterTypes = new ArrayList<>();

    /**
     * 是否为抽象方法/类
     */
    private boolean isAbstract;

    /**
     * 是否为静态方法/字段
     */
    private boolean isStatic;
}
