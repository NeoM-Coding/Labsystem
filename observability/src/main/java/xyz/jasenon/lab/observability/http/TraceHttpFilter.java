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

public class TraceHttpFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try (TraceContext.Scope ignored = TraceContext.open(
                request.getHeader(TraceContext.TRACE_HEADER),
                request.getHeader(TraceContext.REQUEST_HEADER))) {
            UserContext user = UserContextHolder.get();
            if (user != null) TraceContext.putUser(user.getUserId(), user.getUsername());
            response.setHeader(TraceContext.TRACE_HEADER, TraceContext.traceId());
            response.setHeader(TraceContext.REQUEST_HEADER, TraceContext.requestId());
            chain.doFilter(request, response);
        }
    }
}
