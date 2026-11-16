package xyz.jasenon.lab.observability.dubbo;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.AsyncRpcResult;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import xyz.jasenon.lab.common.rpc.RpcResult;
import xyz.jasenon.lab.observability.rpc.RpcErrors;

@Activate(group = CommonConstants.PROVIDER, order = -195)
public class RpcResultDubboFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        var propagation = xyz.jasenon.lab.observability.context.Tracing.capture();
        try {
            Result result = invoker.invoke(invocation);
            if (result instanceof AsyncRpcResult async) {
                async.setResponseFuture(async.getResponseFuture().handle((response, failure) ->
                        propagation.get(() -> {
                            if (failure != null) {
                                return new AppResponse(RpcResult.failure(RpcErrors.from(failure)));
                            }
                            normalize(response);
                            return response;
                        })));
                return async;
            }
            normalize(result);
            return AsyncRpcResult.newDefaultAsyncResult(
                    result.getValue(), result.getException(), invocation
            );
        } catch (Throwable failure) {
            return AsyncRpcResult.newDefaultAsyncResult(
                    RpcResult.failure(RpcErrors.from(failure)), invocation
            );
        }
    }

    private static void normalize(Result result) {
        if (result == null) {
            return;
        }
        Throwable exception = result.getException();
        if (exception == null) {
            return;
        }
        result.setException(null);
        result.setValue(RpcResult.failure(RpcErrors.from(exception)));
    }
}
