package xyz.jasenon.lab.observability.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.context.UserContextHolder;
import xyz.jasenon.lab.observability.context.TraceContext;

import java.io.IOException;
import xyz.jasenon.lab.observability.context.Spans;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;

public class TraceHttpFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        java.util.Map<String, String> headers = new java.util.HashMap<>();
        for (String name : java.util.List.of("traceparent", "tracestate")) {
            if (request.getHeader(name) != null) headers.put(name, request.getHeader(name));
        }
        var span = Spans.builder("HTTP " + request.getMethod())
                .setParent(Spans.extract(headers)).setSpanKind(SpanKind.SERVER).startSpan();
        span.setAttribute("http.request.method", request.getMethod());
        try (var active = Spans.scope(span); TraceContext.Scope ignored = TraceContext.open(
                request.getHeader(TraceContext.TRACE_HEADER),
                request.getHeader(TraceContext.REQUEST_HEADER))) {
            UserContext user = UserContextHolder.get();
            if (user != null) TraceContext.putUser(user.getUserId(), user.getUsername());
            response.setHeader(TraceContext.TRACE_HEADER, TraceContext.traceId());
            response.setHeader(TraceContext.REQUEST_HEADER, TraceContext.requestId());
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException error) {
            Spans.error(span, error);
            throw error;
        } finally {
            span.setAttribute("http.response.status_code", response.getStatus());
            if (response.getStatus() >= 500) span.setStatus(StatusCode.ERROR);
            span.end();
        }
    }
}
