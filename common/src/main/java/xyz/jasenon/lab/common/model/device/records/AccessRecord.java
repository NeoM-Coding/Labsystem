package xyz.jasenon.lab.common.model.device.records;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import xyz.jasenon.lab.common.model.device.BaseRecord;

@Getter
@Setter
@Builder
@TableName(value = "access_record", autoResultMap = true)
public class AccessRecord extends BaseRecord {

    /**
     * 地址
     */
    private int address;
    /**
     * 是否关门
     */
    private boolean isOpen;
    /**
     * 是否锁定门锁开关
     */
    private boolean isLock;
    /**
     * 门锁锁定状态
     */
    private int lockStatus;
    /**
     * 延迟关门时间
     */
    private int delayTime;

}
