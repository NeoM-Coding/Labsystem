package xyz.jasenon.lab.common.model.device;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import xyz.jasenon.lab.common.DeviceType;
import xyz.jasenon.lab.common.model.BaseEntity;
import xyz.jasenon.lab.common.model.device.device.*;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "deviceType", include = JsonTypeInfo.As.EXISTING_PROPERTY, visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = AirCondition.class, name = "AirCondition"),
        @JsonSubTypes.Type(value = Light.class, name = "Light"),
        @JsonSubTypes.Type(value = Access.class, name = "Access"),
        @JsonSubTypes.Type(value = Sensor.class, name = "Sensor"),
        @JsonSubTypes.Type(value = CircuitBreak.class, name = "CircuitBreak"),
})
public class Device extends BaseEntity {

    private String deviceName;

    private String belongTo;

    protected DeviceType deviceType;

    private boolean polling;

}
