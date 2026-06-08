package xyz.jasenon.lab.common.model.device.devices;


import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import xyz.jasenon.lab.common.model.device.Address;
import xyz.jasenon.lab.common.model.device.DeviceType;
import xyz.jasenon.lab.common.model.device.Device;

@Getter
@Setter
@TableName(value = "device",autoResultMap = true)
public class CircuitBreak extends Device implements Address {
    public CircuitBreak() {
        this.deviceType = DeviceType.CircuitBreak;
    }

    /**
     * 电路断路器地址
     */
    private int address;

    public int getAddress() {
        return address;
    }

    public void setAddress(int address) {
        this.address = address;
    }

    @Override
    public int address() {
        return this.address;
    }
}
