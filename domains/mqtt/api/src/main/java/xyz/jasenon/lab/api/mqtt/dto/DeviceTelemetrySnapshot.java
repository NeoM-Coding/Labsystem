package xyz.jasenon.lab.api.mqtt.dto;

import xyz.jasenon.lab.device.model.DeviceType;

import java.io.Serializable;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record DeviceTelemetrySnapshot(
        String deviceId,
        String laboratoryId,
        DeviceType deviceType,
        Map<String, Object> record,
        Instant occurredAt,
        boolean online
) implements Serializable {

    public DeviceTelemetrySnapshot {
        record = record == null ? Map.of() : new LinkedHashMap<>(record);
    }
}
