package xyz.jasenon.lab.web.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.context.UserContextStore;
import xyz.jasenon.lab.web.context.CurrentUserIdResolver;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealtimeHandshakeInterceptorTests {

    @Test
    void rejectsHandshakeWithoutAuthenticatedUser() {
        CurrentUserIdResolver resolver = mock(CurrentUserIdResolver.class);
        UserContextStore store = mock(UserContextStore.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(resolver.currentUserId()).thenReturn(Optional.empty());
        RealtimeHandshakeInterceptor interceptor = new RealtimeHandshakeInterceptor(resolver, store);

        boolean accepted = interceptor.beforeHandshake(
                mock(ServerHttpRequest.class), response, mock(WebSocketHandler.class), new HashMap<>()
        );

        assertThat(accepted).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void storesValidatedUserContextInHandshakeAttributes() {
        CurrentUserIdResolver resolver = mock(CurrentUserIdResolver.class);
        UserContextStore store = mock(UserContextStore.class);
        UserContext context = UserContext.of("user-1", "admin", "Admin", List.of());
        when(resolver.currentUserId()).thenReturn(Optional.of("user-1"));
        when(store.find("user-1")).thenReturn(Optional.of(context));
        RealtimeHandshakeInterceptor interceptor = new RealtimeHandshakeInterceptor(resolver, store);
        HashMap<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(
                mock(ServerHttpRequest.class), mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class), attributes
        );

        assertThat(accepted).isTrue();
        assertThat(attributes).containsEntry(RealtimeHandshakeInterceptor.USER_ID_ATTRIBUTE, "user-1")
                .containsEntry(RealtimeHandshakeInterceptor.USER_CONTEXT_ATTRIBUTE, context);
    }
}
