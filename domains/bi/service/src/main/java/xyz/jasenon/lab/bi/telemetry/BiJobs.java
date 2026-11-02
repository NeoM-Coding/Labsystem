package xyz.jasenon.lab.bi.telemetry;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.*;
import static xyz.jasenon.lab.bi.telemetry.TelemetryRepository.time;

@Component
@EnableScheduling
public class BiJobs {
    private static final Logger log=LoggerFactory.getLogger(BiJobs.class);
    private final JdbcTemplate jdbc;
    private final BiLease lease;
    private final TelemetryRollup rollup;
    private final RecordArchive archive;
    public BiJobs(JdbcTemplate jdbc,BiLease lease,TelemetryRollup rollup,RecordArchive archive) {
        this.jdbc=jdbc;this.lease=lease;this.rollup=rollup;this.archive=archive;
    }
    @Scheduled(fixedDelayString="${lab.bi.jobs.poll-ms:30000}",initialDelayString="${lab.bi.jobs.initial-delay-ms:30000}")
    public void poll() {
        String token=null;
        try {
            token=lease.acquire(); if(token==null) return;
            final String owner=token;
            lease.guarded(token,()->jdbc.update("UPDATE bi_work_request SET status='QUEUED' WHERE status='RUNNING'"));
            enqueueChangedBoundaries(owner);
            var requests=jdbc.queryForList("SELECT * FROM bi_work_request WHERE status='QUEUED' ORDER BY created_at LIMIT 1");
            if(requests.isEmpty()) return;
            var request=requests.get(0);String id=(String)request.get("id"); long began=System.nanoTime();
            lease.guarded(token,()->jdbc.update("UPDATE bi_work_request SET status='RUNNING',started_at=NOW(3),error_message=NULL WHERE id=?",id));
            try {
                String result;
                if("BACKFILL".equals(request.get("kind"))) {
                    LocalDate start=LocalDate.parse(request.get("start_date").toString()),end=LocalDate.parse(request.get("end_date").toString());
                    if(request.get("next_date")!=null) start=LocalDate.parse(request.get("next_date").toString());
                    // Persist progress after every day; next worker resumes without repeating completed dates.
                    for(LocalDate d=start;!d.isAfter(end);d=d.plusDays(1)) {
                        rollup.day(d,token);LocalDate next=d.plusDays(1);
                        lease.guarded(owner,()->jdbc.update("UPDATE bi_work_request SET next_date=? WHERE id=?",next,id));
                    }
                    result="Backfill complete";
                } else {
                    boolean dry=TelemetryRepository.bool(request.get("dry_run"));
                    result=archive.run(dry,token);
                    if(!dry && archive.hasBacklog()) lease.guarded(token,()->jdbc.update(
                        "INSERT INTO bi_work_request(id,kind,dry_run,status,requested_by) VALUES(?,'ARCHIVE',0,'QUEUED','scheduler')",
                        java.util.UUID.randomUUID().toString()));
                }
                lease.guarded(token,()->jdbc.update("UPDATE bi_work_request SET status='COMPLETE',result_message=?,finished_at=NOW(3) WHERE id=?",result,id));
                log.info("BI job {} complete in {} ms: {}",id,(System.nanoTime()-began)/1_000_000,result);
            } catch(Exception e) {
                log.error("BI job {} failed",id,e);
                lease.guarded(token,()->jdbc.update("UPDATE bi_work_request SET status='FAILED',error_message=?,finished_at=NOW(3) WHERE id=?",e.toString().substring(0,Math.min(e.toString().length(),2000)),id));
            }
        } catch(Exception e) { log.error("BI scheduler failed",e); }
        finally { if(token!=null) lease.release(token); }
    }
    /**
     * A reading after an outage can close an energy interval spanning several days.
     * Recompute from the previous observed boundary, not merely the last calendar day.
     */
    void enqueueChangedBoundaries(String token) {
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
        LocalDate earliest=null;
        java.util.List<Runnable> updates=new java.util.ArrayList<>();
        TelemetryRepository repository=new TelemetryRepository(jdbc);
        for(var device:repository.devices()) {
            var raw=repository.latest(device);
            if(raw.isEmpty()) continue;
            var latest=TelemetryRepository.sample(raw);
            var cursors=jdbc.queryForList("SELECT record_id,observed_at FROM bi_projection_cursor WHERE device_id=?",device.id());
            if(!cursors.isEmpty() && latest.id().equals(cursors.get(0).get("record_id"))) continue;
            LocalDate start;
            if(cursors.isEmpty()) {
                var first=jdbc.queryForObject("SELECT MIN(create_at) FROM "+TelemetryRepository.table(device.kind())+" WHERE device_id=? AND delete_at IS NULL",
                    java.sql.Timestamp.class,device.id());
                start=first==null?today:first.toLocalDateTime().toLocalDate();
            } else start=time(cursors.get(0).get("observed_at")).toLocalDate();
            if(start.isBefore(today) && (earliest==null || start.isBefore(earliest))) earliest=start;
            updates.add(()->jdbc.update("INSERT INTO bi_projection_cursor(device_id,record_id,observed_at) VALUES(?,?,?) ON DUPLICATE KEY UPDATE record_id=VALUES(record_id),observed_at=VALUES(observed_at)",
                device.id(),latest.id(),latest.time()));
        }
        LocalDate start=earliest;
        lease.guarded(token,()->{
            if(start!=null) jdbc.update("INSERT INTO bi_work_request(id,kind,start_date,end_date,dry_run,status,requested_by) VALUES(?,'BACKFILL',?,?,0,'QUEUED','incremental')",
                java.util.UUID.randomUUID().toString(),start,today.minusDays(1));
            updates.forEach(Runnable::run);
        });
    }
    @Scheduled(cron="${lab.bi.jobs.daily-cron:0 10 2 * * *}",zone="Asia/Shanghai")
    public void daily() {
        LocalDate yesterday=LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1);
        jdbc.update("INSERT INTO bi_work_request(id,kind,start_date,end_date,dry_run,status,requested_by) VALUES(?, 'BACKFILL',?,?,0,'QUEUED','scheduler') ON DUPLICATE KEY UPDATE id=id",
            "daily-rollup-"+yesterday,yesterday.minusDays(1),yesterday);
        if(archive.deletionEnabled()) jdbc.update("INSERT INTO bi_work_request(id,kind,dry_run,status,requested_by) VALUES(?,'ARCHIVE',0,'QUEUED','scheduler') ON DUPLICATE KEY UPDATE id=id","daily-archive-"+yesterday);
    }
}
