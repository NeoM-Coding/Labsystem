package xyz.jasenon.lab.web.realtime;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.common.realtime.RealtimeAudienceType;
import xyz.jasenon.lab.common.realtime.RealtimeEvent;
import xyz.jasenon.lab.common.realtime.RealtimeMessage;
import xyz.jasenon.lab.common.realtime.RealtimeResource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealtimeSessionRegistryTests {

    private final RealtimeSessionRegistry registry = new RealtimeSessionRegistry(
            JsonMapper.builder().findAndAddModules().build(),
            new RealtimeWebSocketProperties()
    );

    @AfterEach
    void tearDown() {
        registry.close();
    }

    @Test
    void sendsLaboratoryEventOnlyToUsersWithinViewScope() throws Exception {
        WebSocketSession allowed = session("session-1");
        WebSocketSession denied = session("session-2");
        registry.register(allowed, "user-1", context("user-1", "lab-1"));
        registry.register(denied, "user-2", context("user-2", "lab-2"));

        registry.dispatch(message("lab-1"));

        verify(allowed, timeout(500)).sendMessage(any(TextMessage.class));
        verify(denied, after(150).never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void sendsToEveryLocalSessionOwnedByUser() throws Exception {
        WebSocketSession first = session("session-1");
        WebSocketSession second = session("session-2");
        UserContext context = context("user-1", "lab-1");
        registry.register(first, "user-1", context);
        registry.register(second, "user-1", context);

        registry.dispatch(message("lab-1"));

        verify(first, timeout(500)).sendMessage(any(TextMessage.class));
        verify(second, timeout(500)).sendMessage(any(TextMessage.class));
    }

    @Test
    void rebuildingContextRemovesPreviousLaboratoryIndex() throws Exception {
        WebSocketSession session = session("session-1");
        registry.register(session, "user-1", context("user-1", "lab-1"));
        registry.replaceContext("user-1", context("user-1", "lab-2"));

        registry.dispatch(message("lab-1"));

        verify(session, after(150).never()).sendMessage(any(TextMessage.class));
    }

    private static WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private static UserContext context(String userId, String laboratoryId) {
        return UserContext.of(userId, userId, userId, List.of(laboratoryId), List.of());
    }

    private static RealtimeMessage message(String laboratoryId) {
        RealtimeEvent event = new RealtimeEvent(
                "1.0", "event-1", "device.telemetry.updated", Instant.now(), "mqtt", null,
                new RealtimeResource("device", "device-1", laboratoryId), Map.of("value", 1)
        );
        return new RealtimeMessage(RealtimeAudienceType.LABORATORY, List.of(laboratoryId), event);
    }
}
