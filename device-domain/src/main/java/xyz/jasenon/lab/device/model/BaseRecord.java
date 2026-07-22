package xyz.jasenon.lab.device.model;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;
import xyz.jasenon.lab.persistence.model.BaseEntity;

@Getter
@Setter
public class BaseRecord extends BaseEntity {

    @TableField(exist = false)
    private Origin origin;

    /**
     * 设备id
     */
    private String deviceId;

    /** 实时事件路由字段，不参与设备记录持久化。 */
    @TableField(exist = false)
    private String laboratoryId;

}
