package xyz.jasenon.lab.engine.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.lang.Nullable;
import xyz.jasenon.lab.common.event.DeviceRecordSnapshotEvent;
import xyz.jasenon.lab.common.event.RuleEngineChannels;
import xyz.jasenon.lab.common.model.device.DeviceType;
import xyz.jasenon.lab.engine.Engine;
import xyz.jasenon.lab.engine.event.DeviceEvent;
import xyz.jasenon.lab.redis.core.RedisBus;
import xyz.jasenon.lab.redis.core.RedisSubscription;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DeviceRecordChangeListener {

    private static final Logger log = LoggerFactory.getLogger(DeviceRecordChangeListener.class);

    private final Engine engine;
    private final RedisBus redisBus;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<RecordIdentity, Map<String, String>> snapshots = new ConcurrentHashMap<>();
    private RedisSubscription subscription;

    public DeviceRecordChangeListener(Engine engine, @Nullable RedisBus redisBus, ObjectMapper objectMapper) {
        this.engine = engine;
        this.redisBus = redisBus;
        this.objectMapper = objectMapper;
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
        RecordIdentity identity = new RecordIdentity(snapshot.getDeviceType(), snapshot.getDeviceId());
        Map<String, String> current = new LinkedHashMap<>(snapshot.getRecordFields());
        Map<String, String> previous = snapshots.put(identity, current);

        if (previous == null) {
            current.forEach((field, value) -> publishFieldEvent(snapshot, field, value));
            return;
        }

        current.forEach((field, value) -> {
            if (!Objects.equals(previous.get(field), value)) {
                publishFieldEvent(snapshot, field, value);
            }
        });
    }

    int cachedDeviceCount() {
        return snapshots.size();
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

    private record RecordIdentity(DeviceType deviceType, String deviceId) {
    }
}
