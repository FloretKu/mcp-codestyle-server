package top.codestyle.mcp.model.template;

import lombok.Data;
import java.util.List;

/**
 * 模板元信息（通用）
 * 用于本地和远程的模板元数据表示
 *
 * @author CodeStyle Team
 * @since 2.1.0
 */
@Data
public class TemplateMetaInfo {
    /**
     * 模板ID（远程使用）
     */
    private Long id;
    
    /**
     * 组织名（如: continew）
     */
    private String groupId;
    
    /**
     * 模板组名（如: CRUD）
     */
    private String artifactId;
    
    /**
     * 模板描述（如: 控制层）
     */
    private String description;
    
    /**
     * 文件SHA256哈希值
     */
    private String sha256;
    
    /**
     * 版本号（如: 1.0.0）
     */
    private String version;
    
    /**
     * 模板文件名（如: Controller.java.ftl）
     */
    private String filename;
    
    /**
     * 模板文件所在目录路径（如: /src/main/java/com/air/controller）
     */
    private String filePath;
    
    /**
     * 模板文件完整路径（如: /src/main/java/com/air/controller/Controller.java.ftl）
     */
    private String path;

    /**
     * 模板输入变量列表
     */
    private List<TemplateVariable> inputVariables;
}

