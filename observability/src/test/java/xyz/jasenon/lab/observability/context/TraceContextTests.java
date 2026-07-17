package xyz.jasenon.lab.observability.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class TraceContextTests {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void keepsValidIncomingIdsAndRestoresPreviousContext() {
        MDC.put("existing", "value");

        try (TraceContext.Scope ignored = TraceContext.open("trace-123", "request-456")) {
            assertThat(TraceContext.traceId()).isEqualTo("trace-123");
            assertThat(TraceContext.requestId()).isEqualTo("request-456");
        }

        assertThat(MDC.get("existing")).isEqualTo("value");
        assertThat(TraceContext.traceId()).isNull();
    }

    @Test
    void replacesUnsafeIds() {
        try (TraceContext.Scope ignored = TraceContext.open("bad id\n", null)) {
            assertThat(TraceContext.traceId()).matches("[0-9a-f]{32}");
            assertThat(TraceContext.requestId()).matches("[0-9a-f]{24}");
        }
    }
}
