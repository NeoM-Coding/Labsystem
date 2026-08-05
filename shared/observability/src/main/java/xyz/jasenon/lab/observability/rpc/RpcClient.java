package xyz.jasenon.lab.observability.rpc;

import org.apache.dubbo.rpc.RpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.common.rpc.RpcError;
import xyz.jasenon.lab.common.rpc.RpcResult;

import java.util.function.Supplier;

public final class RpcClient {

    private static final Logger log = LoggerFactory.getLogger(RpcClient.class);

    private RpcClient() {
    }

    public static <T> T call(Supplier<RpcResult<T>> invocation) {
        final RpcResult<T> result;
        try {
            result = invocation.get();
        } catch (RpcException exception) {
            log.error("RPC transport failed before a valid RpcResult was received", exception);
            BusinessException local = new BusinessException(503, "下游服务暂不可用");
            local.initCause(exception);
            throw local;
        }
        return require(result);
    }

    public static <T> T require(RpcResult<T> result) {
        if (result == null) {
            throw new BusinessException(502, "下游服务返回了空响应");
        }
        if (!result.successful()) {
            throw localException(result.error());
        }
        return result.data();
    }

    public static void run(Supplier<RpcResult<Void>> invocation) {
        call(invocation);
    }

    private static BusinessException localException(RpcError error) {
        if (error == null) {
            return new BusinessException(502, "下游服务返回了无效错误状态");
        }
        String message = error.message();
        if (error.traceId() != null && !error.traceId().isBlank() && error.status() >= 500) {
            message = message + "（traceId: " + error.traceId() + "）";
        }
        return new BusinessException(error.status(), message);
    }
}
