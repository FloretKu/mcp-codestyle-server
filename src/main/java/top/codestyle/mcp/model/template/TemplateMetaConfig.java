package top.codestyle.mcp.model.template;

import lombok.Data;
import java.util.List;

/**
 * 模板元数据配置（meta.json 结构）
 * 单版本格式，用于本地缓存和远程下载
 * 
 * 目录结构: groupId/artifactId/version/meta.json
 * 
 * @author CodeStyle Team
 * @since 2.1.0
 */
@Data
public class TemplateMetaConfig {
    /**
     * 组织名（用户名）
     */
    private String groupId;
    
    /**
     * 模板组名
     */
    private String artifactId;
    
    /**
     * 版本号（如 "1.0.0"）
     */
    private String version;
    
    /**
     * 模板名称
     */
    private String name;
    
    /**
     * 模板组总体描述
     */
    private String description;
    
    /**
     * 该版本的文件列表
     */
    private List<FileInfo> files;

    /**
     * 文件信息
     */
    @Data
    public static class FileInfo {
        /**
         * 文件路径（如 "src/main/java/com/air/controller"）
         */
        private String filePath;
        
        /**
         * 文件说明
         */
        private String description;
        
        /**
         * 文件名（如 "Controller.java.ftl"）
         */
        private String filename;
        
        /**
         * 输入变量列表
         */
        private List<TemplateVariable> inputVariables;
        
        /**
         * 文件SHA256哈希值
         */
        private String sha256;
    }
}

