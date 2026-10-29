package xyz.jasenon.lab.mqtt.client.itfc.impl;

import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.device.model.DeviceType;
import xyz.jasenon.lab.device.model.devices.AirCondition;
import xyz.jasenon.lab.device.model.devices.Access;
import xyz.jasenon.lab.mqtt.client.itfc.mapper.DeviceMapper;
import xyz.jasenon.lab.mqtt.client.mqtt.MqttTask;
import xyz.jasenon.lab.mqtt.protocol.command.CommandLine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class MqttTaskHelperTests {

    private final MqttTaskHelper helper = new MqttTaskHelper(mock(DeviceMapper.class));

    @Test
    void prependsDeviceAddressAndSelfIdBeforeClientArguments() {
        AirCondition device = airCondition();
        MqttTask task = helper.help(device, MqttTaskDto.of(
                CommandLine.ENHANCE_CONTROL_AIR_CONDITION,
                new int[]{255, 255, 24, 255},
                DeviceType.AirCondition,
                "air-1"
        ));

        assertArrayEquals(new int[]{2, 1, 255, 255, 24, 255}, task.getArgs());
        assertEquals("gateway-1", task.getGatewayId());
        assertEquals("laboratory-1", task.getLaboratoryId());
    }

    @Test
    void encodesEnhancedAccessValveThenPanelAndUnsignedChecksum() {
        Access device = new Access();
        device.setAddress(2);
        device.setGatewayId("gateway-1");
        device.setBelongTo("laboratory-1");
        for (int valve : new int[]{0, 0x11, 255}) {
            for (int panel : new int[]{0, 0x11, 255}) {
                MqttTask task = helper.help(device, MqttTaskDto.of(CommandLine.ENHANCE_CONTROL_ACCESS,
                        new int[]{valve, panel}, DeviceType.Access, "access-1"));
                assertArrayEquals(new int[]{2, valve, panel}, task.getArgs());
                byte checksum = (byte) ((2 + 0x0A + 1 + valve + panel) % 0xFF);
                assertArrayEquals(new byte[]{2, 0x0A, 1, (byte) valve, (byte) panel, 0, checksum},
                        task.convert().getPayload());
                assertEquals("gateway-1", task.getGatewayId());
            }
        }
        assertThrows(BusinessException.class, () -> helper.help(device, MqttTaskDto.of(
                CommandLine.ENHANCE_CONTROL_ACCESS, new int[]{1, 255}, DeviceType.Access, "access-1")));
    }

    @Test
    void rejectsClientDeviceTypeThatDiffersFromPersistedDevice() {
        assertThrows(BusinessException.class, () -> helper.help(airCondition(), MqttTaskDto.of(
                CommandLine.OPEN_LIGHT,
                new int[0],
                DeviceType.Light,
                "air-1"
        )));
    }

    private static AirCondition airCondition() {
        AirCondition device = new AirCondition();
        device.setAddress(2);
        device.setSelfId(1);
        device.setGatewayId("gateway-1");
        device.setBelongTo("laboratory-1");
        return device;
    }
}
