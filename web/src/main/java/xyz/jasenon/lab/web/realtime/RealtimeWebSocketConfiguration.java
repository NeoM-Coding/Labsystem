package xyz.jasenon.lab.web.realtime;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration(proxyBeanMethods = false)
@EnableWebSocket
@EnableConfigurationProperties(RealtimeWebSocketProperties.class)
public class RealtimeWebSocketConfiguration implements WebSocketConfigurer {

    private final RealtimeWebSocketHandler handler;
    private final RealtimeHandshakeInterceptor handshakeInterceptor;
    private final RealtimeWebSocketProperties properties;

    public RealtimeWebSocketConfiguration(RealtimeWebSocketHandler handler,
                                          RealtimeHandshakeInterceptor handshakeInterceptor,
                                          RealtimeWebSocketProperties properties) {
        this.handler = handler;
        this.handshakeInterceptor = handshakeInterceptor;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = properties.getAllowedOrigins().stream()
                .filter(origin -> origin != null && !origin.isBlank() && !"*".equals(origin.trim()))
                .map(String::trim)
                .toArray(String[]::new);
        registry.addHandler(handler, "/ws/events")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins(origins);
    }
}
