package xyz.jasenon.lab.common.model.device.records;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import xyz.jasenon.lab.common.model.device.BaseRecord;

@Getter
@Setter
@Builder
@TableName(value = "sensor_record",autoResultMap = true)
public class SensorRecord extends BaseRecord {

    /**
     * 地址
     */
    private int address;

    /**
     * 内编号
     */
    private int selfId;

    /**
     * 温度
     */
    private double temperature;

    /**
     * 湿度
     */
    private double humidity;

    /**
     * 光照强度
     */
    private double light;

    /**
     * 烟雾
     */
    private int smoke;

}
