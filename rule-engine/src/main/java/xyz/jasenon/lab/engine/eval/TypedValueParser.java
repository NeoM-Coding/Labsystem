package xyz.jasenon.lab.engine.eval;

import xyz.jasenon.lab.common.model.device.DeviceType;

import java.math.BigDecimal;

public final class TypedValueParser {

    private TypedValueParser() {
    }

    public static Object parse(DeviceType deviceType, String field, String value) {
        Class<?> fieldType = RecordFieldTypeResolver.resolve(deviceType, field);
        return parseAs(fieldType, value);
    }

    public static boolean compare(DeviceType deviceType, String field, Operator operator, String leftValue, String rightValue) {
        if (operator == null) {
            throw new IllegalArgumentException("operator must not be null");
        }
        Class<?> fieldType = RecordFieldTypeResolver.resolve(deviceType, field);
        Object left = parseAs(fieldType, leftValue);
        Object right = parseAs(fieldType, rightValue);

        if (isNumberType(fieldType)) {
            BigDecimal leftNumber = toBigDecimal(left);
            BigDecimal rightNumber = toBigDecimal(right);
            int compared = leftNumber.compareTo(rightNumber);
            return switch (operator) {
                case EQ -> compared == 0;
                case NE -> compared != 0;
                case GT -> compared > 0;
                case GE -> compared >= 0;
                case ST -> compared < 0;
                case SE -> compared <= 0;
            };
        }

        return switch (operator) {
            case EQ -> left.equals(right);
            case NE -> !left.equals(right);
            case GT, GE, ST, SE -> false;
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object parseAs(Class<?> fieldType, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String trimmed = value.trim();
        if (fieldType == String.class) {
            return value;
        }
        if (fieldType == boolean.class || fieldType == Boolean.class) {
            if ("true".equalsIgnoreCase(trimmed) || "1".equals(trimmed)) {
                return true;
            }
            if ("false".equalsIgnoreCase(trimmed) || "0".equals(trimmed)) {
                return false;
            }
            throw new IllegalArgumentException("invalid boolean value: " + value);
        }
        if (fieldType == int.class || fieldType == Integer.class) {
            return Integer.parseInt(trimmed);
        }
        if (fieldType == long.class || fieldType == Long.class) {
            return Long.parseLong(trimmed);
        }
        if (fieldType == float.class || fieldType == Float.class) {
            return new BigDecimal(trimmed);
        }
        if (fieldType == double.class || fieldType == Double.class) {
            return new BigDecimal(trimmed);
        }
        if (fieldType == BigDecimal.class) {
            return new BigDecimal(trimmed);
        }
        if (fieldType.isEnum()) {
            return Enum.valueOf((Class<Enum>) fieldType.asSubclass(Enum.class), trimmed);
        }
        return value;
    }

    private static boolean isNumberType(Class<?> type) {
        return type == byte.class || type == Byte.class
                || type == short.class || type == Short.class
                || type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == float.class || type == Float.class
                || type == double.class || type == Double.class
                || type == BigDecimal.class;
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        return new BigDecimal(String.valueOf(value));
    }
}
