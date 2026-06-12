package xyz.jasenon.lab.mqtt.client.message_handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.jasenon.lab.common.Const;
import xyz.jasenon.lab.common.event.DeviceRecordSnapshotEvent;
import xyz.jasenon.lab.common.event.RuleEngineChannels;
import xyz.jasenon.lab.common.model.device.BaseRecord;
import xyz.jasenon.lab.common.model.device.DeviceType;
import xyz.jasenon.lab.common.util.ObjectMapUtil;
import xyz.jasenon.lab.redis.core.RedisBus;

import java.time.Instant;
import java.time.Duration;
import java.util.Map;

public abstract class MessageHandler<R extends BaseRecord> implements Const.Key {

    private static final Logger log = LoggerFactory.getLogger(MessageHandler.class);
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();

    // 持久化辅助
    private final MessagePersistent<R> persistent;
    private final RedisBus jedis;
    public final DeviceType deviceType;

    public MessageHandler(
            MessagePersistent<R> persistent,
            RedisBus jedis,
            DeviceType deviceType
    ) {
        this.persistent = persistent;
        this.jedis = jedis;
        this.deviceType = deviceType;
    }

    protected abstract R decode(byte[] payload);

    public void persist(String deviceId, byte[] payload){
        R r = decode(payload);
        r.setDeviceId(deviceId);
        onChange(r);

        // 刷redis hmap 为 sqel提供驱动数据
        // 形如 #{root.deviceId.field operator(>,<,==,!=,ext...) any}
        // hget 可以直接提供字段级访问
        Map<String, String> rmap = ObjectMapUtil.toStringMap(r);
        jedis.hsetex(RECORD_KEY(deviceType, deviceId), rmap, Duration.ofSeconds(15));
        publishSnapshot(deviceId, rmap);

        // 落库
        persistent.persist(r);
    }

    // 后端主动通知前端的钩子方法
    protected void onChange(R r){};

    private void publishSnapshot(String deviceId, Map<String, String> recordFields) {
        DeviceRecordSnapshotEvent event = new DeviceRecordSnapshotEvent(
                deviceType,
                deviceId,
                recordFields,
                Instant.now()
        );
        try {
            jedis.publish(RuleEngineChannels.DEVICE_RECORD_CHANGE, OBJECT_MAPPER.writeValueAsString(event));
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("[MessageHandler] publish rule-engine snapshot failed, device-type:{}, device-id:{}", deviceType, deviceId, e);
        }
    }
}
