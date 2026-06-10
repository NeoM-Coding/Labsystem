package xyz.jasenon.lab.mqtt.client.message_handler;

import xyz.jasenon.lab.common.Const;
import xyz.jasenon.lab.common.model.device.BaseRecord;
import xyz.jasenon.lab.common.model.device.DeviceType;
import xyz.jasenon.lab.common.util.ObjectMapUtil;
import xyz.jasenon.lab.redis.core.RedisBus;

import java.time.Duration;
import java.util.Map;

public abstract class MessageHandler<R extends BaseRecord> implements Const.Key {

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

        // 落库
        persistent.persist(r);
    }

    // 后端主动通知前端的钩子方法
    protected void onChange(R r){};

}
