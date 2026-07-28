package xyz.jasenon.lab.device.model.records;

import com.baomidou.mybatisplus.annotation.TableField;
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
@TableName(value = "light_record",autoResultMap = true)
public class LightRecord extends BaseRecord {
    /**
     * 地址
     */
    private int address;
    /**
     * 自编号
     */
    private int selfId;
    /**
     * 是否开启
     */
    @TableField(value = "is_open")
    private boolean opened;
    /**
     * 是否锁定
     */
    @TableField(value = "is_lock")
    private boolean locked;

}
