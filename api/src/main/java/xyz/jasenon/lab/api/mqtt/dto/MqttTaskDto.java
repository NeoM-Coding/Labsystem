package xyz.jasenon.lab.api.mqtt.dto;

import xyz.jasenon.lab.common.model.device.DeviceType;
import xyz.jasenon.lab.common.command.CommandLine;

import java.io.Serializable;

public class MqttTaskDto implements Serializable {

    private CommandLine commandLine;
    private int[] args;
    private DeviceType type;
    private String deviceId;

    public CommandLine getCommandLine() {
        return commandLine;
    }

    public void setCommandLine(CommandLine commandLine) {
        this.commandLine = commandLine;
    }

    public int[] getArgs() {
        return args;
    }

    public void setArgs(int[] args) {
        this.args = args;
    }

    public DeviceType getType() {
        return type;
    }

    public void setType(DeviceType type) {
        this.type = type;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }
}
