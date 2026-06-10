package xyz.jasenon.lab.common.model.device;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;
import xyz.jasenon.lab.common.model.BaseEntity;

@Getter
@Setter
public class BaseRecord extends BaseEntity {

    @TableField(exist = false)
    private Origin origin;

    /**
     * 设备id
     */
    private String deviceId;

}
