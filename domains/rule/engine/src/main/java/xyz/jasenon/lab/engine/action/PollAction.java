package xyz.jasenon.lab.engine.action;

import xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto;
import xyz.jasenon.lab.device.model.DeviceType;
import xyz.jasenon.lab.mqtt.protocol.command.CommandLine;

import java.util.Objects;

/**
 * 用户动作完成后提交的一次性设备查询。
 *
 * <p>它只等待 MQTT 请求成功入队，不等待设备响应；响应仍经由正常的
 * MessageHandler 快照链路回到规则引擎。</p>
 */
public final class PollAction implements Action {

    private final MqttTaskDto query;

    public PollAction(DeviceType deviceType, String deviceId) {
        Objects.requireNonNull(deviceType, "deviceType");
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId must not be blank");
        }
        this.query = MqttTaskDto.of(queryCommand(deviceType), new int[0], deviceType, deviceId);
    }

    public MqttTaskDto query() {
        return query;
    }

    @Override
    public ActionType is() {
        return ActionType.Poll;
    }

    private static CommandLine queryCommand(DeviceType deviceType) {
        return switch (deviceType) {
            case Access -> CommandLine.REQUEST_ACCESS_DATA;
            case AirCondition -> CommandLine.REQUEST_AIR_CONDITION_DATA_RS485;
            case CircuitBreak -> CommandLine.REQUEST_CIRCUITBREAK_DATA;
            case Light -> CommandLine.REQUEST_LIGHT_DATA;
            case Sensor -> CommandLine.REQUEST_SENSOR_DATA;
        };
    }
}
