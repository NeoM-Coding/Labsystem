package xyz.jasenon.lab.audit.api.model;

import xyz.jasenon.lab.audit.api.AuditAction;

public record AuditFragment(
        AuditAction action,
        String objectType,
        String objectId,
        String eventType,
        String description
) implements java.io.Serializable {
    @java.io.Serial
    private static final long serialVersionUID = 1L;
}
