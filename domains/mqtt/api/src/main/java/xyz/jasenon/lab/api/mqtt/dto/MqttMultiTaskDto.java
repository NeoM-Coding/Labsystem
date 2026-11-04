package xyz.jasenon.lab.api.mqtt.dto;

import xyz.jasenon.lab.audit.api.Loggable;
import xyz.jasenon.lab.device.model.DeviceType;
import xyz.jasenon.lab.mqtt.protocol.command.CommandLine;

import java.io.Serial;
import java.util.Arrays;
import java.util.List;

public record MqttMultiTaskDto(
        CommandLine commandLine,
        int[] args,
        DeviceType type,
        List<String> deviceIds
) implements Loggable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String log() {
        return "批量发送MQTT任务：设备「" + deviceIds + "」，类型「" + type
                + "」，指令「" + (commandLine == null ? "未指定" : commandLine.getDescription())
                + "」，参数" + Arrays.toString(args);
    }
}
