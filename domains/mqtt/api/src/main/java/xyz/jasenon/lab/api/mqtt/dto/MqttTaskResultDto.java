package xyz.jasenon.lab.api.mqtt.dto;

import xyz.jasenon.lab.common.error.ErrorCode;
import java.io.Serial;
import java.io.Serializable;

public record MqttTaskResultDto(
        String deviceId,
        boolean success,
        MqttResponseDto response,
        String message,
        int code
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static MqttTaskResultDto success(String deviceId, MqttResponseDto response) {
        return new MqttTaskResultDto(deviceId, true, response, "指令执行成功", 200);
    }

    public static MqttTaskResultDto failure(String deviceId, Throwable error) {
        var code = error instanceof java.util.concurrent.TimeoutException
                ? ErrorCode.MQTT_DEVICE_TIMEOUT
                : error instanceof InterruptedException
                ? ErrorCode.MQTT_REQUEST_INTERRUPTED
                : ErrorCode.INTERNAL_ERROR;
        if (error instanceof xyz.jasenon.lab.common.exception.BusinessException business) {
            return new MqttTaskResultDto(deviceId, false, null, business.getMessage(), business.getCode());
        }
        return new MqttTaskResultDto(deviceId, false, null, code.message(), code.code());
    }
}
