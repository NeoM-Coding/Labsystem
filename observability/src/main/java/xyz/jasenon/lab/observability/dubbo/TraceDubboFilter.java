package xyz.jasenon.lab.observability.dubbo;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.context.UserContextHolder;
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
            RpcContext.getClientAttachment().setAttachment(TRACE_ATTACHMENT, TraceContext.traceId());
            RpcContext.getClientAttachment().setAttachment(REQUEST_ATTACHMENT, TraceContext.requestId());
            UserContext user = UserContextHolder.get();
            if (user != null) {
                RpcContext.getClientAttachment().setObjectAttachment(UserContextHolder.DUBBO_ATTACHMENT_KEY, user);
            }
            return invoker.invoke(invocation);
        } finally {
            if (scope != null) scope.close();
        }
    }

    private Result invokeProvider(Invoker<?> invoker, Invocation invocation) {
        Object traceId = RpcContext.getServerAttachment().getAttachment(TRACE_ATTACHMENT);
        Object requestId = RpcContext.getServerAttachment().getAttachment(REQUEST_ATTACHMENT);
        Object user = RpcContext.getServerAttachment()
                .getObjectAttachment(UserContextHolder.DUBBO_ATTACHMENT_KEY);
        if (user == null) {
            user = RpcContext.getServerAttachment()
                    .getObjectAttachment(UserContextHolder.LEGACY_DUBBO_ATTACHMENT_KEY);
        }
        UserContextHolder.clear();
        if (user instanceof UserContext userContext) {
            UserContextHolder.set(userContext);
        }
        try (TraceContext.Scope ignored = TraceContext.open(stringValue(traceId), stringValue(requestId))) {
            return invoker.invoke(invocation);
        } finally {
            // Dubbo provider 线程会复用，必须在调用完成后清除用户上下文。
            UserContextHolder.clear();
        }
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
