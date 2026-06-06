package xyz.jasenon.lab.common.model.device.device;


import xyz.jasenon.lab.common.DeviceType;
import xyz.jasenon.lab.common.model.device.Device;

public class CircuitBreak extends Device {
    public CircuitBreak() {
        this.deviceType = DeviceType.CircuitBreak;
    }

    /**
     * 电路断路器地址
     */
    private int address;

    /**
     * rs485网关id
     */
    private String rs485GatewayId;

}
