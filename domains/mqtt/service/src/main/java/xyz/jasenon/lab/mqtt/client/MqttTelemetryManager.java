package xyz.jasenon.lab.mqtt.client;

import org.apache.dubbo.config.annotation.DubboService;
import xyz.jasenon.lab.api.mqtt.MqttTelemetryQuery;
import xyz.jasenon.lab.api.mqtt.dto.DeviceTelemetrySnapshot;
import xyz.jasenon.lab.common.util.ObjectMapUtil;
import xyz.jasenon.lab.common.rpc.RpcResult;
import xyz.jasenon.lab.device.model.BaseRecord;
import xyz.jasenon.lab.device.model.Device;
import xyz.jasenon.lab.device.model.DeviceRecordKeys;
import xyz.jasenon.lab.device.model.DeviceType;
import xyz.jasenon.lab.mqtt.client.itfc.DeviceHelper;
import xyz.jasenon.lab.mqtt.client.itfc.mapper.LatestDeviceRecordMapper;
import xyz.jasenon.lab.observability.annotation.Traced;
import xyz.jasenon.lab.redis.core.RedisBus;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@DubboService
@Traced("mqtt-telemetry-query")
public class MqttTelemetryManager implements MqttTelemetryQuery {

    private static final String OCCURRED_AT_FIELD = "__occurredAt";
    private final DeviceHelper deviceHelper;
    private final LatestDeviceRecordMapper latestRecordMapper;
    private final VisibleLaboratoryScope visibleLaboratoryScope;
    private final RedisBus redisBus;

    public MqttTelemetryManager(DeviceHelper deviceHelper,
                                LatestDeviceRecordMapper latestRecordMapper,
                                VisibleLaboratoryScope visibleLaboratoryScope,
                                RedisBus redisBus) {
        this.deviceHelper = deviceHelper;
        this.latestRecordMapper = latestRecordMapper;
        this.visibleLaboratoryScope = visibleLaboratoryScope;
        this.redisBus = redisBus;
    }

    @Override
    public RpcResult<List<DeviceTelemetrySnapshot>> snapshots(List<String> laboratoryIds) {
        List<String> visibleIds = visibleLaboratoryScope.resolve(laboratoryIds);
        if (visibleIds.isEmpty()) {
            return RpcResult.success(List.of());
        }
        List<Device> devices = deviceHelper.listByLaboratories(null, visibleIds);
        if (devices.isEmpty()) {
            return RpcResult.success(List.of());
        }

        List<String> redisKeys = devices.stream()
                .map(device -> DeviceRecordKeys.recordKey(device.getDeviceType(), device.getId()))
                .toList();
        Map<String, Map<String, String>> redisSnapshots = redisBus.hgetAllBatch(redisKeys);
        Map<String, BaseRecord> databaseSnapshots = loadDatabaseFallbacks(devices, redisSnapshots);

        List<DeviceTelemetrySnapshot> result = new ArrayList<>(devices.size());
        for (Device device : devices) {
            String key = DeviceRecordKeys.recordKey(device.getDeviceType(), device.getId());
            Map<String, String> redisRecord = redisSnapshots.getOrDefault(key, Map.of());
            if (!redisRecord.isEmpty()) {
                result.add(fromRedis(device, redisRecord));
                continue;
            }
            BaseRecord databaseRecord = databaseSnapshots.get(device.getId());
            if (databaseRecord != null) {
                result.add(fromDatabase(device, databaseRecord));
            }
        }
        return RpcResult.success(result);
    }

    private Map<String, BaseRecord> loadDatabaseFallbacks(
            List<Device> devices,
            Map<String, Map<String, String>> redisSnapshots
    ) {
        Map<DeviceType, List<String>> missingByType = new EnumMap<>(DeviceType.class);
        for (Device device : devices) {
            String key = DeviceRecordKeys.recordKey(device.getDeviceType(), device.getId());
            if (redisSnapshots.getOrDefault(key, Map.of()).isEmpty()) {
                missingByType.computeIfAbsent(device.getDeviceType(), ignored -> new ArrayList<>())
                        .add(device.getId());
            }
        }

        Map<String, BaseRecord> result = new HashMap<>();
        addIfPresent(result, missingByType.get(DeviceType.Access), latestRecordMapper::latestAccess);
        addIfPresent(result, missingByType.get(DeviceType.AirCondition), latestRecordMapper::latestAirCondition);
        addIfPresent(result, missingByType.get(DeviceType.CircuitBreak), latestRecordMapper::latestCircuitBreak);
        addIfPresent(result, missingByType.get(DeviceType.Light), latestRecordMapper::latestLight);
        addIfPresent(result, missingByType.get(DeviceType.Sensor), latestRecordMapper::latestSensor);
        return result;
    }

    private static void addIfPresent(Map<String, BaseRecord> target,
                                     List<String> deviceIds,
                                     java.util.function.Function<List<String>, ? extends List<? extends BaseRecord>> loader) {
        if (deviceIds != null && !deviceIds.isEmpty()) {
            addAll(target, loader.apply(deviceIds));
        }
    }

    private static void addAll(Map<String, BaseRecord> target, List<? extends BaseRecord> records) {
        records.forEach(record -> target.put(record.getDeviceId(), record));
    }

    private DeviceTelemetrySnapshot fromRedis(Device device, Map<String, String> source) {
        Map<String, String> fields = new LinkedHashMap<>(source);
        Instant occurredAt = parseInstant(fields.remove(OCCURRED_AT_FIELD));
        return new DeviceTelemetrySnapshot(
                device.getId(),
                device.getBelongTo(),
                device.getDeviceType(),
                typedTelemetryFields(fields),
                occurredAt,
                true
        );
    }

    private static DeviceTelemetrySnapshot fromDatabase(Device device, BaseRecord record) {
        Instant occurredAt = record.getCreateAt() == null
                ? null
                : record.getCreateAt().atZone(ZoneId.systemDefault()).toInstant();
        return new DeviceTelemetrySnapshot(
                device.getId(),
                device.getBelongTo(),
                device.getDeviceType(),
                telemetryFields(record),
                occurredAt,
                false
        );
    }

    private static Map<String, Object> telemetryFields(BaseRecord record) {
        Map<String, Object> fields = new LinkedHashMap<>(ObjectMapUtil.toMap(record));
        List.of(
                "id", "deviceId", "laboratoryId", "origin",
                "createAt", "updateAt", "deleteAt"
        ).forEach(fields.keySet()::remove);
        return fields;
    }

    private static Map<String, Object> typedTelemetryFields(Map<String, String> source) {
        Map<String, Object> fields = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (List.of(
                    "id", "deviceId", "laboratoryId", "origin",
                    "createAt", "updateAt", "deleteAt"
            ).contains(key)) {
                return;
            }
            if (List.of("opened", "locked", "fixed").contains(key)) {
                fields.put(key, Boolean.parseBoolean(value));
                return;
            }
            if (List.of("mode", "speed").contains(key)) {
                fields.put(key, value);
                return;
            }
            try {
                fields.put(key, Double.parseDouble(value));
            } catch (NumberFormatException ignored) {
                fields.put(key, value);
            }
        });
        return fields;
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return Instant.now();
        }
    }
}
