package xyz.jasenon.lab.observability.log;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public final class SafeArgumentRenderer {

    private static final Set<String> SENSITIVE = Set.of(
            "password", "passwd", "secret", "token", "authorization", "cookie", "credential", "privatekey"
    );

    private final int maxLength;
    private final int maxCollectionSize;
    private final int maxDepth;

    public SafeArgumentRenderer(int maxLength, int maxCollectionSize, int maxDepth) {
        this.maxLength = Math.max(128, maxLength);
        this.maxCollectionSize = Math.max(1, maxCollectionSize);
        this.maxDepth = Math.max(1, maxDepth);
    }

    public String render(Object value) {
        StringBuilder output = new StringBuilder();
        append(value, output, 0, new IdentityHashMap<>());
        return output.length() <= maxLength
                ? output.toString()
                : output.substring(0, maxLength) + "...[truncated]";
    }

    private void append(Object value, StringBuilder output, int depth, IdentityHashMap<Object, Boolean> seen) {
        if (value == null || isSimple(value)) {
            output.append(String.valueOf(value));
            return;
        }
        if (depth >= maxDepth) {
            output.append("<max-depth>");
            return;
        }
        if (seen.put(value, Boolean.TRUE) != null) {
            output.append("<cycle>");
            return;
        }
        try {
            if (value instanceof Map<?, ?> map) {
                appendMap(map, output, depth, seen);
            } else if (value instanceof Collection<?> collection) {
                appendCollection(collection, output, depth, seen);
            } else if (value.getClass().isArray()) {
                appendArray(value, output, depth, seen);
            } else {
                appendObject(value, output, depth, seen);
            }
        } finally {
            seen.remove(value);
        }
    }

    private void appendMap(Map<?, ?> map, StringBuilder output, int depth, IdentityHashMap<Object, Boolean> seen) {
        output.append('{');
        int index = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (index++ >= maxCollectionSize) break;
            String key = String.valueOf(entry.getKey());
            if (index > 1) output.append(", ");
            output.append(key).append('=');
            if (isSensitive(key)) output.append("***");
            else append(entry.getValue(), output, depth + 1, seen);
        }
        output.append('}');
    }

    private void appendCollection(Collection<?> values, StringBuilder output, int depth, IdentityHashMap<Object, Boolean> seen) {
        output.append('[');
        int index = 0;
        for (Object value : values) {
            if (index >= maxCollectionSize) break;
            if (index++ > 0) output.append(", ");
            append(value, output, depth + 1, seen);
        }
        output.append(']');
    }

    private void appendArray(Object array, StringBuilder output, int depth, IdentityHashMap<Object, Boolean> seen) {
        output.append('[');
        int length = Math.min(Array.getLength(array), maxCollectionSize);
        for (int i = 0; i < length; i++) {
            if (i > 0) output.append(", ");
            append(Array.get(array, i), output, depth + 1, seen);
        }
        output.append(']');
    }

    private void appendObject(Object value, StringBuilder output, int depth, IdentityHashMap<Object, Boolean> seen) {
        Package typePackage = value.getClass().getPackage();
        if (typePackage != null && typePackage.getName().startsWith("java.")) {
            output.append(value.getClass().getSimpleName());
            return;
        }
        output.append(value.getClass().getSimpleName()).append('{');
        int index = 0;
        for (Field field : value.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
            if (index++ >= maxCollectionSize) break;
            if (index > 1) output.append(", ");
            output.append(field.getName()).append('=');
            if (isSensitive(field.getName())) {
                output.append("***");
                continue;
            }
            try {
                if (!field.trySetAccessible()) {
                    output.append("<inaccessible>");
                } else {
                    append(field.get(value), output, depth + 1, seen);
                }
            } catch (RuntimeException | IllegalAccessException ignored) {
                output.append("<inaccessible>");
            }
        }
        output.append('}');
    }

    private static boolean isSimple(Object value) {
        return value instanceof CharSequence || value instanceof Number || value instanceof Boolean
                || value instanceof Character || value instanceof Enum<?> || value instanceof TemporalAccessor;
    }

    private static boolean isSensitive(String name) {
        String normalized = name.replace("_", "").replace("-", "").toLowerCase();
        return SENSITIVE.stream().anyMatch(normalized::contains);
    }
}
