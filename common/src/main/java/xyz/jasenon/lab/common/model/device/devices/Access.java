package xyz.jasenon.lab.common.model.device.devices;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import xyz.jasenon.lab.common.model.device.Address;
import xyz.jasenon.lab.common.model.device.DeviceType;
import xyz.jasenon.lab.common.model.device.Device;

@AllArgsConstructor
@TableName(value = "device",autoResultMap = true)
public class Access extends Device implements Address {
    public Access() {
        this.deviceType = DeviceType.Access;
    }

    /**
     * 门禁地址
     */
    private int address;

    /**
     * 设备当前状态
     */
    @TableField(value = "is_lock")
    private boolean locked;


    public int getAddress() {
        return address;
    }

    public void setAddress(int address) {
        this.address = address;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    @Override
    public int address() {
        return this.address;
    }
}
