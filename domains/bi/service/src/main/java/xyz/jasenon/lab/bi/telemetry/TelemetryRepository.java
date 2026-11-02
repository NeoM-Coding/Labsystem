package xyz.jasenon.lab.bi.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.time.*;
import java.util.*;
import static xyz.jasenon.lab.bi.telemetry.TelemetryMath.*;

@Repository
public class TelemetryRepository {
    public record Device(String id, String name, String laboratoryId, String kind, LocalDateTime createdAt, LocalDateTime deletedAt, String groupId) {
        public Device(String id,String name,String laboratoryId,String kind,LocalDateTime createdAt,LocalDateTime deletedAt) { this(id,name,laboratoryId,kind,createdAt,deletedAt,null); }
        public Device(String id,String name,String laboratoryId,String kind,LocalDateTime createdAt) { this(id,name,laboratoryId,kind,createdAt,null,null); }
    }
    public record SavedHour(String deviceId, String laboratoryId, String kind, Hour value) {}
    public static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final JdbcTemplate jdbc;
    public TelemetryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public static String table(String kind) {
        return switch(kind) { case "AIR_CONDITION" -> "air_condition_record"; case "ENERGY" -> "circuit_break_record";
            default -> throw new IllegalArgumentException("Unknown telemetry kind"); };
    }
    public List<Device> devices() {
        return jdbc.query("SELECT id,device_name,belong_to,device_type,create_at,delete_at,group_id FROM device WHERE device_type IN ('AirCondition','CircuitBreak')",
            (rs,n) -> new Device(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getString(4).equals("AirCondition") ? "AIR_CONDITION" : "ENERGY", rs.getTimestamp(5).toLocalDateTime(),rs.getTimestamp(6)==null?null:rs.getTimestamp(6).toLocalDateTime(),rs.getString(7)));
    }
    public record Laboratory(String id, String name, String buildingName, String orgName) {}
    public Map<String, Laboratory> laboratories(Set<String> ids) {
        if(ids.isEmpty()) return Map.of();
        String sql="SELECT id,laboratory_name,building_name,org_name FROM laboratory WHERE id IN ("
            + String.join(",",Collections.nCopies(ids.size(),"?")) + ")";
        Map<String,Laboratory> result=new HashMap<>();
        jdbc.query(sql,(rs,n)->new Laboratory(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4)),
            ids.toArray()).forEach(lab->result.put(lab.id(),lab));
        return result;
    }
    public List<Sample> samples(Device device, LocalDateTime start, LocalDateTime end) {
        String table = table(device.kind());
        List<Map<String,Object>> rows = new ArrayList<>(jdbc.queryForList(
            "SELECT * FROM " + table + " WHERE device_id=? AND delete_at IS NULL AND create_at>=? AND create_at<? ORDER BY create_at,id",
            device.id(), start, end));
        rows.addAll(jdbc.queryForList("SELECT * FROM " + table +
            " WHERE device_id=? AND delete_at IS NULL AND create_at<? ORDER BY create_at DESC,id DESC LIMIT 1", device.id(), start));
        rows.addAll(jdbc.queryForList("SELECT * FROM " + table +
            " WHERE device_id=? AND delete_at IS NULL AND create_at>=? ORDER BY create_at,id LIMIT 1", device.id(), end));
        // The archive anchor retains the predecessor after raw rows have left MySQL.
        jdbc.query("SELECT payload FROM bi_archive_anchor WHERE table_name=? AND device_id=? AND observed_at<?",
            (rs,n) -> { rows.add(readMap(rs.getString(1))); return 0; }, table, device.id(), start);
        return rows.stream().map(TelemetryRepository::sample).sorted(Comparator.comparing(Sample::time).thenComparing(Sample::id)).toList();
    }
    public Map<String,Object> latest(Device device) {
        var rows = jdbc.queryForList("SELECT * FROM " + table(device.kind()) +
                " WHERE device_id=? AND delete_at IS NULL ORDER BY create_at DESC,id DESC LIMIT 1", device.id());
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }
    public List<SavedHour> saved(LocalDate start, LocalDate end, String kind) {
        return jdbc.query("SELECT device_id,laboratory_id,kind,payload FROM bi_device_hourly WHERE hour_start>=? AND hour_start<? AND kind=?",
            (rs,n) -> new SavedHour(rs.getString(1),rs.getString(2),rs.getString(3),read(rs.getString(4),Hour.class)),
            start.atStartOfDay(),end.plusDays(1).atStartOfDay(),kind);
    }
    public List<SavedHour> saved(LocalDate start,LocalDate end,String kind,Set<String> labs) {
        if(labs.isEmpty()) return List.of();
        List<Object> params=new ArrayList<>();
        params.add(start.atStartOfDay());params.add(end.plusDays(1).atStartOfDay());params.add(kind);params.addAll(labs);
        String placeholders=String.join(",",java.util.Collections.nCopies(labs.size(),"?"));
        return jdbc.query("SELECT device_id,laboratory_id,kind,payload FROM bi_device_hourly WHERE hour_start>=? AND hour_start<? AND kind=? AND laboratory_id IN ("+placeholders+")",
            (rs,n)->new SavedHour(rs.getString(1),rs.getString(2),rs.getString(3),read(rs.getString(4),Hour.class)),params.toArray());
    }
    public java.util.stream.Stream<SavedHour> streamSaved(LocalDate start,LocalDate end,String kind,Set<String> labs) {
        return streamSaved(start,end,kind,labs,Set.of());
    }
    public java.util.stream.Stream<SavedHour> streamSaved(LocalDate start,LocalDate end,String kind,Set<String> labs,Set<String> deviceIds) {
        if(labs.isEmpty() || start.isAfter(end)) return java.util.stream.Stream.empty();
        List<Object> params=new ArrayList<>();
        params.add(start.atStartOfDay());params.add(end.plusDays(1).atStartOfDay());params.add(kind);params.addAll(labs);
        String sql="SELECT device_id,laboratory_id,kind,payload FROM bi_device_hourly WHERE hour_start>=? AND hour_start<? AND kind=? AND laboratory_id IN ("
            +String.join(",",java.util.Collections.nCopies(labs.size(),"?"))+")";
        if(!deviceIds.isEmpty()) {
            sql+=" AND device_id IN ("+String.join(",",Collections.nCopies(deviceIds.size(),"?"))+")";
            params.addAll(deviceIds);
        }
        return jdbc.queryForStream(sql,ps-> {
            for(int i=0;i<params.size();i++) ps.setObject(i+1,params.get(i));
            // MySQL streaming mode avoids buffering a year of hourly JSON in the driver.
            ps.setFetchSize(Integer.MIN_VALUE);
        },(rs,n)->new SavedHour(rs.getString(1),rs.getString(2),rs.getString(3),read(rs.getString(4),Hour.class)));
    }
    public void save(Device d, Hour h) {
        jdbc.update("INSERT INTO bi_device_hourly(device_id,laboratory_id,kind,hour_start,payload) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE laboratory_id=VALUES(laboratory_id),payload=VALUES(payload)",
            d.id(),d.laboratoryId(),d.kind(),h.hour,json(h));
    }
    public static Sample sample(Map<String,Object> r) {
        return new Sample(String.valueOf(r.get("id")),String.valueOf(r.get("device_id")),time(r.get("create_at")),
            bool(r.get("is_open")), (String)r.get("mode"),number(r.get("temperature")),number(r.get("room_temperature")),
            (int)number(r.get("error_code")),number(r.get("energy")),number(r.get("power")));
    }
    public static boolean bool(Object v) { return Boolean.TRUE.equals(v) || "1".equals(String.valueOf(v)) || v instanceof Number n && n.intValue()!=0; }
    public static double number(Object v) { if (v == null) return 0; double d=Double.parseDouble(v.toString()); return Double.isFinite(d) ? d : 0; }
    public static LocalDateTime time(Object v) {
        if (v instanceof Timestamp t) return t.toLocalDateTime();
        if (v instanceof LocalDateTime t) return t;
        return LocalDateTime.parse(v.toString().replace(' ','T'));
    }
    public static String json(Object value) {
        try { return JSON.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException(e); }
    }
    public static <T> T read(String json, Class<T> type) {
        try { return JSON.readValue(json,type); } catch(Exception e) { throw new IllegalStateException(e); }
    }
    @SuppressWarnings("unchecked") public static Map<String,Object> readMap(String json) { return read(json,Map.class); }
}
