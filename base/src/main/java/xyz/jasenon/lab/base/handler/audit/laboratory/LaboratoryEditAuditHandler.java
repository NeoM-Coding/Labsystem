package xyz.jasenon.lab.base.handler.audit.laboratory;

import org.springframework.stereotype.Component;
import xyz.jasenon.lab.audit.api.AuditAction;
import xyz.jasenon.lab.audit.handler.AuditLogHandler;
import xyz.jasenon.lab.base.api.dto.LaboratoryEdit;

@Component
public class LaboratoryEditAuditHandler extends AuditLogHandler<LaboratoryEdit> {

    public LaboratoryEditAuditHandler() {
        super(LaboratoryEdit.class);
    }

    @Override
    protected AuditAction action(LaboratoryEdit event) {
        return AuditAction.EDIT;
    }

    @Override
    protected String objectType(LaboratoryEdit event) {
        return "laboratory";
    }

    @Override
    protected String objectId(LaboratoryEdit event) {
        return event.laboratoryId();
    }
}
