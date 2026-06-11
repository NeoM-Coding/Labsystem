package xyz.jasenon.lab.mqtt.client.message_handler.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xyz.jasenon.lab.common.model.device.DeviceType;
import xyz.jasenon.lab.common.model.device.records.CircuitBreakRecord;
import xyz.jasenon.lab.common.util.ByteUtil;
import xyz.jasenon.lab.mqtt.client.message_handler.MessageHandler;
import xyz.jasenon.lab.mqtt.client.message_handler.MessageHandlerManager;
import xyz.jasenon.lab.mqtt.client.message_handler.MessagePersistent;
import xyz.jasenon.lab.redis.core.RedisBus;

@Component
public class CircuitBreakMessageHandler extends MessageHandler<CircuitBreakRecord> {
    private static final Logger log = LoggerFactory.getLogger(CircuitBreakMessageHandler.class);

    public CircuitBreakMessageHandler(MessagePersistent<CircuitBreakRecord> persistent,
                                      RedisBus jedis) {
        super(persistent, jedis, DeviceType.CircuitBreak);
        MessageHandlerManager.register(this);
        log.info("[CircuitBreakMessageHandler] finish register");
    }

    @Override
    protected CircuitBreakRecord decode(byte[] payload) {
        ByteUtil.requireLength(payload, 219);

        int address = ByteUtil.unsignedByte(payload, 0);
        boolean isFix = ByteUtil.bit(payload[3], 0);
        boolean isOpen = ByteUtil.bit(payload[4], 0);
        boolean isLock = ByteUtil.bit(payload[4], 1);

        float leakage = ByteUtil.littleEndianFloat(payload, 7);
        float temperature = ByteUtil.littleEndianFloat(payload, 11);
        float voltage = ByteUtil.littleEndianFloat(payload, 55);
        float current = ByteUtil.littleEndianFloat(payload, 119);
        float power = ByteUtil.littleEndianFloat(payload, 151);
        float energy = ByteUtil.littleEndianFloat(payload, 215);

        return CircuitBreakRecord.builder()
                .address(address)
                .current(current)
                .energy(energy)
                .isFix(isFix)
                .isLock(isLock)
                .isOpen(isOpen)
                .leakage(leakage)
                .power(power)
                .temperature(temperature)
                .voltage(voltage)
                .build();
    }

    @Override
    protected void onChange(CircuitBreakRecord circuitBreakRecord) {
        log.info(
                "[CircuitBreakMessageHandler] record changed, device-id:{}, address:{}, open:{}, fix:{}, lock:{}, voltage:{}, current:{}, power:{}, energy:{}, leakage:{}, temperature:{}",
                circuitBreakRecord.getDeviceId(),
                circuitBreakRecord.getAddress(),
                circuitBreakRecord.isOpen(),
                circuitBreakRecord.isFix(),
                circuitBreakRecord.isLock(),
                circuitBreakRecord.getVoltage(),
                circuitBreakRecord.getCurrent(),
                circuitBreakRecord.getPower(),
                circuitBreakRecord.getEnergy(),
                circuitBreakRecord.getLeakage(),
                circuitBreakRecord.getTemperature()
        );
    }
}
