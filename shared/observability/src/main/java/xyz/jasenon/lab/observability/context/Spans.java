package xyz.jasenon.lab.observability.context;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Map;
import java.util.HashMap;

/** Business spans share the SDK and W3C propagation with transport spans. */
public final class Spans {
    private static volatile OpenTelemetry telemetry = OpenTelemetry.noop();
    private Spans() {}
    public static void install(OpenTelemetry value) {
        telemetry = java.util.Objects.requireNonNull(value);
    }
    static Tracer tracer() { return telemetry.getTracer("lab-system"); }
    public static SpanBuilder builder(String name) { return tracer().spanBuilder(name); }
    public static Span start(String name) {
        return tracer().spanBuilder(name).startSpan();
    }
    public static AutoCloseableScope scope(Span span) {
        return new AutoCloseableScope(span.makeCurrent());
    }
    public static Map<String, String> inject() {
        Map<String, String> headers = new HashMap<>();
        telemetry.getPropagators().getTextMapPropagator()
                .inject(Context.current(), headers, Map::put);
        return headers;
    }
    public static Context extract(Map<String, String> headers) {
        return telemetry.getPropagators().getTextMapPropagator().extract(
                Context.root(), headers == null ? Map.of() : headers, new TextMapGetter<Map<String, String>>() {
                    public Iterable<String> keys(Map<String, String> carrier) { return carrier.keySet(); }
                    public String get(Map<String, String> carrier, String key) { return carrier.get(key); }
                });
    }
    public static void error(Span span, Throwable error) {
        span.recordException(error);
        span.setStatus(StatusCode.ERROR);
    }
    public static final class AutoCloseableScope implements AutoCloseable {
        private final Scope scope;
        private final TraceContext.Scope mdc;
        private AutoCloseableScope(Scope scope) {
            this.scope = scope;
            this.mdc = TraceContext.open(TraceContext.traceId(), TraceContext.requestId());
        }
        public void close() { mdc.close(); scope.close(); }
    }
}
