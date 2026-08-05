package xyz.jasenon.lab.base.handler.audit.laboratory;

import org.springframework.stereotype.Component;
import xyz.jasenon.lab.audit.api.AuditAction;
import xyz.jasenon.lab.audit.handler.AuditLogHandler;
import xyz.jasenon.lab.base.api.dto.LaboratoryDelete;

@Component
public class LaboratoryDeleteAuditHandler extends AuditLogHandler<LaboratoryDelete> {

    public LaboratoryDeleteAuditHandler() {
        super(LaboratoryDelete.class);
    }

    @Override
    protected AuditAction action(LaboratoryDelete event) {
        return AuditAction.DELETE;
    }

    @Override
    protected String objectType(LaboratoryDelete event) {
        return "laboratory";
    }

    @Override
    protected String objectId(LaboratoryDelete event) {
        return event.laboratoryId();
    }
}
