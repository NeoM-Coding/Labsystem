package xyz.jasenon.lab.audit.api.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public record AuditLogPage(
        List<AuditLogView> records,
        long total,
        long current,
        long size,
        long pages
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public AuditLogPage {
        records = records == null ? List.of() : List.copyOf(records);
    }
}
