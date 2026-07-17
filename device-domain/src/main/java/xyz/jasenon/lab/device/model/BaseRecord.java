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

}
