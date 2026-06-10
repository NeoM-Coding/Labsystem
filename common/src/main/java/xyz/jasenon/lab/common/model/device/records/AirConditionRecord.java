package xyz.jasenon.lab.common.model.device.records;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import xyz.jasenon.lab.common.model.device.BaseRecord;

@Getter
@Setter
@Builder
@TableName(value = "air_condition_record", autoResultMap = true)
public class AirConditionRecord extends BaseRecord {

    /**
     * 地址
     */
    private int address;
    /**
     * 内机编号
     */
    private int selfId;
    /**
     * 是否开启
     */
    private boolean isOpen;
    /**
     * 模式
     */
    private Mode mode;
    /**
     * 温度
     */
    private int temperature;
    /**
     * 风速
     */
    private Speed speed;
    /**
     * 房间温度
     */
    private int roomTemperature;
    /**
     * 错误码
     */
    private int errorCode;

    public enum Mode {
        Cooling,
        Heating,
        Dehumidification,
        AirSupply,
    }

    public enum Speed {
        Low,
        Middle,
        High,
        Auto
    }

}
