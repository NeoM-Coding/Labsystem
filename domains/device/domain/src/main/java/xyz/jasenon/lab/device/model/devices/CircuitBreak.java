package xyz.jasenon.lab.device.model.devices;


import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import xyz.jasenon.lab.device.model.Address;
import xyz.jasenon.lab.device.model.DeviceType;
import xyz.jasenon.lab.device.model.Device;

import java.io.Serial;

@Getter
@Setter
@TableName(value = "device",autoResultMap = true)
public class CircuitBreak extends Device implements Address {

    @Serial
    private static final long serialVersionUID = 1L;

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
