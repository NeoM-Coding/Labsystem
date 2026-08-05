package xyz.jasenon.lab.api.mqtt.dto;

import java.io.Serial;
import java.io.Serializable;

public record MqttTaskResultDto(
        String deviceId,
        boolean success,
        MqttResponseDto response,
        String message
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static MqttTaskResultDto success(String deviceId, MqttResponseDto response) {
        return new MqttTaskResultDto(deviceId, true, response, "指令执行成功");
    }

    public static MqttTaskResultDto failure(String deviceId, Throwable error) {
        String message = error == null || error.getMessage() == null
                ? "指令执行失败"
                : error.getMessage();
        return new MqttTaskResultDto(deviceId, false, null, message);
    }
}
