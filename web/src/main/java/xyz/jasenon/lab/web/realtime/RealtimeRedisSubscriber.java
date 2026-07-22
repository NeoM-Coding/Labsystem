package xyz.jasenon.lab.web.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import xyz.jasenon.lab.auth.context.UserContextStore;
import xyz.jasenon.lab.common.realtime.RealtimeChannels;
import xyz.jasenon.lab.common.realtime.RealtimeMessage;
import xyz.jasenon.lab.common.realtime.UserContextChangedEvent;
import xyz.jasenon.lab.redis.core.RedisBus;
import xyz.jasenon.lab.redis.core.RedisSubscription;

@Component
public class RealtimeRedisSubscriber {

    private static final Logger log = LoggerFactory.getLogger(RealtimeRedisSubscriber.class);

    private final RedisBus redisBus;
    private final ObjectMapper objectMapper;
    private final RealtimeSessionRegistry registry;
    private final UserContextStore contextStore;
    private RedisSubscription eventSubscription;
    private RedisSubscription contextSubscription;

    public RealtimeRedisSubscriber(RedisBus redisBus,
                                   ObjectMapper objectMapper,
                                   RealtimeSessionRegistry registry,
                                   UserContextStore contextStore) {
        this.redisBus = redisBus;
        this.objectMapper = objectMapper;
        this.registry = registry;
        this.contextStore = contextStore;
    }

    @PostConstruct
    void subscribe() {
        eventSubscription = redisBus.subscribe(RealtimeChannels.EVENTS, this::onRealtimeEvent);
        contextSubscription = redisBus.subscribe(RealtimeChannels.USER_CONTEXT_CHANGED, this::onContextChanged);
    }

    private void onRealtimeEvent(String channel, String json) {
        try {
            registry.dispatch(objectMapper.readValue(json, RealtimeMessage.class));
        } catch (Exception e) {
            log.warn("ignore invalid realtime event from channel:{}", channel, e);
        }
    }

    private void onContextChanged(String channel, String json) {
        try {
            UserContextChangedEvent changed = objectMapper.readValue(json, UserContextChangedEvent.class);
            if (changed.operation() == UserContextChangedEvent.Operation.DELETE) {
                registry.closeUser(changed.userId(), CloseStatus.POLICY_VIOLATION);
                return;
            }
            contextStore.find(changed.userId()).ifPresentOrElse(
                    context -> registry.replaceContext(changed.userId(), context),
                    () -> registry.closeUser(changed.userId(), CloseStatus.POLICY_VIOLATION)
            );
        } catch (Exception e) {
            log.warn("ignore invalid user context event from channel:{}", channel, e);
        }
    }

    @PreDestroy
    void close() {
        if (eventSubscription != null) eventSubscription.close();
        if (contextSubscription != null) contextSubscription.close();
    }
}
