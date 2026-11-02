package xyz.jasenon.lab.bi.api.view;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
public record TelemetryDashboard(String kind, LocalDate periodStart, LocalDate periodEnd, LocalDateTime calculatedAt,
        boolean incomplete, List<String> qualityNotes, Map<String, Double> metrics, List<Point> timeline,
        List<Point> laboratoryRanking, List<Point> deviceRanking, List<Point> modes,
        List<DeviceState> devices, List<Fault> faults, List<UsageRow> rows) implements Serializable {
    public record UsageRow(String deviceId, String deviceName, String groupId, String laboratoryId,
            String laboratoryName, String orgName, String buildingName, double value,
            boolean incomplete, double coverage) implements Serializable {}
    public record Point(String key, String label, double value, boolean incomplete) implements Serializable {}
    public record DeviceState(String deviceId, String name, String laboratoryId, LocalDateTime observedAt,
        boolean stale, boolean opened, Map<String, Double> values) implements Serializable {}
    public record Fault(String deviceId, LocalDateTime startAt, LocalDateTime endAt, int errorCode) implements Serializable {}
}
