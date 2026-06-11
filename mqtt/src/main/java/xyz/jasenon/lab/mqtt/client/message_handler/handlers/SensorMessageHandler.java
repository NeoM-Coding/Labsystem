package xyz.jasenon.lab.mqtt.client.message_handler.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xyz.jasenon.lab.common.model.device.DeviceType;
import xyz.jasenon.lab.common.model.device.records.SensorRecord;
import xyz.jasenon.lab.common.util.ByteUtil;
import xyz.jasenon.lab.mqtt.client.message_handler.MessageHandler;
import xyz.jasenon.lab.mqtt.client.message_handler.MessageHandlerManager;
import xyz.jasenon.lab.mqtt.client.message_handler.MessagePersistent;
import xyz.jasenon.lab.redis.core.RedisBus;

@Component
public class SensorMessageHandler extends MessageHandler<SensorRecord> {
    private static final Logger log = LoggerFactory.getLogger(SensorMessageHandler.class);

    public SensorMessageHandler(MessagePersistent<SensorRecord> persistent,
                                RedisBus jedis) {
        super(persistent, jedis, DeviceType.Sensor);
        MessageHandlerManager.register(this);
        log.info("[SensorMessageHandler] finish register");
    }

    @Override
    protected SensorRecord decode(byte[] payload) {
        ByteUtil.requireLength(payload, 13);

        int address = ByteUtil.unsignedByte(payload, 0);
        int selfId = ByteUtil.unsignedByte(payload, 2);
        double temperature = ByteUtil.unsignedBigEndian(payload, 3, 2) / 10.0;
        double humidity = ByteUtil.unsignedBigEndian(payload, 5, 2) / 10.0;
        double light = ByteUtil.unsignedBigEndian(payload, 7, 4) / 10.0;
        int smoke = ByteUtil.unsignedBigEndian(payload, 11, 2);

        return SensorRecord.builder()
                .address(address)
                .selfId(selfId)
                .temperature(temperature)
                .humidity(humidity)
                .light(light)
                .smoke(smoke)
                .build();
    }

    @Override
    protected void onChange(SensorRecord sensorRecord) {
        log.info(
                "[SensorMessageHandler] record changed, device-id:{}, address:{}, self-id:{}, temperature:{}, humidity:{}, light:{}, smoke:{}",
                sensorRecord.getDeviceId(),
                sensorRecord.getAddress(),
                sensorRecord.getSelfId(),
                sensorRecord.getTemperature(),
                sensorRecord.getHumidity(),
                sensorRecord.getLight(),
                sensorRecord.getSmoke()
        );
    }
}
