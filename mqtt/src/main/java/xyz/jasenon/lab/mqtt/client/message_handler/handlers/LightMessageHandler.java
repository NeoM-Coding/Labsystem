package xyz.jasenon.lab.mqtt.client.message_handler.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xyz.jasenon.lab.common.model.device.DeviceType;
import xyz.jasenon.lab.common.model.device.records.LightRecord;
import xyz.jasenon.lab.mqtt.client.message_handler.MessageHandler;
import xyz.jasenon.lab.mqtt.client.message_handler.MessageHandlerManager;
import xyz.jasenon.lab.mqtt.client.message_handler.MessagePersistent;
import xyz.jasenon.lab.redis.core.RedisBus;

@Component
public class LightMessageHandler extends MessageHandler<LightRecord> {
    private static final Logger log = LoggerFactory.getLogger(LightMessageHandler.class);

    public LightMessageHandler(MessagePersistent<LightRecord> persistent,
                               RedisBus jedis) {
        super(persistent, jedis, DeviceType.Light);
        MessageHandlerManager.register(this);
        log.info("[LightMessageHandler] finish register");
    }

    @Override
    protected LightRecord decode(byte[] payload) {
        int address = payload[0] & 0xff;
        int selfId = payload[2] & 0xff;

        boolean isOpen = payload[3] == (byte) 0xff;
        boolean isLock = payload[4] == (byte) 0xff;

        return LightRecord.builder()
                .address(address)
                .selfId(selfId)
                .isOpen(isOpen)
                .isLock(isLock)
                .build();
    }

    @Override
    protected void onChange(LightRecord lightRecord) {
        log.info(
                "[LightMessageHandler] record changed, device-id:{}, address:{}, self-id:{}, open:{}, lock:{}",
                lightRecord.getDeviceId(),
                lightRecord.getAddress(),
                lightRecord.getSelfId(),
                lightRecord.isOpen(),
                lightRecord.isLock()
        );
    }
}
