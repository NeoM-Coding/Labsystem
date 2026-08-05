package xyz.jasenon.lab.audit.persistence;

import xyz.jasenon.lab.audit.model.AuditEvent;

public interface AuditLogStore {

    void append(AuditEvent event);
}
