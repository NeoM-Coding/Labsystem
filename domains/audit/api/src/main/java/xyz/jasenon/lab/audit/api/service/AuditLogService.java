package xyz.jasenon.lab.audit.api.service;

import xyz.jasenon.lab.audit.api.model.AuditLogQuery;
import xyz.jasenon.lab.audit.api.model.AuditLogPage;
import xyz.jasenon.lab.audit.api.model.AuditLogPageQuery;
import xyz.jasenon.lab.audit.api.model.AuditLogView;
import xyz.jasenon.lab.common.rpc.RpcResult;

import java.util.List;

public interface AuditLogService {

    RpcResult<List<AuditLogView>> query(AuditLogQuery query);

    RpcResult<AuditLogPage> page(AuditLogPageQuery query);
}
