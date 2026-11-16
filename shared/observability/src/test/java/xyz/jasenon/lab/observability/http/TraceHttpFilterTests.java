package xyz.jasenon.lab.observability.http;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import xyz.jasenon.lab.observability.context.TraceContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
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

    @Test
    void generatesTraceAndRequestIdsWhenClientDoesNotProvideHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        new TraceHttpFilter().doFilter(request, response, (req, res) -> {
            assertThat(TraceContext.traceId()).matches("[0-9a-f]{32}");
            assertThat(TraceContext.requestId()).matches("[0-9a-f]{24}");
        });

        assertThat(response.getHeader(TraceContext.TRACE_HEADER)).matches("[0-9a-f]{32}");
        assertThat(response.getHeader(TraceContext.REQUEST_HEADER)).matches("[0-9a-f]{24}");
        assertThat(TraceContext.traceId()).isNull();
    }

    @Test
    void replacesInvalidDeveloperProvidedTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceContext.TRACE_HEADER, "invalid trace id with spaces");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new TraceHttpFilter().doFilter(request, response, (req, res) ->
                assertThat(TraceContext.traceId()).matches("[0-9a-f]{32}"));

        assertThat(response.getHeader(TraceContext.TRACE_HEADER))
                .matches("[0-9a-f]{32}")
                .isNotEqualTo("invalid trace id with spaces");
    }
    @ParameterizedTest
    @CsvSource({"200, false", "400, false", "503, true"})
    void logsOneCompletionWithTheServerErrorClassification(int status, boolean serverError, CapturedOutput output)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new TraceHttpFilter().doFilter(new MockHttpServletRequest(), response, (req, res) -> response.setStatus(status));

        assertThat(output.getAll().lines().filter(line -> line.contains("http_request_completed")))
                .singleElement().asString().endsWith("status=" + status + " server_error=" + serverError);
    }

    @Test
    void countsAnEscapingExceptionBeforeTheContainerWritesTheErrorStatus(CapturedOutput output) {
        var failure = new IllegalStateException("request failed");
        assertThatThrownBy(() -> new TraceHttpFilter().doFilter(
                new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> {
                    throw failure;
                })).isSameAs(failure);

        assertThat(output.getAll().lines().filter(line -> line.contains("http_request_completed")))
                .singleElement().asString().endsWith("server_error=true");
    }
}
