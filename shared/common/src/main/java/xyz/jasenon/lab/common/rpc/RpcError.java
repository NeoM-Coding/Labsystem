package xyz.jasenon.lab.common.rpc;

import java.io.Serial;
import java.io.Serializable;

public record RpcError(
        int code,
        RpcErrorType type,
        int status,
        String message,
        String traceId,
        Object data
) implements Serializable {

    public RpcError(int code, RpcErrorType type, int status, String message, String traceId) {
        this(code, type, status, message, traceId, null);
    }

    @Serial
    private static final long serialVersionUID = 1L;
}
