package xyz.jasenon.lab.common.model.device.devices;


import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import xyz.jasenon.lab.common.model.device.Address;
import xyz.jasenon.lab.common.model.device.DeviceType;
import xyz.jasenon.lab.common.model.device.Device;
import xyz.jasenon.lab.common.model.device.SelfId;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@TableName(value = "device",autoResultMap = true)
public class AirCondition extends Device implements Address, SelfId {
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

    @Override
    public int address() {
        return this.address;
    }

    @Override
    public int selfId() {
        return this.selfId;
    }
}
