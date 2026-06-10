package xyz.jasenon.lab.common.model.device.records;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import xyz.jasenon.lab.common.model.device.BaseRecord;

@Getter
@Setter
@Builder
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
    private boolean isOpen;
    /**
     * 是否锁定
     */
    private boolean isLock;

}
