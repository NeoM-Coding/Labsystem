package xyz.jasenon.lab.mqtt.protocol.command;

import xyz.jasenon.lab.device.model.DeviceType;

import java.util.EnumSet;
import java.util.Set;

public final class MqttCommandPolicy {

    private static final int KEEP = 0xFF;
    private static final Set<Integer> AIR_MODES = Set.of(0x01, 0x02, 0x04, 0x08, KEEP);
    private static final Set<Integer> AIR_SPEEDS = Set.of(0x00, 0x01, 0x02, 0x03, KEEP);

    private MqttCommandPolicy() {
    }

    public static boolean supports(DeviceType deviceType, CommandLine commandLine) {
        if (deviceType == null || commandLine == null) {
            return false;
        }
        return commandsFor(deviceType).contains(commandLine);
    }

    public static Set<CommandLine> commandsFor(DeviceType deviceType) {
        if (deviceType == null) {
            return Set.of();
        }
        return switch (deviceType) {
            case Access -> EnumSet.of(
                    CommandLine.OPEN_ACCESS_ONCE,
                    CommandLine.CLOSE_ACCESS_ONCE,
                    CommandLine.SET_ACCESS_DELAY,
                    CommandLine.REQUEST_ACCESS_DATA
            );
            case AirCondition -> EnumSet.of(
                    CommandLine.OPEN_AIR_CONDITION_RS485,
                    CommandLine.CLOSE_AIR_CONDITION_RS485,
                    CommandLine.ENHANCE_CONTROL_AIR_CONDITION,
                    CommandLine.REQUEST_AIR_CONDITION_DATA_RS485
            );
            case CircuitBreak -> EnumSet.of(
                    CommandLine.OPEN_CIRCUITBREAK,
                    CommandLine.CLOSE_CIRCUITBREAK,
                    CommandLine.REQUEST_CIRCUITBREAK_DATA
            );
            case Light -> EnumSet.of(
                    CommandLine.OPEN_LIGHT,
                    CommandLine.CLOSE_LIGHT,
                    CommandLine.LOCK_LIGHT,
                    CommandLine.UNLOCK_LIGHT,
                    CommandLine.REQUEST_LIGHT_DATA
            );
            case Sensor -> EnumSet.of(CommandLine.REQUEST_SENSOR_DATA);
        };
    }

    public static void validate(DeviceType deviceType, CommandLine commandLine, int[] suppliedArgs) {
        if (!supports(deviceType, commandLine)) {
            throw new IllegalArgumentException("指令与设备类型不匹配");
        }
        int[] args = suppliedArgs == null ? new int[0] : suppliedArgs;
        int expected = expectedArgumentCount(commandLine);
        if (args.length != expected) {
            throw new IllegalArgumentException("指令参数数量错误，需要 " + expected + " 个参数");
        }
        for (int arg : args) {
            if (arg < 0 || arg > 0xFF) {
                throw new IllegalArgumentException("指令参数必须在 0 到 255 之间");
            }
        }
        if (commandLine == CommandLine.ENHANCE_CONTROL_AIR_CONDITION) {
            validateAirConditionArgs(args);
        }
    }

    public static int expectedArgumentCount(CommandLine commandLine) {
        if (commandLine == CommandLine.SET_ACCESS_DELAY) {
            return 1;
        }
        if (commandLine == CommandLine.ENHANCE_CONTROL_AIR_CONDITION) {
            return 4;
        }
        return 0;
    }

    private static void validateAirConditionArgs(int[] args) {
        if (args[0] == KEEP && args[1] == KEEP && args[2] == KEEP && args[3] == KEEP) {
            throw new IllegalArgumentException("增强空调控制至少需要修改一个参数");
        }
        if (args[0] != 0 && args[0] != 1 && args[0] != KEEP) {
            throw new IllegalArgumentException("空调开关参数必须为 0、1 或 255");
        }
        if (!AIR_MODES.contains(args[1])) {
            throw new IllegalArgumentException("空调模式参数不受支持");
        }
        if (args[2] != KEEP && (args[2] < 16 || args[2] > 30)) {
            throw new IllegalArgumentException("空调设定温度必须在 16 到 30°C 之间，或使用 255 保持不变");
        }
        if (!AIR_SPEEDS.contains(args[3])) {
            throw new IllegalArgumentException("空调风速参数不受支持");
        }
    }
}
