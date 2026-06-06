package xyz.jasenon.lab.common.model.device.device;


import xyz.jasenon.lab.common.DeviceType;
import xyz.jasenon.lab.common.model.device.Device;

public class Light extends Device {
    public Light() {
        this.deviceType = DeviceType.Light;
    }

    /**
     * 灯地址
     */
    private int address;

    /**
     * 地址下灯编号
     */
    private int selfId;

    /**
     * rs485网关ID
     */
    private String rs485GatewayId;

    /**
     * 是否锁定
     */
    private boolean isLock;
}
