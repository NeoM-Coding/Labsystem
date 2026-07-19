package xyz.jasenon.lab.base.handler.audit.user;

import org.springframework.stereotype.Component;
import xyz.jasenon.lab.audit.api.AuditAction;
import xyz.jasenon.lab.audit.handler.AuditLogHandler;
import xyz.jasenon.lab.base.api.dto.ContactUserCreate;
import xyz.jasenon.lab.base.api.model.User;

@Component
public class ContactUserCreateAuditHandler extends AuditLogHandler<ContactUserCreate> {

    public ContactUserCreateAuditHandler() {
        super(ContactUserCreate.class);
    }

    @Override
    protected AuditAction action(ContactUserCreate event) {
        return AuditAction.CREATE;
    }

    @Override
    protected String objectType(ContactUserCreate event) {
        return "contact";
    }

    @Override
    protected String objectId(ContactUserCreate event) {
        return "";
    }

    @Override
    protected String objectId(ContactUserCreate event, Object result) {
        return result instanceof User user ? user.getId() : "";
    }
}
