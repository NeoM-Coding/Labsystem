package xyz.jasenon.lab.observability.dubbo;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import xyz.jasenon.lab.observability.context.TraceContext;
import xyz.jasenon.lab.observability.context.Spans;
import io.opentelemetry.api.trace.SpanKind;

@Activate(group = {CommonConstants.CONSUMER, CommonConstants.PROVIDER}, order = -200)
public class TraceDubboFilter implements Filter {

    private static final String TRACE_ATTACHMENT = "trace-id";
    private static final String REQUEST_ATTACHMENT = "request-id";

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String side = invoker.getUrl().getParameter(CommonConstants.SIDE_KEY);
        boolean consumer = CommonConstants.CONSUMER_SIDE.equals(side);
        var builder = Spans.builder("rpc " + invocation.getMethodName())
                .setSpanKind(consumer ? SpanKind.CLIENT : SpanKind.SERVER);
        if (!consumer) {
            java.util.Map<String, String> headers = new java.util.HashMap<>();
            for (String key : java.util.List.of("traceparent", "tracestate")) {
                if (invocation.getAttachment(key) != null) headers.put(key, invocation.getAttachment(key));
            }
            builder.setParent(Spans.extract(headers));
        }
        var span = builder.startSpan();
        span.setAttribute("rpc.system", "dubbo");
        span.setAttribute("rpc.service", invoker.getInterface().getName());
        try (var scope = Spans.scope(span)) {
            if (consumer) Spans.inject().forEach(invocation::setAttachment);
            Result result = consumer ? invokeConsumer(invoker, invocation) : invokeProvider(invoker, invocation);
            java.util.function.BiConsumer<Result, Throwable> complete = (completed, error) -> {
                if (error != null) Spans.error(span, error);
                else if (completed != null && completed.hasException()) Spans.error(span, completed.getException());
                span.end();
            };
            if (result instanceof org.apache.dubbo.rpc.AsyncRpcResult) result.whenCompleteWithContext(complete);
            else complete.accept(result, null);
            return result;
        } catch (RuntimeException error) {
            Spans.error(span, error);
            span.end();
            throw error;
        }
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
