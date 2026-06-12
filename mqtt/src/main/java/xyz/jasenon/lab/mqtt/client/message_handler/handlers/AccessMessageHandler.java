package xyz.jasenon.lab.mqtt.client.message_handler.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xyz.jasenon.lab.common.model.device.DeviceType;
import xyz.jasenon.lab.common.model.device.records.AccessRecord;
import xyz.jasenon.lab.mqtt.client.message_handler.MessageHandler;
import xyz.jasenon.lab.mqtt.client.message_handler.MessageHandlerManager;
import xyz.jasenon.lab.mqtt.client.message_handler.MessagePersistent;
import xyz.jasenon.lab.redis.core.RedisBus;

@Component
public class AccessMessageHandler extends MessageHandler<AccessRecord> {

    private static final Logger log = LoggerFactory.getLogger(AccessMessageHandler.class);

    public AccessMessageHandler(MessagePersistent<AccessRecord> persistent,
                                RedisBus jedis) {
        super(persistent, jedis, DeviceType.Access);
        MessageHandlerManager.register(this);
        log.info("[AccessMessageHandler] finish register");
    }

    @Override
    protected AccessRecord decode(byte[] payload) {
        int address = payload[0] & 0xff;

        boolean isOpen = payload[3] == (byte) 0xFF;
        boolean isLock = payload[5] == (byte) 0xFF;
        int lockStatus = switch (payload[4]) {
            case (byte) 0xFF -> 1;
            case 0x11 -> 2;
            case 0x00 -> 3;
            default -> 0;
        };
        int delayTime = payload[6] & 0xff;

        return AccessRecord.builder()
                .address(address)
                .opened(isOpen)
                .locked(isLock)
                .lockStatus(lockStatus)
                .delayTime(delayTime)
                .build();
    }

    @Override
    protected void onChange(AccessRecord accessRecord) {
        log.info(
                "[AccessMessageHandler] record changed, device-id:{}, address:{}, open:{}, lock:{}, lock-status:{}, delay-time:{}",
                accessRecord.getDeviceId(),
                accessRecord.getAddress(),
                accessRecord.isOpened(),
                accessRecord.isLocked(),
                accessRecord.getLockStatus(),
                accessRecord.getDelayTime()
        );
    }
}
