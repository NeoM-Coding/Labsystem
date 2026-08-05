package xyz.jasenon.lab.base.handler.audit.user;

import org.springframework.stereotype.Component;
import xyz.jasenon.lab.audit.api.AuditAction;
import xyz.jasenon.lab.audit.handler.AuditLogHandler;
import xyz.jasenon.lab.base.api.dto.UserCreate;
import xyz.jasenon.lab.base.api.model.User;

@Component
public class UserCreateAuditHandler extends AuditLogHandler<UserCreate> {

    public UserCreateAuditHandler() {
        super(UserCreate.class);
    }

    @Override
    protected AuditAction action(UserCreate event) {
        return AuditAction.CREATE;
    }

    @Override
    protected String objectType(UserCreate event) {
        return "user";
    }

    @Override
    protected String objectId(UserCreate event) {
        return "";
    }

    @Override
    protected String objectId(UserCreate event, Object result) {
        return result instanceof User user ? user.getId() : "";
    }
}
