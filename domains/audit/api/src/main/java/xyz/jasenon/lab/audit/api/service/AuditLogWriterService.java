package xyz.jasenon.lab.audit.api.service;

import xyz.jasenon.lab.audit.api.model.AuditEvent;
import xyz.jasenon.lab.common.rpc.RpcResult;

/** Internal service contract for appending gateway audit events. */
public interface AuditLogWriterService {
    RpcResult<Void> append(AuditEvent event);
}
