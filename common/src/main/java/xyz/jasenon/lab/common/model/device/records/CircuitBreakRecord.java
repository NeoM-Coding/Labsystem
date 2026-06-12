package xyz.jasenon.lab.common.model.device.records;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import xyz.jasenon.lab.common.model.device.BaseRecord;

@Getter
@Setter
@Builder
@TableName(value = "circuit_break_record",autoResultMap = true)
public class CircuitBreakRecord extends BaseRecord {
    /**
     * 地址
     */
    private int address;
    /**
     * 合闸
     */
    @TableField(value = "is_open")
    private boolean opened;
    /**
     * 是否正在维修
     */
    @TableField(value = "is_fix")
    private boolean fixed;
    /**
     * 是否锁定
     */
    @TableField(value = "is_lock")
    private boolean locked;
    /**
     * 电压
     */
    private float voltage;
    /**
     * 电流
     */
    private float current;
    /**
     * 功率
     */
    private float power;
    /**
     * 能耗
     */
    private float energy;
    /**
     * 漏电电流
     */
    private float leakage;
    /**
     * 线温
     */
    private float temperature;

}
