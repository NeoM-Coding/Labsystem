package xyz.jasenon.lab.common.realtime;

public final class RealtimeEventTypes {

    public static final String SYSTEM_CONNECTED = "system.connected";
    public static final String DEVICE_TELEMETRY_UPDATED = "device.telemetry.updated";
    public static final String DEVICE_ONLINE_CHANGED = "device.online.changed";
    public static final String DEVICE_ALERT_RAISED = "device.alert.raised";
    public static final String DEVICE_ALERT_RESOLVED = "device.alert.resolved";

    private RealtimeEventTypes() {
    }
}
