package xyz.jasenon.lab.observability.context;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import org.slf4j.MDC;

/** Reusable boundary instrumentation; business code need not manage spans or scopes. */
public final class Tracing {
    private Tracing() {}
    @FunctionalInterface
    public interface Invocation<T> { T proceed() throws Throwable; }

    public static Operation operation(String name) { return new Operation(name); }
    public static Propagation capture() { return new Propagation(Context.current()); }
    public static Map<String, String> headers() { return Spans.inject(); }
    public static void failure(Throwable error) { Spans.error(Span.current(), error); }
    public static void attribute(String key, String value) {
        if (value != null) Span.current().setAttribute(key, value);
    }
    public static void attribute(String key, boolean value) { Span.current().setAttribute(key, value); }
    public static void attribute(String key, long value) { Span.current().setAttribute(key, value); }

    /** Opaque context carrier, safe to capture on one thread and restore on another. */
    public static final class Propagation {
        private final Context context;
        private final Map<String, String> diagnosticContext;
        private Propagation(Context context) {
            this.context = context;
            this.diagnosticContext = MDC.getCopyOfContextMap();
        }
        public boolean valid() { return Span.fromContext(context).getSpanContext().isValid(); }
        public Runnable wrap(Runnable task) { return () -> get(() -> { task.run(); return null; }); }
        public <T> T get(Supplier<T> task) {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try (var scope = context.makeCurrent()) {
                restore(diagnosticContext);
                try (var mdc = TraceContext.open(TraceContext.traceId(), TraceContext.requestId())) {
                    return task.get();
                }
            } finally {
                restore(previous);
            }
        }
        private static void restore(Map<String, String> values) {
            if (values == null) MDC.clear();
            else MDC.setContextMap(values);
        }
    }

    /** Create a fresh operation per invocation; configuration is local to the boundary. */
    public static final class Operation {
        private final String name;
        private Context parent;
        private boolean root;
        private SpanKind kind = SpanKind.INTERNAL;
        private final Map<String, Object> attributes = new LinkedHashMap<>();
        private final List<Propagation> links = new ArrayList<>();
        private Operation(String name) { this.name = Objects.requireNonNull(name); }
        public Operation attribute(String key, String value) { if (value != null) attributes.put(key, value); return this; }
        public Operation attribute(String key, long value) { attributes.put(key, value); return this; }
        public Operation attribute(String key, boolean value) { attributes.put(key, value); return this; }
        public Operation consumer(Map<String, String> headers) {
            parent = Spans.extract(headers); kind = SpanKind.CONSUMER; return this;
        }
        public Operation producer() { kind = SpanKind.PRODUCER; return this; }
        public Operation linked(Collection<Propagation> causes) {
            root = true; links.addAll(causes); return this;
        }
        private Span start() {
            var builder = Spans.tracer().spanBuilder(name).setSpanKind(kind);
            if (root) builder.setNoParent();
            else if (parent != null) builder.setParent(parent);
            links.stream().filter(Propagation::valid).limit(128)
                    .forEach(link -> builder.addLink(Span.fromContext(link.context).getSpanContext()));
            Span span = builder.startSpan();
            attributes.forEach((key, value) -> {
                if (value instanceof Long number) span.setAttribute(key, number);
                else if (value instanceof Boolean flag) span.setAttribute(key, flag);
                else span.setAttribute(key, value.toString());
            });
            return span;
        }
        public void run(Runnable task) { get(() -> { task.run(); return null; }); }
        /** AOP entry point: preserves the returned object and checked exception identity. */
        public <T> T invoke(Invocation<T> task) throws Throwable {
            Span span = start();
            boolean deferred = false;
            try (var scope = Spans.scope(span)) {
                T result = task.proceed();
                if (result instanceof CompletionStage<?> stage) {
                    stage.whenComplete((value, error) -> {
                        try (var active = Spans.scope(span)) {
                            if (error != null) Spans.error(span, error);
                        } finally { span.end(); }
                    });
                    deferred = true;
                }
                return result;
            } catch (Throwable error) { Spans.error(span, error); throw error; }
            finally { if (!deferred) span.end(); }
        }
        public <T> T get(Supplier<T> task) {
            Span span = start();
            try (var scope = Spans.scope(span)) { return task.get(); }
            catch (RuntimeException | Error error) { Spans.error(span, error); throw error; }
            finally { span.end(); }
        }
        public <T> T call(Callable<T> task) throws Exception {
            Span span = start();
            try (var scope = Spans.scope(span)) { return task.call(); }
            catch (Exception | Error error) { Spans.error(span, error); throw error; }
            finally { span.end(); }
        }
        /** Scope closes on the caller thread; span ends only when the stage terminates. */
        public <T> CompletionStage<T> async(Supplier<? extends CompletionStage<T>> task) {
            Span span = start();
            try (var scope = Spans.scope(span)) {
                CompletionStage<T> stage = Objects.requireNonNull(task.get(), "async task returned null");
                return stage.whenComplete((result, error) -> {
                    try (var completionScope = Spans.scope(span)) {
                        if (error != null) Spans.error(span, error);
                    } finally { span.end(); }
                });
            } catch (RuntimeException | Error error) {
                Spans.error(span, error); span.end(); throw error;
            }
        }
    }
}
