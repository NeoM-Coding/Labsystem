package xyz.jasenon.lab.observability.dubbo;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.AsyncRpcResult;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import xyz.jasenon.lab.common.rpc.RpcResult;
import xyz.jasenon.lab.observability.rpc.RpcErrors;

@Activate(group = CommonConstants.PROVIDER, order = -10000)
public class RpcResultDubboFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        try {
            Result result = invoker.invoke(invocation);
            if (result instanceof AsyncRpcResult) {
                return result.whenCompleteWithContext((response, failure) -> normalize(response, failure));
            }
            normalize(result, null);
            return AsyncRpcResult.newDefaultAsyncResult(
                    result.getValue(), result.getException(), invocation
            );
        } catch (Throwable failure) {
            return AsyncRpcResult.newDefaultAsyncResult(
                    RpcResult.failure(RpcErrors.from(failure)), invocation
            );
        }
    }

    private static void normalize(Result result, Throwable failure) {
        if (result == null) {
            return;
        }
        Throwable exception = failure != null ? failure : result.getException();
        if (exception == null) {
            return;
        }
        result.setException(null);
        result.setValue(RpcResult.failure(RpcErrors.from(exception)));
    }
}
