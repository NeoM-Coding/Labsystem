package xyz.jasenon.lab.web.audit;

import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import xyz.jasenon.lab.audit.api.model.AuditEvent;
import xyz.jasenon.lab.audit.api.service.AuditLogWriterService;
import xyz.jasenon.lab.audit.persistence.AuditLogStore;
import xyz.jasenon.lab.observability.rpc.RpcClient;

@Component
@ConditionalOnProperty(prefix = "lab.audit", name = "store", havingValue = "rpc")
public class RpcAuditLogStore implements AuditLogStore {

    @DubboReference(check = false, retries = 0)
    private AuditLogWriterService writer;

    @Override
    public void append(AuditEvent event) {
        RpcClient.run(() -> writer.append(event));
    }
}
