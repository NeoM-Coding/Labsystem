package xyz.jasenon.lab.bi.telemetry;

import xyz.jasenon.lab.common.error.ErrorCode;
import xyz.jasenon.lab.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.UUID;

/** Database-clock leases with a fencing check inside each mutating transaction. */
@Component
public class BiLease {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    public BiLease(JdbcTemplate jdbc, PlatformTransactionManager manager) { this.jdbc=jdbc; tx=new TransactionTemplate(manager); }
    public String acquire() {
        String token=UUID.randomUUID().toString();
        jdbc.update("INSERT IGNORE INTO bi_job_lease(job_name,owner,expires_at) VALUES('telemetry','',NOW(3))");
        return jdbc.update("UPDATE bi_job_lease SET owner=?,expires_at=DATE_ADD(NOW(3),INTERVAL 5 MINUTE) WHERE job_name='telemetry' AND expires_at<=NOW(3)",token)==1 ? token : null;
    }
    public void renew(String token) {
        if (jdbc.update("UPDATE bi_job_lease SET expires_at=DATE_ADD(NOW(3),INTERVAL 5 MINUTE) WHERE job_name='telemetry' AND owner=? AND expires_at>NOW(3)",token)!=1)
            throw new BusinessException(ErrorCode.BI_LEASE_LOST);
    }
    public void guarded(String token, Runnable action) {
        tx.executeWithoutResult(status -> {
            var owners=jdbc.queryForList("SELECT owner FROM bi_job_lease WHERE job_name='telemetry' AND owner=? AND expires_at>NOW(3) FOR UPDATE",String.class,token);
            if (owners.isEmpty()) throw new BusinessException(ErrorCode.BI_LEASE_LOST);
            action.run();
        });
        renew(token);
    }
    public void release(String token) {
        jdbc.update("UPDATE bi_job_lease SET expires_at=NOW(3) WHERE job_name='telemetry' AND owner=?",token);
    }
}
