package top.codestyle.mcp.model.remote;

import lombok.Data;

/**
 * 远程删除响应
 * 用于远程模板删除 API 的响应结果
 *
 * @author CodeStyle Team
 * @since 2.1.0
 */
@Data
public class RemoteDeleteResponse {
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
    private DeleteData data;
    
    /**
     * 删除数据
     */
    @Data
    public static class DeleteData {
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
         * 删除时间
         */
        private String deleteTime;
    }
}

