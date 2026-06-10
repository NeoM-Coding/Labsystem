package xyz.jasenon.lab.common.util;

public final class ByteUtil {

    private ByteUtil() {
    }

    public static int unsignedByte(byte[] bytes, int index) {
        requireLength(bytes, index + 1);
        return bytes[index] & 0xFF;
    }

    public static int unsignedBigEndian(byte[] bytes, int offset, int length) {
        if (length <= 0 || length > Integer.BYTES) {
            throw new IllegalArgumentException("length must be between 1 and " + Integer.BYTES);
        }
        requireLength(bytes, offset + length);

        int value = 0;
        for (int index = 0; index < length; index++) {
            value = (value << Byte.SIZE) | (bytes[offset + index] & 0xFF);
        }
        return value;
    }

    public static boolean bit(byte value, int bitIndex) {
        if (bitIndex < 0 || bitIndex > 7) {
            throw new IllegalArgumentException("bitIndex must be between 0 and 7");
        }
        return ((value & 0xFF) & (1 << bitIndex)) != 0;
    }

    public static float reversedBytesToFloat(byte[] bytes) {
        if (bytes == null || bytes.length != Float.BYTES) {
            throw new IllegalArgumentException("float bytes length must be " + Float.BYTES);
        }

        int bits = 0;
        for (byte value : bytes) {
            bits = (bits >>> Byte.SIZE) | ((value & 0xFF) << 24);
        }
        return Float.intBitsToFloat(bits);
    }

    public static float littleEndianFloat(byte[] bytes, int offset) {
        requireLength(bytes, offset + Float.BYTES);

        int bits = (bytes[offset] & 0xFF)
                | ((bytes[offset + 1] & 0xFF) << 8)
                | ((bytes[offset + 2] & 0xFF) << 16)
                | ((bytes[offset + 3] & 0xFF) << 24);
        return Float.intBitsToFloat(bits);
    }

    public static void requireLength(byte[] bytes, int minLength) {
        if (bytes == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        if (bytes.length < minLength) {
            throw new IllegalArgumentException("payload length must be at least " + minLength + ", actual " + bytes.length);
        }
    }
}
