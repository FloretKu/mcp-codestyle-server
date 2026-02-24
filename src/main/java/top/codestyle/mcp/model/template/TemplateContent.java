package top.codestyle.mcp.model.template;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 带内容的模板信息
 * 继承 TemplateMetaInfo，增加模板文件内容字段
 * 用于本地缓存和内容读取
 *
 * @author CodeStyle Team
 * @since 2.1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TemplateContent extends TemplateMetaInfo {
    /**
     * 模板文件内容
     */
    private String templateContent;
}

