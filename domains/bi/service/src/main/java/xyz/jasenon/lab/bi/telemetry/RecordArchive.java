package xyz.jasenon.lab.bi.telemetry;

import xyz.jasenon.lab.common.error.ErrorCode;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.observability.rpc.RpcErrors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import xyz.jasenon.lab.storage.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.zip.*;
import static xyz.jasenon.lab.bi.telemetry.TelemetryRepository.*;

/** Archive objects are immutable; deletion always compares every source field under a row lock. */
@Component
public class RecordArchive {
    public static final List<String> TABLES=List.of("access_record","air_condition_record","circuit_break_record","light_record","sensor_record");
    private final JdbcTemplate jdbc;
    private final TelemetryRollup rollup;
    private final BiLease lease;
    private final ObjectProvider<ObjectStorage> storage;
    private final StorageProperties properties;
    private final boolean deleteEnabled;
    private final int batchSize,retention;
    public RecordArchive(JdbcTemplate jdbc,TelemetryRollup rollup,BiLease lease,ObjectProvider<ObjectStorage> storage,
        StorageProperties properties,@Value("${lab.bi.archive.delete-enabled:false}") boolean deleteEnabled,
        @Value("${lab.bi.archive.batch-size:500}") int batchSize,@Value("${lab.bi.archive.retention-days:90}") int retention) {
        if(batchSize<1 || batchSize>2000 || retention<1) throw new IllegalArgumentException("Invalid archive limits");
        this.jdbc=jdbc;this.rollup=rollup;this.lease=lease;this.storage=storage;this.properties=properties;
        this.deleteEnabled=deleteEnabled;this.batchSize=batchSize;this.retention=retention;
    }
    public boolean deletionEnabled() { return deleteEnabled; }
    public int retentionDays() { return retention; }
    public boolean hasBacklog() {
        LocalDateTime cutoff=LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(retention).atStartOfDay();
        for(String table:TABLES) if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM "+table+" r WHERE r.create_at<? AND "+eligible(table,"r")+")",Boolean.class,cutoff))) return true;
        return false;
    }
    public String run(boolean dryRun,String token) throws Exception {
        if(!dryRun && !deleteEnabled) throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "删除开关未开启，请先执行归档演练");
        ObjectStorage objects=storage.getIfAvailable();
        if(objects==null) throw new BusinessException(ErrorCode.OBJECT_STORAGE_FAILED, "归档对象存储未启用");
        if(!dryRun) {
            for(var batch:jdbc.queryForList("SELECT * FROM bi_archive_batch WHERE status IN ('VERIFIED','DELETING','FAILED') AND sha256 IS NOT NULL ORDER BY created_at LIMIT 20")) {
                lease.renew(token); resume(batch,objects,token);
            }
        }
        // An interrupted pre-verification upload never authorizes deletion.
        lease.guarded(token,()->jdbc.update("UPDATE bi_archive_batch SET status='ABANDONED',error_message='Interrupted before verification' WHERE status='PREPARING'"));
        LocalDateTime cutoff=LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(retention).atStartOfDay();
        int batches=0,total=0;
        for(String table:TABLES) {
            // Bound each invocation. Repeated jobs drain the backlog without monopolizing MySQL.
            while(batches<20) {
                lease.renew(token);
                List<Map<String,Object>> rows=jdbc.queryForList("SELECT r.* FROM "+table+" r WHERE r.create_at<? AND "+eligible(table,"r")+" ORDER BY r.create_at,r.id LIMIT "+batchSize,cutoff);
                if(rows.isEmpty()) break;
                LocalDate date=time(rows.get(0).get("create_at")).toLocalDate();
                rows=rows.stream().filter(row->time(row.get("create_at")).toLocalDate().equals(date)).toList();
                LocalDateTime watermark=prepareDay(date,dryRun,token);
                for(var row:rows) if(time(row.get("update_at")).isAfter(watermark))
                    throw new BusinessException(ErrorCode.BI_ARCHIVE_INCONSISTENT, "已封存日期 " + date + " 存在新增或修改的记录，请核对后重试");
                String id=UUID.randomUUID().toString();
                String key=table+"/"+date+"/"+id+".ndjson.gz";
                List<Map<String,Object>> snapshot=rows.stream().map(RecordArchive::canonical).toList();
                lease.guarded(token,()->jdbc.update("INSERT INTO bi_archive_batch(id,table_name,archive_date,object_key,row_count,status) VALUES(?,?,?,?,?,'PREPARING')",
                    id,table,date,key,snapshot.size()));
                Path file=Files.createTempFile("lab-archive-",".gz"),manifest=Files.createTempFile("lab-manifest-",".json");
                try {
                    try(var output=new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(file)),StandardCharsets.UTF_8))) {
                        for(var row:snapshot) { output.write(json(Map.of("schemaVersion",1,"table",table,"row",row)));output.newLine(); }
                    }
                    String digest=objects.uploadVerified(properties.archiveBucket(),key,file,"application/gzip");
                    var entries=snapshot.stream().map(row->Map.of("id",row.get("id"),"version",row.get("update_at"),"sha256",rowDigest(row))).toList();
                    Files.writeString(manifest,json(Map.of("schemaVersion",1,"table",table,"batchId",id,"objectKey",key,
                        "rowCount",snapshot.size(),"sha256",digest,"startAt",snapshot.get(0).get("create_at"),
                        "endAt",snapshot.get(snapshot.size()-1).get("create_at"),"records",entries)));
                    String manifestDigest=objects.uploadVerified(properties.archiveBucket(),key+".manifest.json",manifest,"application/json");
                    lease.guarded(token,()->jdbc.update("UPDATE bi_archive_batch SET status=?,sha256=?,manifest_sha256=?,error_message=NULL WHERE id=?",
                        dryRun?"DRY_RUN":"VERIFIED",digest,manifestDigest,id));
                    if(!dryRun) resume(jdbc.queryForMap("SELECT * FROM bi_archive_batch WHERE id=?",id),objects,token);
                    total+=snapshot.size();batches++;
                    if(dryRun) break;
                } catch(Exception error) {
                    var fallback = ErrorCode.BI_MAINTENANCE_FAILED;
                    var failure = RpcErrors.from(error, fallback, fallback.message());
                    lease.guarded(token,()->jdbc.update("UPDATE bi_archive_batch SET status='FAILED',error_message=? WHERE id=?",
                        abbreviate("[" + failure.code() + "] " + failure.message()),id));
                    throw error;
                } finally { Files.deleteIfExists(file);Files.deleteIfExists(manifest); }
            }
        }
        return "batches="+batches+", records="+total+", dryRun="+dryRun;
    }
    private LocalDateTime prepareDay(LocalDate date,boolean dryRun,String token) {
        var sealed=jdbc.queryForList("SELECT calculated_at FROM bi_summary_day WHERE summary_date=? AND sealed=1",date);
        if(!sealed.isEmpty()) return time(sealed.get(0).get("calculated_at"));
        LocalDateTime began=jdbc.queryForObject("SELECT NOW(3)",Timestamp.class).toLocalDateTime();
        rollup.day(date,token);
        // Raw records carry database update timestamps. Refuse to seal a moving source.
        lease.guarded(token,()-> {
            for(String table:TABLES) {
                Long changed=jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE create_at>=? AND create_at<? AND update_at>?",
                    Long.class,date.atStartOfDay(),date.plusDays(1).atStartOfDay(),began);
                if(changed!=null && changed>0) throw new BusinessException(ErrorCode.BI_ARCHIVE_INCONSISTENT, "汇总期间源记录发生变更，请重新汇总 " + date);
            }
            if(!dryRun) jdbc.update("UPDATE bi_summary_day SET sealed=1,calculated_at=? WHERE summary_date=?",began,date);
        });
        return began;
    }
    @SuppressWarnings("unchecked")
    private void resume(Map<String,Object> batch,ObjectStorage objects,String token) throws Exception {
        String table=(String)batch.get("table_name"),key=(String)batch.get("object_key"),id=(String)batch.get("id");
        if(!TABLES.contains(table)) throw new BusinessException(ErrorCode.BI_ARCHIVE_INVALID, "归档记录的表名无效");
        if(!objects.sha256(properties.archiveBucket(),key).equals(batch.get("sha256")) ||
           !objects.sha256(properties.archiveBucket(),key+".manifest.json").equals(batch.get("manifest_sha256")))
            throw new BusinessException(ErrorCode.BI_ARCHIVE_INVALID, "归档文件校验和不匹配");
        List<Map<String,Object>> rows=new ArrayList<>();
        try(var input=new BufferedReader(new InputStreamReader(new GZIPInputStream(objects.get(properties.archiveBucket(),key)),StandardCharsets.UTF_8))) {
            for(String line;(line=input.readLine())!=null;) {
                var envelope=readMap(line);
                if(!table.equals(envelope.get("table")) || number(envelope.get("schemaVersion"))!=1) throw new BusinessException(ErrorCode.BI_ARCHIVE_INVALID, "归档文件格式不匹配");
                rows.add((Map<String,Object>)envelope.get("row"));
                if(rows.size()>2000) throw new BusinessException(ErrorCode.BI_ARCHIVE_INVALID, "归档文件超过批次行数上限");
            }
        }
        if(rows.size()!=((Number)batch.get("row_count")).intValue()) throw new BusinessException(ErrorCode.BI_ARCHIVE_INVALID, "归档文件行数与批次记录不匹配");
        lease.guarded(token,()->jdbc.update("UPDATE bi_archive_batch SET status='DELETING' WHERE id=?",id));
        // One short transaction per source row. Resume tolerates already-deleted rows.
        for(var archived:rows) {
            lease.guarded(token,()->{
                var current=jdbc.queryForList("SELECT * FROM "+table+" WHERE id=? FOR UPDATE",archived.get("id"));
                if(current.isEmpty()) return;
                if(!canonical(current.get(0)).equals(archived)) throw new BusinessException(ErrorCode.BI_ARCHIVE_INCONSISTENT, "归档期间源记录版本发生变更");
                var newest=jdbc.queryForList("SELECT id FROM "+table+" WHERE device_id=? AND delete_at IS NULL ORDER BY create_at DESC,id DESC LIMIT 1",archived.get("device_id"));
                if(!newest.isEmpty() && archived.get("id").equals(newest.get(0).get("id"))) throw new BusinessException(ErrorCode.BI_ARCHIVE_INCONSISTENT, "设备最新状态必须保留在在线库");
                if(archived.get("delete_at")==null) {
                    var existing=jdbc.queryForList("SELECT observed_at,record_id FROM bi_archive_anchor WHERE table_name=? AND device_id=? FOR UPDATE",table,archived.get("device_id"));
                    boolean newer=existing.isEmpty() || time(existing.get(0).get("observed_at")).isBefore(time(archived.get("create_at")))
                        || time(existing.get(0).get("observed_at")).equals(time(archived.get("create_at"))) && existing.get(0).get("record_id").toString().compareTo(archived.get("id").toString())<0;
                    if(newer) jdbc.update("INSERT INTO bi_archive_anchor(table_name,device_id,observed_at,record_id,payload) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE observed_at=VALUES(observed_at),record_id=VALUES(record_id),payload=VALUES(payload)",
                        table,archived.get("device_id"),time(archived.get("create_at")),archived.get("id"),json(archived));
                }
                jdbc.update("DELETE FROM "+table+" WHERE id=?",archived.get("id"));
                jdbc.update("UPDATE bi_archive_batch SET deleted_count=deleted_count+1 WHERE id=?",id);
            });
        }
        lease.guarded(token,()->jdbc.update("UPDATE bi_archive_batch SET status='COMPLETE',finished_at=NOW(3),error_message=NULL WHERE id=?",id));
    }
    private static String eligible(String table,String alias) {
        return "("+alias+".delete_at IS NOT NULL OR EXISTS(SELECT 1 FROM "+table+" newer WHERE newer.device_id="+alias+".device_id AND newer.delete_at IS NULL AND (newer.create_at>"+alias+".create_at OR (newer.create_at="+alias+".create_at AND newer.id>"+alias+".id))))";
    }
    /** Store exact SQL values, including decimals and millisecond versions, without JSON number coercion. */
    public static Map<String,Object> canonical(Map<String,Object> raw) {
        Map<String,Object> result=new TreeMap<>();
        raw.forEach((key,value)->result.put(key,value==null?null:
            value instanceof Timestamp t?t.toLocalDateTime().toString():
            value instanceof LocalDateTime t?t.toString():
            value instanceof Boolean b?b?"1":"0":value.toString()));
        return result;
    }
    private static String rowDigest(Map<String,Object> row) {
        try { return ObjectStorage.digest(new ByteArrayInputStream(json(row).getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    private static String abbreviate(String s) { return s.substring(0,Math.min(2000,s.length())); }
}
