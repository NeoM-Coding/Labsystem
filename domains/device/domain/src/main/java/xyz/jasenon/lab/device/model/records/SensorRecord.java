package xyz.jasenon.lab.device.model.records;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import xyz.jasenon.lab.device.model.BaseRecord;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
