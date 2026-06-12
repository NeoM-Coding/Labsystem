package xyz.jasenon.lab.mqtt.client.message_handler.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xyz.jasenon.lab.common.model.device.DeviceType;
import xyz.jasenon.lab.common.model.device.records.AirConditionRecord;
import xyz.jasenon.lab.mqtt.client.message_handler.MessageHandler;
import xyz.jasenon.lab.mqtt.client.message_handler.MessageHandlerManager;
import xyz.jasenon.lab.mqtt.client.message_handler.MessagePersistent;
import xyz.jasenon.lab.redis.core.RedisBus;

@Component
public class AirConditionMessageHandler extends MessageHandler<AirConditionRecord> {
    private static final Logger log = LoggerFactory.getLogger(AirConditionMessageHandler.class);

    public AirConditionMessageHandler(MessagePersistent<AirConditionRecord> persistent,
                                      RedisBus jedis) {
        super(persistent, jedis, DeviceType.AirCondition);
        MessageHandlerManager.register(this);
        log.info("[AirConditionMessageHandler] finish register");
    }

    @Override
    protected AirConditionRecord decode(byte[] payload) {
        int address = payload[0] & 0xFF;
        int selfId = payload[1] & 0xFF;

        boolean isOpen = payload[2] == 0x01;
        AirConditionRecord.Mode mode = switch (payload[3]) {
            case 0x01 -> AirConditionRecord.Mode.Heating;
            case 0x02 -> AirConditionRecord.Mode.Cooling;
            case 0x04 -> AirConditionRecord.Mode.AirSupply;
            case 0x08 -> AirConditionRecord.Mode.Dehumidification;
            default -> null;
        };
        int temperature = payload[4] & 0xFF;
        AirConditionRecord.Speed speed = switch (payload[5]) {
            case 0x00 -> AirConditionRecord.Speed.Auto;
            case 0x01 -> AirConditionRecord.Speed.Low;
            case 0x02 -> AirConditionRecord.Speed.Middle;
            case 0x03 -> AirConditionRecord.Speed.High;
            default -> null;
        };
        int roomTemperature = payload[6] & 0xFF;
        int errorCode = payload[7] & 0xFF;

        return AirConditionRecord.builder()
                .address(address)
                .selfId(selfId)
                .opened(isOpen)
                .mode(mode)
                .temperature(temperature)
                .speed(speed)
                .roomTemperature(roomTemperature)
                .errorCode(errorCode)
                .build();
    }

    @Override
    protected void onChange(AirConditionRecord airConditionRecord){
        log.info(
                "[AirConditionMessageHandler] record changed, device-id:{}, address:{}, self-id:{}, open:{}, mode:{}, speed:{}, temperature:{}, room-temperature:{}, error-code:{}",
                airConditionRecord.getDeviceId(),
                airConditionRecord.getAddress(),
                airConditionRecord.getSelfId(),
                airConditionRecord.isOpened(),
                airConditionRecord.getMode(),
                airConditionRecord.getSpeed(),
                airConditionRecord.getTemperature(),
                airConditionRecord.getRoomTemperature(),
                airConditionRecord.getErrorCode()
        );
    }
}
