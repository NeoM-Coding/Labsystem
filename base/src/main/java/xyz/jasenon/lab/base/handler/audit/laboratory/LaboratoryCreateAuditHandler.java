package xyz.jasenon.lab.base.handler.audit.laboratory;

import org.springframework.stereotype.Component;
import xyz.jasenon.lab.audit.api.AuditAction;
import xyz.jasenon.lab.audit.handler.AuditLogHandler;
import xyz.jasenon.lab.base.api.dto.LaboratoryCreate;
import xyz.jasenon.lab.base.api.model.Laboratory;

@Component
public class LaboratoryCreateAuditHandler extends AuditLogHandler<LaboratoryCreate> {

    public LaboratoryCreateAuditHandler() {
        super(LaboratoryCreate.class);
    }

    @Override
    protected AuditAction action(LaboratoryCreate event) {
        return AuditAction.CREATE;
    }

    @Override
    protected String objectType(LaboratoryCreate event) {
        return "laboratory";
    }

    @Override
    protected String objectId(LaboratoryCreate event) {
        return "";
    }

    @Override
    protected String objectId(LaboratoryCreate event, Object result) {
        return result instanceof Laboratory laboratory ? laboratory.getId() : "";
    }
}
