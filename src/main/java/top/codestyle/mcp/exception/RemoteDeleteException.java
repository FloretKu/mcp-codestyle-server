package top.codestyle.mcp.exception;

/**
 * 远程删除异常
 *
 * @author CodeStyle Team
 * @since 2.1.0
 */
public class RemoteDeleteException extends RuntimeException {
    
    public RemoteDeleteException(String message) {
        super(message);
    }
    
    public RemoteDeleteException(String message, Throwable cause) {
        super(message, cause);
    }
}

