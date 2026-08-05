package xyz.jasenon.lab.observability.dubbo;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcInvocation;
import org.junit.jupiter.api.Test;
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

        RpcResult<?> rpcResult = (RpcResult<?>) result.getValue();
        assertNull(result.getException());
        assertFalse(rpcResult.successful());
        assertEquals(403, rpcResult.error().status());
        assertEquals("没有操作权限", rpcResult.error().message());
    }

    @Test
    void convertsSynchronousFilterFailureIntoFailureValue() {
        Result result = filter.invoke(throwingInvoker(), new RpcInvocation());

        RpcResult<?> rpcResult = (RpcResult<?>) result.getValue();
        assertFalse(rpcResult.successful());
        assertEquals(400, rpcResult.error().status());
    }

    @Test
    void unwrapsProviderRpcWrapperBeforeClassifyingBusinessFailure() {
        Result result = filter.invoke(invoker(new AppResponse(
                new RpcException("provider invocation failed",
                        new BusinessException(404, "设备不存在"))
        )), new RpcInvocation());

        RpcResult<?> rpcResult = (RpcResult<?>) result.getValue();
        assertFalse(rpcResult.successful());
        assertEquals(404, rpcResult.error().status());
        assertEquals("设备不存在", rpcResult.error().message());
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
