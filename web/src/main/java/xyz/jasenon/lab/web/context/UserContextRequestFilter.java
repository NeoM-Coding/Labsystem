package xyz.jasenon.lab.web.context;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.context.UserContextHolder;
import xyz.jasenon.lab.auth.context.UserContextStore;

import java.io.IOException;
import java.util.Optional;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UserContextRequestFilter extends OncePerRequestFilter {

    private final CurrentUserIdResolver userIdResolver;
    private final UserContextStore contextStore;

    public UserContextRequestFilter(CurrentUserIdResolver userIdResolver,
                                    UserContextStore contextStore) {
        this.userIdResolver = userIdResolver;
        this.contextStore = contextStore;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Servlet 线程会被复用，入口和 finally 都清理以防上一个请求的身份泄漏。
        UserContextHolder.clear();
        try {
            Optional<String> userId = userIdResolver.currentUserId();
            if (userId.isEmpty()) {
                filterChain.doFilter(request, response);
                return;
            }

            Optional<UserContext> context = contextStore.find(userId.get());
            if (context.isEmpty()) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "登录上下文不存在，请重新登录");
                return;
            }

            UserContextHolder.set(context.get());
            filterChain.doFilter(request, response);
        } finally {
            UserContextHolder.clear();
        }
    }
}
