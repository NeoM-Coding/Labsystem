package xyz.jasenon.lab.device.model;

public final class DeviceRecordKeys {

    private static final String PREFIX = "record";
    private static final String SEPARATOR = ":";

    private DeviceRecordKeys() {
    }

    public static String recordKey(DeviceType deviceType, String deviceId) {
        return PREFIX + SEPARATOR + deviceType.name() + SEPARATOR + deviceId;
    }
}
