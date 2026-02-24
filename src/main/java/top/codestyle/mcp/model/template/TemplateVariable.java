package top.codestyle.mcp.model.template;

import lombok.Data;

/**
 * 模板变量（通用）
 * 用于本地和远程的模板变量定义
 *
 * @author CodeStyle Team
 * @since 2.1.0
 */
@Data
public class TemplateVariable {
    /**
     * 变量名
     */
    private String variableName;
    
    /**
     * 变量类型
     */
    private String variableType;
    
    /**
     * 变量注释
     */
    private String variableComment;
    
    /**
     * 示例值
     */
    private String example;
}

