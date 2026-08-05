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
