package xyz.jasenon.lab.base.handler.authorization.user;

import org.springframework.stereotype.Component;
import xyz.jasenon.lab.auth.permission.Action;
import xyz.jasenon.lab.base.api.dto.UserPermissionQuery;
import xyz.jasenon.lab.base.handler.authorization.AbstractAppActionCommandHandler;

@Component
public class UserPermissionListActionHandler
        extends AbstractAppActionCommandHandler<UserPermissionQuery> {

    public UserPermissionListActionHandler() {
        super(UserPermissionQuery.class, Action.App.list_user_permissions);
    }
}
