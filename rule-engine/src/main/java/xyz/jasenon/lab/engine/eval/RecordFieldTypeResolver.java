package xyz.jasenon.lab.engine.eval;

import xyz.jasenon.lab.common.model.device.BaseRecord;
import xyz.jasenon.lab.common.model.device.DeviceType;
import xyz.jasenon.lab.common.model.device.records.AccessRecord;
import xyz.jasenon.lab.common.model.device.records.AirConditionRecord;
import xyz.jasenon.lab.common.model.device.records.CircuitBreakRecord;
import xyz.jasenon.lab.common.model.device.records.LightRecord;
import xyz.jasenon.lab.common.model.device.records.SensorRecord;

import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class RecordFieldTypeResolver {

    private static final Map<DeviceType, Class<? extends BaseRecord>> RECORD_TYPES = new EnumMap<>(DeviceType.class);
    private static final ConcurrentHashMap<String, Class<?>> FIELD_TYPES = new ConcurrentHashMap<>();

    static {
        RECORD_TYPES.put(DeviceType.Access, AccessRecord.class);
        RECORD_TYPES.put(DeviceType.AirCondition, AirConditionRecord.class);
        RECORD_TYPES.put(DeviceType.CircuitBreak, CircuitBreakRecord.class);
        RECORD_TYPES.put(DeviceType.Light, LightRecord.class);
        RECORD_TYPES.put(DeviceType.Sensor, SensorRecord.class);
    }

    private RecordFieldTypeResolver() {
    }

    public static Class<?> resolve(DeviceType deviceType, String fieldName) {
        Objects.requireNonNull(deviceType, "deviceType");
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName must not be blank");
        }
        Class<? extends BaseRecord> recordType = RECORD_TYPES.get(deviceType);
        if (recordType == null) {
            throw new IllegalArgumentException("unsupported device type: " + deviceType);
        }
        return FIELD_TYPES.computeIfAbsent(deviceType.name() + ":" + fieldName, ignored -> findFieldType(recordType, fieldName));
    }

    private static Class<?> findFieldType(Class<?> recordType, String fieldName) {
        Class<?> current = recordType;
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(fieldName);
                return field.getType();
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new IllegalArgumentException("unknown record field: " + recordType.getSimpleName() + "." + fieldName);
    }
}
