package xyz.jasenon.lab.common.model.device.device;

import xyz.jasenon.lab.common.DeviceType;
import xyz.jasenon.lab.common.model.device.Device;

public class Access extends Device {
    public Access() {
        this.deviceType = DeviceType.Access;
    }

    /**
     * 门禁地址
     */
    private int address;

    /**
     * rs485网关ID
     */
    private String rs485GatewayId;

    /**
     * 设备当前状态
     */
    private boolean isLock;
}
