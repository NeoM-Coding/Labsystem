package xyz.jasenon.lab.mqtt.client.message_handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.jasenon.lab.device.event.DeviceRecordSnapshotEvent;
import xyz.jasenon.lab.device.event.RuleEngineChannels;
import xyz.jasenon.lab.device.model.BaseRecord;
import xyz.jasenon.lab.device.model.DeviceRecordKeys;
import xyz.jasenon.lab.device.model.DeviceType;
import xyz.jasenon.lab.common.util.ObjectMapUtil;
import xyz.jasenon.lab.redis.core.RedisBus;

import java.time.Instant;
import java.time.Duration;
import java.util.Map;
import java.util.LinkedHashMap;

public abstract class MessageHandler<R extends BaseRecord> {

    private static final Logger log = LoggerFactory.getLogger(MessageHandler.class);
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();

    // 持久化辅助
    private final MessagePersistent<R> persistent;
    private final RedisBus jedis;
    private final RealtimeTelemetryPublisher realtimePublisher;
    public final DeviceType deviceType;

    public MessageHandler(
            MessagePersistent<R> persistent,
            RedisBus jedis,
            DeviceType deviceType
    ) {
        this(persistent, jedis, deviceType, null);
    }

    public MessageHandler(MessagePersistent<R> persistent,
                          RedisBus jedis,
                          DeviceType deviceType,
                          RealtimeTelemetryPublisher realtimePublisher) {
        this.persistent = persistent;
        this.jedis = jedis;
        this.deviceType = deviceType;
        this.realtimePublisher = realtimePublisher;
    }

    protected abstract R decode(byte[] payload);

    public void persist(String deviceId, byte[] payload){
        persist(deviceId, null, payload);
    }

    public void persist(String deviceId, String laboratoryId, byte[] payload){
        R r = decode(payload);
        r.setDeviceId(deviceId);
        r.setLaboratoryId(laboratoryId);
        onChange(r);

        // 刷 redis hash 用于快速获取对应字段 统计用
        // 同时 redis 数据 expire 也可以作为设备在线判断
        Instant occurredAt = Instant.now();
        Map<String, String> recordFields = ObjectMapUtil.toStringMap(r);
        Map<String, String> redisFields = new LinkedHashMap<>(recordFields);
        redisFields.put("__occurredAt", occurredAt.toString());
        jedis.hsetex(DeviceRecordKeys.recordKey(deviceType, deviceId), redisFields, Duration.ofSeconds(15));
        publishSnapshot(deviceId, recordFields, occurredAt);

        // 落库
        persistent.persist(r);
    }

    /**
     * Hook for pushing the latest record to application clients, such as WebSocket subscribers.
     * This method runs for every decoded record, so the default implementation intentionally
     * performs no logging.
     */
    protected void onChange(R record) {
        if (realtimePublisher != null) {
            realtimePublisher.publish(deviceType, record);
        }
    }

    private void publishSnapshot(String deviceId, Map<String, String> recordFields, Instant occurredAt) {
        DeviceRecordSnapshotEvent event = new DeviceRecordSnapshotEvent(
                deviceType,
                deviceId,
                recordFields,
                occurredAt
        );
        try {
            jedis.publish(RuleEngineChannels.DEVICE_RECORD_CHANGE, OBJECT_MAPPER.writeValueAsString(event));
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("[MessageHandler] publish rule-engine snapshot failed, device-type:{}, device-id:{}", deviceType, deviceId, e);
        }
    }
}
