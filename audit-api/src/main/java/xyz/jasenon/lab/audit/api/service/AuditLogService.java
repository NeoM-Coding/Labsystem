package xyz.jasenon.lab.audit.api.service;

import xyz.jasenon.lab.audit.api.model.AuditLogQuery;
import xyz.jasenon.lab.audit.api.model.AuditLogView;

import java.util.List;

public interface AuditLogService {

    List<AuditLogView> query(AuditLogQuery query);
}
