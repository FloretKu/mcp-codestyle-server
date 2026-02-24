package top.codestyle.mcp.model.remote;

import lombok.Data;

/**
 * 远程搜索结果
 * 用于远程模板检索的返回结果
 *
 * @author CodeStyle Team
 * @since 2.1.0
 */
@Data
public class RemoteSearchResult {
    /**
     * 搜索结果 ID
     */
    private String id;
    
    /**
     * 标题
     */
    private String title;
    
    /**
     * 摘要
     */
    private String snippet;
    
    /**
     * 内容
     */
    private String content;
    
    /**
     * 评分
     */
    private Double score;
    
    /**
     * 高亮内容
     */
    private String highlight;
    
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
     * 文件类型
     */
    private String fileType;
    
    /**
     * 文件路径
     */
    private String filePath;
    
    /**
     * 文件名
     */
    private String filename;
    
    /**
     * 文件哈希值
     */
    private String sha256;
    
    /**
     * 生成下载 URL
     */
    public String getDownloadUrl(String baseUrl) {
        if (groupId != null && artifactId != null && version != null) {
            return String.format("%s/open-api/template/download?groupId=%s&artifactId=%s&version=%s",
                baseUrl, groupId, artifactId, version);
        }
        return null;
    }
}

