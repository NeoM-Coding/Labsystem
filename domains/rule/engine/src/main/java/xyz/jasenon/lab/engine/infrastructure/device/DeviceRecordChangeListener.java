package xyz.jasenon.lab.engine.infrastructure.device;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import xyz.jasenon.lab.device.event.DeviceRecordSnapshotEvent;
import xyz.jasenon.lab.device.event.RuleEngineChannels;
import xyz.jasenon.lab.engine.Engine;
import xyz.jasenon.lab.engine.event.DeviceEvent;
import xyz.jasenon.lab.observability.context.Tracing;
import xyz.jasenon.lab.redis.core.RedisBus;
import xyz.jasenon.lab.redis.core.RedisSubscription;

import java.time.Instant;

@Component
public class DeviceRecordChangeListener {

    private static final Logger log = LoggerFactory.getLogger(DeviceRecordChangeListener.class);

    private final Engine engine;
    private final RedisBus redisBus;
    private final ObjectMapper objectMapper;
    private RedisSubscription subscription;

    public DeviceRecordChangeListener(Engine engine, @Nullable RedisBus redisBus, ObjectMapper objectMapper) {
        this.engine = engine;
        this.redisBus = redisBus;
        this.objectMapper = objectMapper;
    }

    private static void validate(DeviceRecordSnapshotEvent snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        if (snapshot.getDeviceType() == null) {
            throw new IllegalArgumentException("snapshot.deviceType must not be null");
        }
        if (snapshot.getDeviceId() == null || snapshot.getDeviceId().isBlank()) {
            throw new IllegalArgumentException("snapshot.deviceId must not be blank");
        }
        if (snapshot.getRecordFields() == null) {
            throw new IllegalArgumentException("snapshot.recordFields must not be null");
        }
    }

    @PostConstruct
    void subscribe() {
        if (redisBus == null) {
            log.info("[RuleEngine] RedisBus not found, device record listener subscription skipped");
            return;
        }
        subscription = redisBus.subscribe(RuleEngineChannels.DEVICE_RECORD_CHANGE, this::onMessage);
    }

    @PreDestroy
    void close() {
        if (subscription != null) {
            subscription.close();
        }
    }

    public void accept(DeviceRecordSnapshotEvent snapshot) {
        validate(snapshot);
        Tracing.operation("rule.snapshot.consume").consumer(snapshot.getTraceHeaders())
                .attribute("device.id", snapshot.getDeviceId()).attribute("event.id", snapshot.getEventId())
                .run(() -> acceptFields(snapshot));
    }

    private void acceptFields(DeviceRecordSnapshotEvent snapshot) {
        snapshot.getRecordFields().forEach((field, value) -> publishFieldEvent(snapshot, field, value));
    }

    private void onMessage(String channel, String message) {
        try {
            accept(objectMapper.readValue(message, DeviceRecordSnapshotEvent.class));
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.warn("[RuleEngine] ignore invalid device snapshot event, channel:{}, message:{}", channel, message, e);
        }
    }

    private void publishFieldEvent(DeviceRecordSnapshotEvent snapshot, String field, String value) {
        engine.accept(new DeviceEvent(
                snapshot.getDeviceType(),
                snapshot.getDeviceId(),
                field,
                value,
                snapshot.getOccurredAt() == null ? Instant.now() : snapshot.getOccurredAt()
        ));
    }

}
