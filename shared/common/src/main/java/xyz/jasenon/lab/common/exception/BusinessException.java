package xyz.jasenon.lab.common.exception;

import xyz.jasenon.lab.common.error.ErrorCode;
import xyz.jasenon.lab.common.rpc.RpcErrorType;
import xyz.jasenon.lab.common.util.R;

/** Local business failure. RPC transports RpcError values rather than exceptions. */
public class BusinessException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final int code;
    private final int httpStatus;
    private final Object data;
    private final RpcErrorType type;
    private final String traceId;

    public BusinessException(ErrorCode error) { this(error, error.message()); }
    public static BusinessException causedBy(ErrorCode error, Throwable cause) {
        BusinessException exception = new BusinessException(error);
        exception.initCause(cause);
        return exception;
    }
    public BusinessException(ErrorCode error, String message) { this(error, message, null); }
    public BusinessException(ErrorCode error, String message, Object data) {
        this(error.code(), error.httpStatus(), error.type(), message == null || message.isBlank() ? error.message() : message, data, null);
    }
    /** Compatibility for internal callers; HTTP codes are normalized to catalog codes. */
    public BusinessException(Integer code, String message) { this(code, message, null); }
    public BusinessException(Integer code, String message, Object data) {
        this(code != null && code >= 100000 ? ErrorCode.fromCode(code) : ErrorCode.forHttpStatus(code == null ? 500 : code), message, data);
    }
    public BusinessException(R<?> response) {
        this(response.getCode(), response.getHttpStatus(), ErrorCode.fromCode(response.getCode()).type(), response.getMsg(), response.getData(), response.getTraceId());
    }
    public BusinessException(int code, int httpStatus, RpcErrorType type, String message, Object data, String traceId) {
        super(message);
        this.code = code; this.httpStatus = httpStatus; this.type = type; this.data = data; this.traceId = traceId;
    }
    public Integer getCode() { return code; }
    public int getHttpStatus() { return httpStatus; }
    public RpcErrorType getType() { return type; }
    public String getTraceId() { return traceId; }
    public boolean isOk() { return false; }
    public Object getData() { return data; }
    public R<?> getR() { return R.fail(httpStatus, code, getMessage(), data).withTraceId(traceId); }
}
