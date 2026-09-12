package xyz.jasenon.lab.observability.context;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import org.junit.jupiter.api.*;
import org.slf4j.MDC;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.*;

class TracingTests {
    InMemorySpanExporter exporter;
    OpenTelemetrySdk sdk;
    @BeforeEach void setup() {
        GlobalOpenTelemetry.resetForTest();
        exporter = InMemorySpanExporter.create();
        sdk = OpenTelemetrySdk.builder().setTracerProvider(SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter)).build())
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal();
        Spans.install(sdk);
    }
    @AfterEach void cleanup() { sdk.close(); Spans.install(io.opentelemetry.api.OpenTelemetry.noop()); GlobalOpenTelemetry.resetForTest(); MDC.clear(); }

    @Test void nestedCallsRestoreMdcAndPropagateW3c() {
        MDC.put("trace_id", "outer");
        Tracing.operation("publish").run(() -> {
            String trace = TraceContext.traceId();
            var headers = Tracing.headers();
            Tracing.operation("consume").consumer(headers).run(() ->
                    assertThat(TraceContext.traceId()).isEqualTo(trace));
        });
        assertThat(MDC.get("trace_id")).isEqualTo("outer");
        var spans = exporter.getFinishedSpanItems();
        assertThat(spans).hasSize(2);
        assertThat(spans.get(0).getParentSpanId()).isEqualTo(spans.get(1).getSpanId());
    }
    @Test void callablePreservesCheckedException() {
        IOException failure = new IOException("test");
        assertThatThrownBy(() -> Tracing.operation("call").call(() -> { throw failure; })).isSameAs(failure);
        assertThat(exporter.getFinishedSpanItems().get(0).getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    }
    @Test void asyncEndsOnCompletionAndDoesNotLeakScope() {
        CompletableFuture<String> source = new CompletableFuture<>();
        var result = Tracing.operation("async").async(() -> source);
        assertThat(exporter.getFinishedSpanItems()).isEmpty();
        assertThat(MDC.get("trace_id")).isNull();
        source.complete("ok");
        assertThat(result.toCompletableFuture().join()).isEqualTo("ok");
        assertThat(exporter.getFinishedSpanItems()).hasSize(1);
    }
    @Test void invocationPreservesFutureIdentityAndRecordsCancellation() throws Throwable {
        CompletableFuture<String> source = new CompletableFuture<>();
        assertThat(Tracing.operation("aop").invoke(() -> source)).isSameAs(source);
        source.cancel(false);
        assertThat(exporter.getFinishedSpanItems()).hasSize(1);
        assertThat(exporter.getFinishedSpanItems().get(0).getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    }
    @Test void linksAndExecutorPropagationAreReusable() {
        var captured = Tracing.operation("source").get(Tracing::capture);
        CompletableFuture.runAsync(captured.wrap(() -> Tracing.operation("child").run(() -> {}))).join();
        Tracing.operation("merged").linked(List.of(captured)).run(() -> {});
        var spans = exporter.getFinishedSpanItems();
        assertThat(spans.get(1).getParentSpanId()).isEqualTo(spans.get(0).getSpanId());
        assertThat(spans.get(2).getLinks().get(0).getSpanContext().getSpanId()).isEqualTo(spans.get(0).getSpanId());
        assertThat(MDC.get("trace_id")).isNull();
    }
    @Test void capturedMdcRestoresWorkerContextEvenOnFailure() {
        MDC.put("user_id", "caller");
        MDC.put("request_id", "request-1");
        var captured = Tracing.capture();
        MDC.put("user_id", "worker");
        assertThatThrownBy(() -> captured.wrap(() -> {
            assertThat(MDC.get("user_id")).isEqualTo("caller");
            assertThat(MDC.get("request_id")).isEqualTo("request-1");
            throw new IllegalStateException("failure");
        }).run()).isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get("user_id")).isEqualTo("worker");
    }
}
