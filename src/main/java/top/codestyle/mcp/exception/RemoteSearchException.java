package top.codestyle.mcp.exception;

/**
 * 远程检索异常
 *
 * @author CodeStyle Team
 * @since 2.0.0
 */
public class RemoteSearchException extends RuntimeException {

    private final ErrorCode errorCode;

    public RemoteSearchException(ErrorCode errorCode, String message) {
        super(String.format("[%s] %s", errorCode.name(), message));
        this.errorCode = errorCode;
    }

    public RemoteSearchException(ErrorCode errorCode, String message, Throwable cause) {
        super(String.format("[%s] %s", errorCode.name(), message), cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public enum ErrorCode {
        /** 签名验证失败 */
        SIGNATURE_FAILED,
        /** 访问被拒绝 */
        ACCESS_DENIED,
        /** 请求超时 */
        TIMEOUT,
        /** 服务器错误 */
        SERVER_ERROR,
        /** 网络错误 */
        NETWORK_ERROR,
        /** 配置错误 */
        CONFIG_ERROR
    }
}

