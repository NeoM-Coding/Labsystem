package xyz.jasenon.lab.mqtt.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.device.model.devices.Light;
import xyz.jasenon.lab.device.model.gateway.gateways.RS485Gateway;
import xyz.jasenon.lab.mqtt.client.itfc.DeviceHelper;
import xyz.jasenon.lab.mqtt.client.itfc.GatewayHelper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MqttGatewayManagerTests {

    private GatewayHelper gatewayHelper;
    private DeviceHelper deviceHelper;
    private SysClientManager clientManager;
    private MqttGatewayManager manager;

    @BeforeEach
    void setUp() {
        gatewayHelper = mock(GatewayHelper.class);
        deviceHelper = mock(DeviceHelper.class);
        clientManager = mock(SysClientManager.class);
        manager = new MqttGatewayManager(gatewayHelper, deviceHelper, clientManager);
    }

    @Test
    void createPersistsThenRegistersClientImmediately() {
        RS485Gateway gateway = gateway();
        when(gatewayHelper.addRS485Gateway(gateway)).thenAnswer(invocation -> {
            gateway.setId("2001");
            return true;
        });
        when(gatewayHelper.getById("2001")).thenReturn(gateway);

        RS485Gateway created = manager.create(gateway).data();

        assertEquals("2001", created.getId());
        verify(clientManager).registerGateway(gateway);
    }

    @Test
    void deleteRejectsGatewayStillReferencedByDevices() {
        RS485Gateway gateway = gateway();
        gateway.setId("gateway-1");
        when(gatewayHelper.getById("gateway-1")).thenReturn(gateway);
        when(deviceHelper.list("gateway-1", null)).thenReturn(List.of(new Light()));

        BusinessException error = assertThrows(BusinessException.class, () -> manager.delete("gateway-1"));

        assertEquals(409, error.getCode());
        verify(gatewayHelper, never()).removeRS485Gateway("gateway-1");
        verify(clientManager, never()).unregisterGateway("gateway-1");
    }

    private RS485Gateway gateway() {
        RS485Gateway gateway = new RS485Gateway();
        gateway.setGatewayName("mqtt gateway");
        gateway.setSendTopic("lab/send");
        gateway.setAcceptTopic("lab/accept");
        return gateway;
    }
}
