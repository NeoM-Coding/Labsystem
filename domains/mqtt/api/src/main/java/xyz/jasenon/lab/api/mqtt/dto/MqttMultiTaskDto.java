package xyz.jasenon.lab.api.mqtt.dto;

import xyz.jasenon.lab.device.model.DeviceType;
import xyz.jasenon.lab.mqtt.protocol.command.CommandLine;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public record MqttMultiTaskDto(
        CommandLine commandLine,
        int[] args,
        DeviceType type,
        List<String> deviceIds
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
