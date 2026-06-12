package xyz.jasenon.lab.common.model.device.devices;


import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import xyz.jasenon.lab.common.model.device.Address;
import xyz.jasenon.lab.common.model.device.DeviceType;
import xyz.jasenon.lab.common.model.device.Device;
import xyz.jasenon.lab.common.model.device.SelfId;

@Getter
@Setter
@TableName(value = "device",autoResultMap = true)
public class Light extends Device implements Address, SelfId {
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
     * 是否锁定
     */
    @TableField(value = "is_lock")
    private boolean locked;

    @Override
    public int address() {
        return this.address;
    }

    @Override
    public int selfId() {
        return this.selfId;
    }
}
