package xyz.jasenon.lab.web.realtime;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.context.UserContextStore;
import xyz.jasenon.lab.web.context.CurrentUserIdResolver;

import java.util.Map;
import java.util.Optional;

@Component
public class RealtimeHandshakeInterceptor implements HandshakeInterceptor {

    static final String USER_ID_ATTRIBUTE = "realtime.userId";
    static final String USER_CONTEXT_ATTRIBUTE = "realtime.userContext";

    private final CurrentUserIdResolver userIdResolver;
    private final UserContextStore contextStore;

    public RealtimeHandshakeInterceptor(CurrentUserIdResolver userIdResolver, UserContextStore contextStore) {
        this.userIdResolver = userIdResolver;
        this.contextStore = contextStore;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        Optional<String> userId = userIdResolver.currentUserId();
        Optional<UserContext> context = userId.flatMap(contextStore::find);
        if (userId.isEmpty() || context.isEmpty() || !userId.get().equals(context.get().getUserId())) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(USER_ID_ATTRIBUTE, userId.get());
        attributes.put(USER_CONTEXT_ATTRIBUTE, context.get());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
    }
}
