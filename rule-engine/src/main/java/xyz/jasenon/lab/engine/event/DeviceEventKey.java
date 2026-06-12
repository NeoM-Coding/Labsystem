package xyz.jasenon.lab.engine.event;

import xyz.jasenon.lab.common.model.device.DeviceType;

import java.util.Objects;

public final class DeviceEventKey implements EventKey {

    private final DeviceType deviceType;
    private final String deviceId;
    private final String field;

    public DeviceEventKey(DeviceType deviceType, String deviceId, String field) {
        this.deviceType = Objects.requireNonNull(deviceType, "deviceType");
        this.deviceId = requireText(deviceId, "deviceId");
        this.field = requireText(field, "field");
    }

    public DeviceType deviceType() {
        return deviceType;
    }

    public String deviceId() {
        return deviceId;
    }

    public String field() {
        return field;
    }

    @Override
    public EventType type() {
        return EventType.DEVICE;
    }

    @Override
    public String asString() {
        return EventType.DEVICE + ":" + deviceType.name() + ":" + deviceId + ":" + field;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DeviceEventKey that)) {
            return false;
        }
        return deviceType == that.deviceType
                && Objects.equals(deviceId, that.deviceId)
                && Objects.equals(field, that.field);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceType, deviceId, field);
    }

    @Override
    public String toString() {
        return asString();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
