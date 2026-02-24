package top.codestyle.mcp.exception;

/**
 * 远程上传异常
 *
 * @author CodeStyle Team
 * @since 2.1.0
 */
public class RemoteUploadException extends RuntimeException {
    
    public RemoteUploadException(String message) {
        super(message);
    }
    
    public RemoteUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}

