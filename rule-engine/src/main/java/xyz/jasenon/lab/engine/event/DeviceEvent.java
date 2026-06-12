package xyz.jasenon.lab.engine.event;

import lombok.Getter;
import xyz.jasenon.lab.common.model.device.DeviceType;

import java.time.Instant;
import java.util.Objects;

@Getter
public class DeviceEvent {

    private final DeviceEventKey key;
    private final String value;
    private final Instant occurredAt;

    public DeviceEvent(DeviceType deviceType, String deviceId, String field, String value, Instant occurredAt) {
        this.key = new DeviceEventKey(deviceType, deviceId, field);
        this.value = value;
        this.occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    public DeviceType getDeviceType() {
        return key.deviceType();
    }

    public String getDeviceId() {
        return key.deviceId();
    }

    public String getField() {
        return key.field();
    }

    public EventKey eventKey() {
        return key;
    }

    @Override
    public String toString() {
        return "DeviceEvent{" +
                "key=" + key +
                ", value='" + Objects.toString(value) + '\'' +
                ", occurredAt=" + occurredAt +
                '}';
    }
}
