package xyz.jasenon.lab.base.handler.audit.user;

import org.springframework.stereotype.Component;
import xyz.jasenon.lab.audit.api.AuditAction;
import xyz.jasenon.lab.audit.handler.AuditLogHandler;
import xyz.jasenon.lab.base.api.dto.UserAuthorizationUpdate;

@Component
public class UserUpdateAuditHandler extends AuditLogHandler<UserAuthorizationUpdate> {

    public UserUpdateAuditHandler() {
        super(UserAuthorizationUpdate.class);
    }

    @Override
    protected AuditAction action(UserAuthorizationUpdate event) {
        return AuditAction.EDIT;
    }

    @Override
    protected String objectType(UserAuthorizationUpdate event) {
        return "user";
    }

    @Override
    protected String objectId(UserAuthorizationUpdate event) {
        return event.user() == null ? "" : event.user().getId();
    }
}
