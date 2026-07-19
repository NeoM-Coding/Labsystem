package xyz.jasenon.lab.audit.handler;

import xyz.jasenon.lab.audit.api.AuditAction;

public record AuditFragment(
        AuditAction action,
        String objectType,
        String objectId,
        String eventType,
        String description
) {
}
