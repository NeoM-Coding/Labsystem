package xyz.jasenon.lab.audit.api.model;

import java.time.LocalDateTime;
import java.util.List;

public record AuditEvent(
        String subjectId,
        String subjectName,
        String subjectDisplayName,
        String operation,
        List<AuditFragment> fragments,
        String traceId,
        String requestId,
        LocalDateTime occurredAt
) implements java.io.Serializable {
    @java.io.Serial
    private static final long serialVersionUID = 1L;
}
