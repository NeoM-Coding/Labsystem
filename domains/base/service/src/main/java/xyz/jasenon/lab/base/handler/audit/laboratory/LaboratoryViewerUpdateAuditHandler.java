package xyz.jasenon.lab.base.handler.audit.laboratory;

import org.springframework.stereotype.Component;
import xyz.jasenon.lab.audit.api.AuditAction;
import xyz.jasenon.lab.audit.handler.AuditLogHandler;
import xyz.jasenon.lab.base.api.dto.LaboratoryViewerUpdate;

@Component
public class LaboratoryViewerUpdateAuditHandler extends AuditLogHandler<LaboratoryViewerUpdate> {
    public LaboratoryViewerUpdateAuditHandler() {
        super(LaboratoryViewerUpdate.class);
    }

    @Override
    protected AuditAction action(LaboratoryViewerUpdate event) {
        return AuditAction.EDIT;
    }

    @Override
    protected String objectType(LaboratoryViewerUpdate event) {
        return "laboratory-members";
    }

    @Override
    protected String objectId(LaboratoryViewerUpdate event) {
        return event.laboratoryId();
    }
}
