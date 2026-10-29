package xyz.jasenon.lab.mqtt.protocol.command;

import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.device.model.DeviceType;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MqttCommandPolicyTests {

    @Test
    void acceptsCommandsOnlyForTheirDeviceType() {
        assertDoesNotThrow(() -> MqttCommandPolicy.validate(
                DeviceType.Light,
                CommandLine.OPEN_LIGHT,
                new int[0]
        ));
        assertThrows(IllegalArgumentException.class, () -> MqttCommandPolicy.validate(
                DeviceType.Access,
                CommandLine.OPEN_LIGHT,
                new int[0]
        ));
    }

    @Test
    void validatesAccessDelayAsSingleByte() {
        assertDoesNotThrow(() -> MqttCommandPolicy.validate(
                DeviceType.Access,
                CommandLine.SET_ACCESS_DELAY,
                new int[]{255}
        ));
        assertThrows(IllegalArgumentException.class, () -> MqttCommandPolicy.validate(
                DeviceType.Access,
                CommandLine.SET_ACCESS_DELAY,
                new int[]{256}
        ));
    }

    @Test
    void validatesEnhancedAccessAsTwoIndependentThreeStateEnums() {
        for (int valve : new int[]{0, 0x11, 255}) {
            for (int panel : new int[]{0, 0x11, 255}) {
                assertDoesNotThrow(() -> MqttCommandPolicy.validate(
                        DeviceType.Access, CommandLine.ENHANCE_CONTROL_ACCESS, new int[]{valve, panel}));
            }
        }
        for (int[] invalid : new int[][]{ {}, {0}, {0, 255, 0}, {1, 0}, {0, 1}, {11, 255}, {255, 11}, {-1, 0}, {0, 256} }) {
            assertThrows(IllegalArgumentException.class, () -> MqttCommandPolicy.validate(
                    DeviceType.Access, CommandLine.ENHANCE_CONTROL_ACCESS, invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> MqttCommandPolicy.validate(
                DeviceType.Light, CommandLine.ENHANCE_CONTROL_ACCESS, new int[]{0, 255}));
    }

    @Test
    void validatesEnhancedAirConditionArguments() {
        assertDoesNotThrow(() -> MqttCommandPolicy.validate(
                DeviceType.AirCondition,
                CommandLine.ENHANCE_CONTROL_AIR_CONDITION,
                new int[]{1, 2, 24, 0}
        ));
        assertDoesNotThrow(() -> MqttCommandPolicy.validate(
                DeviceType.AirCondition,
                CommandLine.ENHANCE_CONTROL_AIR_CONDITION,
                new int[]{255, 255, 16, 255}
        ));
        assertThrows(IllegalArgumentException.class, () -> MqttCommandPolicy.validate(
                DeviceType.AirCondition,
                CommandLine.ENHANCE_CONTROL_AIR_CONDITION,
                new int[]{255, 255, 15, 255}
        ));
        assertThrows(IllegalArgumentException.class, () -> MqttCommandPolicy.validate(
                DeviceType.AirCondition,
                CommandLine.ENHANCE_CONTROL_AIR_CONDITION,
                new int[]{255, 255, 255, 255}
        ));
    }
}
