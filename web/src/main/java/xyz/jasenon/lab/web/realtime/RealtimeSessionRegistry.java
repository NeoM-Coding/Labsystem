package xyz.jasenon.lab.web.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.common.realtime.RealtimeAudienceType;
import xyz.jasenon.lab.common.realtime.RealtimeEvent;
import xyz.jasenon.lab.common.realtime.RealtimeMessage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class RealtimeSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(RealtimeSessionRegistry.class);
    private static final CloseStatus TRY_AGAIN_LATER = new CloseStatus(1013, "client cannot keep up");

    private final ObjectMapper objectMapper;
    private final RealtimeWebSocketProperties properties;
    private final Map<String, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();
    private final Map<String, String> userBySession = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> usersByLaboratory = new ConcurrentHashMap<>();
    private final Map<String, UserContext> contextsByUser = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSeenBySession = new ConcurrentHashMap<>();
    private final ExecutorService sender = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "websocket-sender");
        thread.setDaemon(true);
        return thread;
    });

    public RealtimeSessionRegistry(ObjectMapper objectMapper, RealtimeWebSocketProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public WebSocketSession register(WebSocketSession rawSession, String userId, UserContext context) {
        WebSocketSession session = new ConcurrentWebSocketSessionDecorator(
                rawSession,
                properties.getSendTimeLimitMillis(),
                properties.getSendBufferBytes()
        );
        sessionsByUser.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
        userBySession.put(session.getId(), userId);
        lastSeenBySession.put(session.getId(), System.currentTimeMillis());
        replaceContext(userId, context);
        return session;
    }

    public void unregister(String sessionId) {
        String userId = userBySession.remove(sessionId);
        lastSeenBySession.remove(sessionId);
        if (userId == null) return;
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions != null) {
            sessions.removeIf(session -> sessionId.equals(session.getId()));
            if (sessions.isEmpty()) {
                sessionsByUser.remove(userId, sessions);
                removeContextIndexes(userId, contextsByUser.remove(userId));
            }
        }
    }

    public void touch(String sessionId) {
        if (userBySession.containsKey(sessionId)) {
            lastSeenBySession.put(sessionId, System.currentTimeMillis());
        }
    }

    public void replaceContext(String userId, UserContext context) {
        // Redis 通知会到达所有 Web 实例；只维护本实例实际持有连接的用户。
        if (!sessionsByUser.containsKey(userId)) return;
        UserContext previous = contextsByUser.put(userId, context);
        removeContextIndexes(userId, previous);
        if (context.getLaboratoryIds() != null) {
            context.getLaboratoryIds().forEach(laboratoryId ->
                    usersByLaboratory.computeIfAbsent(laboratoryId, ignored -> ConcurrentHashMap.newKeySet())
                            .add(userId));
        }
    }

    public void dispatch(RealtimeMessage message) {
        if (message == null || message.event() == null || message.audienceType() == null) return;
        Set<String> recipients = switch (message.audienceType()) {
            case USER -> new HashSet<>(message.audienceIds());
            case BROADCAST -> new HashSet<>(sessionsByUser.keySet());
            case LABORATORY -> laboratoryRecipients(message.audienceIds());
        };
        recipients.forEach(userId -> {
            if (authorized(userId, message)) send(userId, message.event());
        });
    }

    public void send(String userId, RealtimeEvent event) {
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions == null || sessions.isEmpty()) return;
        final String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.warn("serialize websocket event failed, event-type:{}", event.eventType(), e);
            return;
        }
        sessions.forEach(session -> sender.execute(() -> send(session, json)));
    }

    public void heartbeat() {
        long now = System.currentTimeMillis();
        sessionsByUser.values().stream().flatMap(Collection::stream).forEach(session -> {
            Long lastSeen = lastSeenBySession.get(session.getId());
            if (lastSeen == null || now - lastSeen > properties.getIdleTimeoutMillis()) {
                close(session, CloseStatus.SESSION_NOT_RELIABLE);
                unregister(session.getId());
                return;
            }
            try {
                session.sendMessage(new PingMessage(ByteBuffer.wrap(
                        Long.toString(now).getBytes(StandardCharsets.US_ASCII)
                )));
            } catch (IOException | RuntimeException e) {
                close(session, CloseStatus.SERVER_ERROR);
                unregister(session.getId());
            }
        });
    }

    public void closeUser(String userId, CloseStatus status) {
        Set<WebSocketSession> sessions = sessionsByUser.remove(userId);
        UserContext context = contextsByUser.remove(userId);
        removeContextIndexes(userId, context);
        if (sessions != null) {
            sessions.forEach(session -> {
                userBySession.remove(session.getId());
                lastSeenBySession.remove(session.getId());
                close(session, status);
            });
        }
    }

    private Set<String> laboratoryRecipients(List<String> laboratoryIds) {
        Set<String> recipients = new HashSet<>();
        laboratoryIds.forEach(id -> recipients.addAll(usersByLaboratory.getOrDefault(id, Set.of())));
        return recipients;
    }

    private boolean authorized(String userId, RealtimeMessage message) {
        if (message.audienceType() != RealtimeAudienceType.LABORATORY) return true;
        UserContext context = contextsByUser.get(userId);
        return context != null && message.audienceIds().stream().anyMatch(context::canViewLaboratory);
    }

    private void send(WebSocketSession session, String json) {
        if (!session.isOpen()) {
            unregister(session.getId());
            return;
        }
        try {
            session.sendMessage(new TextMessage(json));
        } catch (IOException | RuntimeException e) {
            log.warn("websocket send failed, session-id:{}", session.getId(), e);
            close(session, TRY_AGAIN_LATER);
            unregister(session.getId());
        }
    }

    private void removeContextIndexes(String userId, UserContext context) {
        if (context == null || context.getLaboratoryIds() == null) return;
        context.getLaboratoryIds().forEach(laboratoryId -> {
            Set<String> users = usersByLaboratory.get(laboratoryId);
            if (users != null) {
                users.remove(userId);
                if (users.isEmpty()) usersByLaboratory.remove(laboratoryId, users);
            }
        });
    }

    private static void close(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) session.close(status);
        } catch (IOException ignored) {
        }
    }

    @PreDestroy
    void close() {
        new HashSet<>(sessionsByUser.keySet()).forEach(userId -> closeUser(userId, CloseStatus.GOING_AWAY));
        sender.shutdownNow();
    }
}
