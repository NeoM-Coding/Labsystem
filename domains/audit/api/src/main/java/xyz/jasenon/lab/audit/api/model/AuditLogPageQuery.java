package xyz.jasenon.lab.audit.api.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理员审计日志分页筛选条件。
 */
public record AuditLogPageQuery(
        long current,
        long size,
        String subjectId,
        String subjectName,
        String subjectDisplayName,
        String operation,
        String action,
        String objectType,
        String objectId,
        String eventType,
        String description,
        String traceId,
        String requestId,
        LocalDateTime occurredFrom,
        LocalDateTime occurredTo
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
