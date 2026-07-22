package xyz.jasenon.lab.observability.dubbo;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import xyz.jasenon.lab.observability.context.TraceContext;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class TraceDubboFilterTests {

    private final TraceDubboFilter filter = new TraceDubboFilter();

    @AfterEach
    void cleanUp() {
        MDC.clear();
    }

    @Test
    void consumerWritesCurrentTraceToInvocationAttachments() {
        RpcInvocation invocation = new RpcInvocation();

        try (TraceContext.Scope ignored = TraceContext.open("trace-1", "request-1")) {
            filter.invoke(invoker("consumer", AppResponse::new), invocation);
        }

        assertThat(invocation.getAttachment("trace-id")).isEqualTo("trace-1");
        assertThat(invocation.getAttachment("request-id")).isEqualTo("request-1");
    }

    @Test
    void providerRestoresTraceBeforeCallingRemainingFilterChain() {
        RpcInvocation invocation = new RpcInvocation();
        invocation.setAttachment("trace-id", "trace-1");
        invocation.setAttachment("request-id", "request-1");
        Invoker<Object> provider = invoker("provider", () -> {
            assertThat(TraceContext.traceId()).isEqualTo("trace-1");
            assertThat(TraceContext.requestId()).isEqualTo("request-1");
            return new AppResponse();
        });

        filter.invoke(provider, invocation);

        assertThat(TraceContext.traceId()).isNull();
        assertThat(TraceContext.requestId()).isNull();
    }

    private static Invoker<Object> invoker(String side, Supplier<Result> invocation) {
        return new Invoker<>() {
            @Override
            public Class<Object> getInterface() {
                return Object.class;
            }

            @Override
            public Result invoke(Invocation ignored) {
                return invocation.get();
            }

            @Override
            public URL getUrl() {
                return URL.valueOf("tri://localhost/service?side=" + side);
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public void destroy() {
            }
        };
    }
}
