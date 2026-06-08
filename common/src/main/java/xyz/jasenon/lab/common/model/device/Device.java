package xyz.jasenon.lab.common.model.device;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import xyz.jasenon.lab.common.model.BaseEntity;
import xyz.jasenon.lab.common.model.device.devices.*;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "deviceType", include = JsonTypeInfo.As.EXISTING_PROPERTY, visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = AirCondition.class, name = "AirCondition"),
        @JsonSubTypes.Type(value = Light.class, name = "Light"),
        @JsonSubTypes.Type(value = Access.class, name = "Access"),
        @JsonSubTypes.Type(value = Sensor.class, name = "Sensor"),
        @JsonSubTypes.Type(value = CircuitBreak.class, name = "CircuitBreak"),
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Device extends BaseEntity {
    // 设备名称
    private String deviceName;
    // 属于
    private String belongTo;
    // 设备类型
    protected DeviceType deviceType;
    // 是否开启轮询
    private boolean polling;
    // 网关id
    private String gatewayId;
}
