package xyz.jasenon.lab.audit.model;

import xyz.jasenon.lab.audit.handler.AuditFragment;

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
) {
}
