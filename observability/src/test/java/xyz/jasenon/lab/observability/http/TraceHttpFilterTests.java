package xyz.jasenon.lab.observability.http;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import xyz.jasenon.lab.observability.context.TraceContext;

import static org.assertj.core.api.Assertions.assertThat;

class TraceHttpFilterTests {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void propagatesHeadersAndClearsRequestContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceContext.TRACE_HEADER, "incoming-trace");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new TraceHttpFilter().doFilter(request, response, (req, res) -> {
            assertThat(TraceContext.traceId()).isEqualTo("incoming-trace");
            assertThat(TraceContext.requestId()).isNotBlank();
        });

        assertThat(response.getHeader(TraceContext.TRACE_HEADER)).isEqualTo("incoming-trace");
        assertThat(response.getHeader(TraceContext.REQUEST_HEADER)).isNotBlank();
        assertThat(TraceContext.traceId()).isNull();
    }
}
