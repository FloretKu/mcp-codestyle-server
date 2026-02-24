package top.codestyle.mcp.model.local;

import lombok.Data;

/**
 * 本地上传结果
 * 用于本地模板保存操作的返回结果
 *
 * @author CodeStyle Team
 * @since 2.1.0
 */
@Data
public class LocalUploadResult {
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
     * 文件数量
     */
    private int fileCount;
    
    /**
     * 是否上传到远程
     */
    private boolean uploadedToRemote;
    
    /**
     * 错误信息（失败时）
     */
    private String errorMessage;
    
    /**
     * 本地路径
     */
    private String localPath;
    
    /**
     * 远程 ID（远程上传成功时）
     */
    private String remoteId;
}

