package xyz.jasenon.lab.audit.api.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

public record AuditLogView(
        String id,
        String subjectId,
        String subjectName,
        String subjectDisplayName,
        String operation,
        String actions,
        String objectTypes,
        String objectIds,
        String eventTypes,
        String description,
        String traceId,
        String requestId,
        LocalDateTime occurredAt
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
