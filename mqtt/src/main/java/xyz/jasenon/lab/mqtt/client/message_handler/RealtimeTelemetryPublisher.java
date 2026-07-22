package xyz.jasenon.lab.mqtt.client.message_handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xyz.jasenon.lab.common.realtime.RealtimeAudienceType;
import xyz.jasenon.lab.common.realtime.RealtimeChannels;
import xyz.jasenon.lab.common.realtime.RealtimeEvent;
import xyz.jasenon.lab.common.realtime.RealtimeEventTypes;
import xyz.jasenon.lab.common.realtime.RealtimeMessage;
import xyz.jasenon.lab.common.realtime.RealtimeResource;
import xyz.jasenon.lab.device.model.BaseRecord;
import xyz.jasenon.lab.device.model.DeviceType;
import xyz.jasenon.lab.mqtt.config.MqttOptions;
import xyz.jasenon.lab.observability.context.TraceContext;
import xyz.jasenon.lab.redis.core.RedisBus;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
public class RealtimeTelemetryPublisher {

    private static final Logger log = LoggerFactory.getLogger(RealtimeTelemetryPublisher.class);

    private final RedisBus redisBus;
    private final long coalesceWindowMillis;
    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "mqtt-realtime-publisher");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, PendingTelemetry> pending = new ConcurrentHashMap<>();

    public RealtimeTelemetryPublisher(RedisBus redisBus, MqttOptions options) {
        this.redisBus = redisBus;
        this.coalesceWindowMillis = options.getRealtime().getCoalesceWindowMillis();
    }

    public void publish(DeviceType deviceType, BaseRecord record) {
        if (record == null || isBlank(record.getDeviceId()) || isBlank(record.getLaboratoryId())) {
            return;
        }
        String key = record.getDeviceId().trim();
        pending.compute(key, (ignored, current) -> {
            if (current == null) {
                PendingTelemetry created = new PendingTelemetry(deviceType, record, TraceContext.traceId());
                created.future = scheduler.schedule(() -> flush(key), coalesceWindowMillis, TimeUnit.MILLISECONDS);
                return created;
            }
            current.deviceType = deviceType;
            current.record = record;
            current.traceId = TraceContext.traceId();
            return current;
        });
    }

    private void flush(String deviceId) {
        PendingTelemetry telemetry = pending.remove(deviceId);
        if (telemetry == null) return;

        Map<String, Object> data = objectMapper.convertValue(telemetry.record, LinkedHashMap.class);
        data.put("deviceType", telemetry.deviceType.name());
        RealtimeEvent event = new RealtimeEvent(
                RealtimeEvent.CURRENT_VERSION,
                UUID.randomUUID().toString(),
                RealtimeEventTypes.DEVICE_TELEMETRY_UPDATED,
                Instant.now(),
                "mqtt",
                telemetry.traceId,
                new RealtimeResource("device", deviceId, telemetry.record.getLaboratoryId()),
                data
        );
        RealtimeMessage message = new RealtimeMessage(
                RealtimeAudienceType.LABORATORY,
                List.of(telemetry.record.getLaboratoryId()),
                event
        );
        try {
            redisBus.publish(RealtimeChannels.EVENTS, objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("publish realtime telemetry failed, device-id:{}", deviceId, e);
        }
    }

    @PreDestroy
    void close() {
        pending.values().forEach(value -> value.future.cancel(false));
        pending.clear();
        scheduler.shutdownNow();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static final class PendingTelemetry {
        private volatile DeviceType deviceType;
        private volatile BaseRecord record;
        private volatile String traceId;
        private ScheduledFuture<?> future;

        private PendingTelemetry(DeviceType deviceType, BaseRecord record, String traceId) {
            this.deviceType = deviceType;
            this.record = record;
            this.traceId = traceId;
        }
    }
}
