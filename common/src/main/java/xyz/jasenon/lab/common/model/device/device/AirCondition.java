package xyz.jasenon.lab.common.model.device.device;


import xyz.jasenon.lab.common.DeviceType;
import xyz.jasenon.lab.common.model.device.Device;

import java.util.UUID;


public class AirCondition extends Device {
    public AirCondition() {
        this.deviceType = DeviceType.AirCondition;
    }

    /**
     * 空调地址
     */
    private int address;

    /**
     * 地址下空调编号 
     */
    private int selfId;

    /**
     * rs485网关id
     */
    private String rs485GatewayId;

    /**
     * socket网关id
     */
    private String socketGatewayId;

    /**
     * 机组id
     */
    private String groupId = UUID.randomUUID().toString();

    /**
     * 设备当前状态
     */
    private boolean isLock;
}
