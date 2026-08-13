package xyz.jasenon.lab.base.handler.authorization.user;

import org.springframework.stereotype.Component;
import xyz.jasenon.lab.auth.permission.Action;
import xyz.jasenon.lab.base.api.dto.UserDelete;
import xyz.jasenon.lab.base.handler.authorization.AbstractAppActionCommandHandler;

@Component
public class UserDeleteActionHandler extends AbstractAppActionCommandHandler<UserDelete> {

    public UserDeleteActionHandler() {
        super(UserDelete.class, Action.App.delete_user);
    }
}
