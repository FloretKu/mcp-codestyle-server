package top.codestyle.mcp.model.remote;

import lombok.Data;

/**
 * 远程上传响应
 * 用于远程模板上传 API 的响应结果
 *
 * @author CodeStyle Team
 * @since 2.1.0
 */
@Data
public class RemoteUploadResponse {
    /**
     * 响应码（0=成功）
     */
    private String code;
    
    /**
     * 是否成功
     */
    private Boolean success;
    
    /**
     * 消息
     */
    private String msg;
    
    /**
     * 数据
     */
    private UploadData data;
    
    /**
     * 上传数据
     */
    @Data
    public static class UploadData {
        /** 模板 ID */
        private String templateId;
        /** 组织 ID */
        private String groupId;
        /** 模板 ID（artifactId） */
        private String artifactId;
        /** 版本号 */
        private String version;
        /** 模板名称 */
        private String name;
        /** 模板描述 */
        private String description;
        /** 下载 URL */
        private String downloadUrl;
        /** 上传时间 */
        private String uploadTime;
        /** 文件数量 */
        private Integer fileCount;
        /** 文件列表 */
        private Object files;
    }
}

