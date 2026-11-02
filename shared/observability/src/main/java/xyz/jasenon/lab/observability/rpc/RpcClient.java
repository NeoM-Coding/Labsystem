package xyz.jasenon.lab.observability.rpc;

import org.apache.dubbo.rpc.RpcException;
import xyz.jasenon.lab.common.error.ErrorCode;
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
            BusinessException local = new BusinessException(exception.isTimeout()
                    ? ErrorCode.RPC_TIMEOUT : ErrorCode.DEPENDENCY_UNAVAILABLE);
            local.initCause(exception);
            throw local;
        }
        return require(result);
    }

    public static <T> T require(RpcResult<T> result) {
        if (result == null) {
            throw new BusinessException(ErrorCode.INVALID_RPC_RESPONSE, "下游服务返回了空响应");
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
        if (error == null || error.code() < 100000 || error.code() > 999999
                || error.status() < 400 || error.status() > 599 || error.type() == null) {
            return new BusinessException(ErrorCode.INVALID_RPC_RESPONSE, "下游服务返回了无效错误状态");
        }
        return new BusinessException(error.code(), error.status(), error.type(),
                error.message(), error.data(), error.traceId());
    }
}
