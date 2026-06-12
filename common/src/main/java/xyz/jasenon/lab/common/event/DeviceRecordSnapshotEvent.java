package xyz.jasenon.lab.common.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import xyz.jasenon.lab.common.model.device.DeviceType;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeviceRecordSnapshotEvent {

    private DeviceType deviceType;

    private String deviceId;

    private Map<String, String> recordFields = new LinkedHashMap<>();

    private Instant occurredAt;

}
