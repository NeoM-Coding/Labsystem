package xyz.jasenon.lab.audit.api.model;

import java.io.Serial;
import java.io.Serializable;

public record AuditLogQuery(
        String subjectId,
        String action,
        String objectType,
        int limit
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public AuditLogQuery {
        limit = limit <= 0 ? 100 : Math.min(limit, 500);
    }
}
