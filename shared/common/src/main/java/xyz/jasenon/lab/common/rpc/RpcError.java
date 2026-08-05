package xyz.jasenon.lab.common.rpc;

import java.io.Serial;
import java.io.Serializable;

public record RpcError(
        String code,
        RpcErrorType type,
        int status,
        String message,
        String traceId
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
