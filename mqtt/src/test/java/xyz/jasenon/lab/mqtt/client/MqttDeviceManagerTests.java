package xyz.jasenon.lab.mqtt.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.device.model.Device;
import xyz.jasenon.lab.device.model.devices.Light;
import xyz.jasenon.lab.device.model.gateway.gateways.RS485Gateway;
import xyz.jasenon.lab.mqtt.client.itfc.DeviceHelper;
import xyz.jasenon.lab.mqtt.client.itfc.GatewayHelper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MqttDeviceManagerTests {

    private DeviceHelper deviceHelper;
    private GatewayHelper gatewayHelper;
    private SysPollingManager pollingManager;
    private MqttDeviceManager manager;

    @BeforeEach
    void setUp() {
        deviceHelper = mock(DeviceHelper.class);
        gatewayHelper = mock(GatewayHelper.class);
        pollingManager = mock(SysPollingManager.class);
        when(gatewayHelper.getById("gateway-1")).thenReturn(new RS485Gateway());
        manager = new MqttDeviceManager(deviceHelper, gatewayHelper, pollingManager);
    }

    @Test
    void createPersistsThenRegistersPollingImmediately() {
        Light device = light("gateway-1", true);
        when(deviceHelper.addDevice(device)).thenAnswer(invocation -> {
            device.setId("1001");
            return true;
        });
        when(deviceHelper.getDeviceById("1001")).thenReturn(device);

        Device created = manager.create(device);

        assertEquals("1001", created.getId());
        verify(pollingManager).synchronizeRuntime(null, device);
    }

    @Test
    void updateReplacesOldPollingSnapshot() {
        Light previous = light("gateway-1", true);
        previous.setId("device-1");
        Light current = light("gateway-1", false);
        when(deviceHelper.getDeviceById("device-1")).thenReturn(previous, current);
        when(deviceHelper.updateDevice(current)).thenReturn(true);

        manager.update("device-1", current);

        verify(pollingManager).synchronizeRuntime(previous, current);
    }

    private Light light(String gatewayId, boolean polling) {
        Light light = new Light();
        light.setDeviceName("lab light");
        light.setGatewayId(gatewayId);
        light.setPolling(polling);
        light.setAddress(41);
        light.setSelfId(1);
        return light;
    }
}
