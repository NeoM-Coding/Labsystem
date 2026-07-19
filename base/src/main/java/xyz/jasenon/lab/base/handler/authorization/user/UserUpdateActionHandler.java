package xyz.jasenon.lab.base.handler.authorization.user;

import org.springframework.stereotype.Component;
import xyz.jasenon.lab.auth.permission.Action;
import xyz.jasenon.lab.base.api.dto.UserAuthorizationUpdate;
import xyz.jasenon.lab.base.handler.authorization.AbstractAppActionCommandHandler;

@Component
public class UserUpdateActionHandler
        extends AbstractAppActionCommandHandler<UserAuthorizationUpdate> {

    public UserUpdateActionHandler() {
        super(UserAuthorizationUpdate.class, Action.App.edit_user);
    }
}
