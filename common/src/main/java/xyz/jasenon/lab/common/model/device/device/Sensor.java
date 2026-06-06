package xyz.jasenon.lab.common.model.device.device;


import xyz.jasenon.lab.common.DeviceType;
import xyz.jasenon.lab.common.model.device.Device;

public class Sensor extends Device {
    public Sensor() {
        this.deviceType = DeviceType.Sensor;
    }

    /**
     * 传感器地址
     */
    private int address;

    /**
     * 地址下传感器编号
     */
    private int selfId;

    /**
     * rs485网关id
     */
    private String rs485GatewayId;

}
