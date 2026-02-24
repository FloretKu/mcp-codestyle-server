package top.codestyle.mcp.model.local;

import lombok.Data;

/**
 * 本地删除结果
 * 用于本地模板删除操作的返回结果
 *
 * @author CodeStyle Team
 * @since 2.1.0
 */
@Data
public class LocalDeleteResult {
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 组织 ID
     */
    private String groupId;
    
    /**
     * 模板 ID
     */
    private String artifactId;
    
    /**
     * 版本号
     */
    private String version;
    
    /**
     * 是否删除了远程
     */
    private boolean deletedFromRemote;
    
    /**
     * 错误信息（失败时）
     */
    private String errorMessage;
}

