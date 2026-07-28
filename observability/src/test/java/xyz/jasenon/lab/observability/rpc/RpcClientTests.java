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
                "USER_NOT_FOUND", RpcErrorType.NOT_FOUND, 404, "用户不存在", "trace-1"
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> RpcClient.call(() -> RpcResult.failure(error))
        );

        assertEquals(404, exception.getCode());
        assertEquals("用户不存在", exception.getMessage());
    }

    @Test
    void convertsTransportFailureIntoServiceUnavailable() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> RpcClient.call(() -> {
                    throw new RpcException("timeout");
                })
        );

        assertEquals(503, exception.getCode());
    }
}
