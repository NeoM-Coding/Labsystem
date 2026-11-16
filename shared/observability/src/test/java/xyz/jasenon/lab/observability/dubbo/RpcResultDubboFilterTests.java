package xyz.jasenon.lab.observability.dubbo;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.AsyncRpcResult;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import xyz.jasenon.lab.common.error.ErrorCode;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.common.rpc.RpcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class RpcResultDubboFilterTests {

    private final RpcResultDubboFilter filter = new RpcResultDubboFilter();

    @Test
    void convertsResultBusinessExceptionIntoFailureValue() {
        Result result = filter.invoke(invoker(new AppResponse(
                new BusinessException(403, "没有操作权限")
        )), new RpcInvocation());

        assertEquals(AsyncRpcResult.class, result.getClass());
        Result response = ((AsyncRpcResult) result).getAppResponse();
        RpcResult<?> rpcResult = (RpcResult<?>) response.getValue();
        assertNull(response.getException());
        assertFalse(rpcResult.successful());
        assertEquals(403, rpcResult.error().status());
        assertEquals("没有操作权限", rpcResult.error().message());
    }

    @Test
    void convertsSynchronousFilterFailureIntoFailureValue() {
        Result result = filter.invoke(throwingInvoker(), new RpcInvocation());

        assertEquals(AsyncRpcResult.class, result.getClass());
        RpcResult<?> rpcResult = (RpcResult<?>) ((AsyncRpcResult) result).getAppResponse().getValue();
        assertFalse(rpcResult.successful());
        assertEquals(400, rpcResult.error().status());
    }

    @Test
    void unwrapsProviderRpcWrapperBeforeClassifyingBusinessFailure() {
        Result result = filter.invoke(invoker(new AppResponse(
                new RpcException("provider invocation failed",
                        new BusinessException(404, "设备不存在"))
        )), new RpcInvocation());

        assertEquals(AsyncRpcResult.class, result.getClass());
        RpcResult<?> rpcResult = (RpcResult<?>) ((AsyncRpcResult) result).getAppResponse().getValue();
        assertFalse(rpcResult.successful());
        assertEquals(404, rpcResult.error().status());
        assertEquals("设备不存在", rpcResult.error().message());
    }

    @Test
    void normalizesAnExceptionallyCompletedResponseFuture() {
        var future = new java.util.concurrent.CompletableFuture<AppResponse>();
        var invocation = new RpcInvocation();
        Result result = filter.invoke(invoker(new AsyncRpcResult(future, invocation)), invocation);

        future.completeExceptionally(new DuplicateKeyException("SQL details"));

        var error = ((RpcResult<?>) ((AsyncRpcResult) result).getAppResponse().getValue()).error();
        assertEquals(ErrorCode.DUPLICATE_KEY.code(), error.code());
        assertEquals(ErrorCode.DUPLICATE_KEY.message(), error.message());
        assertEquals(409, error.status());
    }

    @Test
    void normalizesAsynchronousWrappedBusinessFailure() {
        var future = new java.util.concurrent.CompletableFuture<AppResponse>();
        var invocation = new RpcInvocation();
        Result result;
        try (var scope = xyz.jasenon.lab.observability.context.TraceContext.open("trace-async-1", "request-1")) {
            result = filter.invoke(invoker(new AsyncRpcResult(future, invocation)), invocation);
        }
        future.complete(new AppResponse(new java.util.concurrent.CompletionException(
                new BusinessException(xyz.jasenon.lab.common.error.ErrorCode.MQTT_DEVICE_TIMEOUT))));
        RpcResult<?> failure = (RpcResult<?>) ((AsyncRpcResult) result).getAppResponse().getValue();
        assertEquals(130001, failure.error().code());
        assertEquals("trace-async-1", failure.error().traceId());
        assertEquals(504, failure.error().status());
    }

    private static Invoker<Object> invoker(Result response) {
        return new TestInvoker() {
            @Override
            public Result invoke(Invocation invocation) {
                return response;
            }
        };
    }

    private static Invoker<Object> throwingInvoker() {
        return new TestInvoker() {
            @Override
            public Result invoke(Invocation invocation) {
                throw new IllegalArgumentException("参数错误");
            }
        };
    }

    private abstract static class TestInvoker implements Invoker<Object> {

        @Override
        public Class<Object> getInterface() {
            return Object.class;
        }

        @Override
        public URL getUrl() {
            return URL.valueOf("tri://localhost/test?side=provider");
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void destroy() {
        }
    }
}
