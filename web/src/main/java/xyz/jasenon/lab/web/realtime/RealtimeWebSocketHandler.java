package xyz.jasenon.lab.web.realtime;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.common.realtime.RealtimeEvent;
import xyz.jasenon.lab.common.realtime.RealtimeEventTypes;
import xyz.jasenon.lab.common.realtime.RealtimeResource;
import xyz.jasenon.lab.observability.context.TraceContext;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class RealtimeWebSocketHandler extends TextWebSocketHandler {

    private final RealtimeSessionRegistry registry;

    public RealtimeWebSocketHandler(RealtimeSessionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = (String) session.getAttributes().get(RealtimeHandshakeInterceptor.USER_ID_ATTRIBUTE);
        UserContext context = (UserContext) session.getAttributes()
                .get(RealtimeHandshakeInterceptor.USER_CONTEXT_ATTRIBUTE);
        WebSocketSession registered = registry.register(session, userId, context);
        registry.send(userId, new RealtimeEvent(
                RealtimeEvent.CURRENT_VERSION,
                UUID.randomUUID().toString(),
                RealtimeEventTypes.SYSTEM_CONNECTED,
                Instant.now(),
                "web",
                TraceContext.traceId(),
                new RealtimeResource("user", userId, null),
                Map.of(
                        "userId", userId,
                        "connectionId", registered.getId(),
                        "protocolVersion", RealtimeEvent.CURRENT_VERSION
                )
        ));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 当前协议仅支持服务端推送；收到文本只更新活跃时间，不接受订阅范围变更。
        registry.touch(session.getId());
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        registry.touch(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        registry.unregister(session.getId());
        if (session.isOpen()) session.close(CloseStatus.SERVER_ERROR);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.unregister(session.getId());
    }
}
