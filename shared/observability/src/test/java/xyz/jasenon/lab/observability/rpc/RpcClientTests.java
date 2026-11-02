package xyz.jasenon.lab.observability.rpc;

import org.apache.dubbo.rpc.RpcException;
import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.common.rpc.RpcError;
import xyz.jasenon.lab.common.rpc.RpcErrorType;
import xyz.jasenon.lab.common.rpc.RpcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RpcClientTests {

    @Test
    void returnsSuccessfulPayload() {
        assertEquals("ok", RpcClient.call(() -> RpcResult.success("ok")));
    }

    @Test
    void convertsRpcErrorIntoLocalBusinessException() {
        RpcError error = new RpcError(
                120101, RpcErrorType.NOT_FOUND, 404, "用户不存在", "trace-1"
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> RpcClient.call(() -> RpcResult.failure(error))
        );

        assertEquals(120101, exception.getCode());
        assertEquals(404, exception.getHttpStatus());
        assertEquals("trace-1", exception.getTraceId());
        assertEquals("用户不存在", exception.getMessage());
    }

    @Test
    void distinguishesTransportTimeoutFromDeviceTimeout() {
        BusinessException failure = assertThrows(BusinessException.class,
                () -> RpcClient.call(() -> { throw new RpcException(RpcException.TIMEOUT_EXCEPTION, "transport"); }));
        assertEquals(100006, failure.getCode());
        assertEquals(504, failure.getHttpStatus());
    }

    @Test
    void preservesFailureAcrossAnotherProviderBoundary() {
        var original = new BusinessException(130001, 504, RpcErrorType.DEPENDENCY, "设备超时", java.util.Map.of("deviceId", "1"), "trace-original");
        var local = assertThrows(BusinessException.class, () -> RpcClient.require(RpcResult.failure(RpcErrors.from(original))));
        var forwarded = RpcErrors.from(new java.util.concurrent.CompletionException(local));
        assertEquals(original.getCode(), forwarded.code());
        assertEquals(original.getData(), forwarded.data());
        assertEquals("trace-original", forwarded.traceId());
    }

    @Test
    void convertsTransportFailureIntoServiceUnavailable() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> RpcClient.call(() -> {
                    throw new RpcException("timeout");
                })
        );

        assertEquals(100005, exception.getCode());
        assertEquals(503, exception.getHttpStatus());
    }
}
