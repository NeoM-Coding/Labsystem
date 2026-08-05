package xyz.jasenon.lab.common.rpc;

import java.io.Serial;
import java.io.Serializable;

public record RpcResult<T>(T data, RpcError error) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public RpcResult {
        if (data != null && error != null) {
            throw new IllegalArgumentException("RPC result cannot contain both data and error");
        }
    }

    public boolean successful() {
        return error == null;
    }

    public static <T> RpcResult<T> success(T data) {
        return new RpcResult<>(data, null);
    }

    public static RpcResult<Void> success() {
        return new RpcResult<>(null, null);
    }

    public static <T> RpcResult<T> failure(RpcError error) {
        if (error == null) {
            throw new IllegalArgumentException("RPC error is required");
        }
        return new RpcResult<>(null, error);
    }
}
