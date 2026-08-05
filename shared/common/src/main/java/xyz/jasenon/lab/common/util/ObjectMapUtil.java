package xyz.jasenon.lab.common.util;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ObjectMapUtil {

    private ObjectMapUtil() {
    }

    public static Map<String, Object> toMap(Object source) {
        if (source == null) {
            return Map.of();
        }
        if (source instanceof Map<?, ?> map) {
            return copyMap(map);
        }
        if (source.getClass().isRecord()) {
            return recordToMap(source);
        }
        return fieldsToMap(source);
    }

    public static Map<String, String> toStringMap(Object source) {
        Map<String, Object> values = toMap(source);
        if (values.isEmpty()) {
            return Map.of();
        }

        Map<String, String> result = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (value != null) {
                result.put(key, String.valueOf(value));
            }
        });
        return result;
    }

    private static Map<String, Object> copyMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null) {
                result.put(String.valueOf(key), value);
            }
        });
        return result;
    }

    private static Map<String, Object> recordToMap(Object source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (RecordComponent component : source.getClass().getRecordComponents()) {
            try {
                result.put(component.getName(), component.getAccessor().invoke(source));
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalArgumentException("failed to read record component: " + component.getName(), e);
            }
        }
        return result;
    }

    private static Map<String, Object> fieldsToMap(Object source) {
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            Class<?> type = source.getClass();
            while (type != null && type != Object.class) {
                for (Field field : type.getDeclaredFields()) {
                    if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    field.setAccessible(true);
                    result.put(field.getName(), field.get(source));
                }
                type = type.getSuperclass();
            }
            return result;
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(
                    "failed to convert object to map: " + Objects.toString(source.getClass().getName()),
                    e
            );
        }
    }
}
