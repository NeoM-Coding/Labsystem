package xyz.jasenon.lab.audit.service;

import org.apache.dubbo.config.annotation.DubboService;
import xyz.jasenon.lab.audit.api.model.AuditEvent;
import xyz.jasenon.lab.audit.api.service.AuditLogWriterService;
import xyz.jasenon.lab.audit.persistence.AuditLogStore;
import xyz.jasenon.lab.common.rpc.RpcResult;

@DubboService
public class AuditLogWriterServiceImpl implements AuditLogWriterService {

    private final AuditLogStore store;

    public AuditLogWriterServiceImpl(AuditLogStore store) {
        this.store = store;
    }

    @Override
    public RpcResult<Void> append(AuditEvent event) {
        store.append(event);
        return RpcResult.success();
    }
}
