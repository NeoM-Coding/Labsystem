package xyz.jasenon.lab.bi.telemetry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.time.*;
import static xyz.jasenon.lab.bi.telemetry.TelemetryRepository.*;

@Component
public class TelemetryRollup {
    private final TelemetryRepository repository;
    private final JdbcTemplate jdbc;
    private final BiLease lease;
    private final Duration stale;
    public TelemetryRollup(TelemetryRepository repository, JdbcTemplate jdbc, BiLease lease,
            @Value("${lab.bi.stale-minutes:15}") long minutes) {
        if (minutes<1 || minutes>1440) throw new IllegalArgumentException("Invalid stale-minutes");
        this.repository=repository;this.jdbc=jdbc;this.lease=lease;stale=Duration.ofMinutes(minutes);
    }
    public Duration staleAfter() { return stale; }
    public void day(LocalDate date,String token) {
        // Sealed days are immutable because their raw records may already be archived.
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM bi_summary_day WHERE summary_date=? AND sealed=1)",Boolean.class,date))) return;
        java.util.List<SavedHour> staged=new java.util.ArrayList<>();
        for (Device d:repository.devices()) {
            lease.renew(token);
            LocalDateTime start=d.createdAt().isAfter(date.atStartOfDay())?d.createdAt():date.atStartOfDay();
            LocalDateTime end=date.plusDays(1).atStartOfDay();
            if(d.deletedAt()!=null && d.deletedAt().isBefore(end)) end=d.deletedAt();
            if(!start.isBefore(end)) continue;
            var hours=TelemetryMath.calculate(repository.samples(d,start,end),start,end,d.kind().equals("AIR_CONDITION"),stale);
            hours.forEach(h -> staged.add(new SavedHour(d.id(),d.laboratoryId(),d.kind(),h)));
        }
        // Publish the whole day atomically. Readers never mix device generations.
        lease.guarded(token,() -> {
            jdbc.update("DELETE FROM bi_device_hourly WHERE hour_start>=? AND hour_start<?",date.atStartOfDay(),date.plusDays(1).atStartOfDay());
            staged.forEach(saved -> repository.save(new Device(saved.deviceId(),null,saved.laboratoryId(),saved.kind(),date.atStartOfDay()),saved.value()));
            jdbc.update("INSERT INTO bi_summary_day(summary_date,sealed,calculated_at) VALUES(?,0,NOW(3)) ON DUPLICATE KEY UPDATE calculated_at=NOW(3)",date);
        });
    }
}
