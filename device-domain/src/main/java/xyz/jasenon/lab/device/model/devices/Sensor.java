package xyz.jasenon.lab.device.model.devices;


import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import xyz.jasenon.lab.device.model.Address;
import xyz.jasenon.lab.device.model.DeviceType;
import xyz.jasenon.lab.device.model.Device;
import xyz.jasenon.lab.device.model.SelfId;

@Getter
@Setter
@TableName(value = "device",autoResultMap = true)
public class Sensor extends Device implements Address, SelfId {
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

    @Override
    public int address() {
        return this.address;
    }

    @Override
    public int selfId() {
        return this.selfId;
    }
}
