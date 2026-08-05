package xyz.jasenon.lab.observability.dubbo;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import xyz.jasenon.lab.observability.context.TraceContext;

@Activate(group = {CommonConstants.CONSUMER, CommonConstants.PROVIDER}, order = -200)
public class TraceDubboFilter implements Filter {

    private static final String TRACE_ATTACHMENT = "trace-id";
    private static final String REQUEST_ATTACHMENT = "request-id";

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String side = invoker.getUrl().getParameter(CommonConstants.SIDE_KEY);
        if (CommonConstants.CONSUMER_SIDE.equals(side)) {
            return invokeConsumer(invoker, invocation);
        }
        return invokeProvider(invoker, invocation);
    }

    private Result invokeConsumer(Invoker<?> invoker, Invocation invocation) {
        boolean ownsContext = TraceContext.traceId() == null;
        TraceContext.Scope scope = ownsContext ? TraceContext.open(null, null) : null;
        try {
            invocation.setAttachment(TRACE_ATTACHMENT, TraceContext.traceId());
            invocation.setAttachment(REQUEST_ATTACHMENT, TraceContext.requestId());
            return invoker.invoke(invocation);
        } finally {
            if (scope != null) scope.close();
        }
    }

    private Result invokeProvider(Invoker<?> invoker, Invocation invocation) {
        String traceId = invocation.getAttachment(TRACE_ATTACHMENT);
        String requestId = invocation.getAttachment(REQUEST_ATTACHMENT);
        try (TraceContext.Scope ignored = TraceContext.open(traceId, requestId)) {
            return invoker.invoke(invocation);
        }
    }
}
