package xyz.jasenon.lab.base.handler.audit.user;

import org.springframework.stereotype.Component;
import xyz.jasenon.lab.audit.api.AuditAction;
import xyz.jasenon.lab.audit.handler.AuditLogHandler;
import xyz.jasenon.lab.base.api.dto.UserDelete;

@Component
public class UserDeleteAuditHandler extends AuditLogHandler<UserDelete> {

    public UserDeleteAuditHandler() {
        super(UserDelete.class);
    }

    @Override
    protected AuditAction action(UserDelete event) {
        return AuditAction.DELETE;
    }

    @Override
    protected String objectType(UserDelete event) {
        return "user";
    }

    @Override
    protected String objectId(UserDelete event) {
        return event.userId();
    }
}
