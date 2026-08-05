package xyz.jasenon.lab.base.handler.authorization.user;

import org.springframework.stereotype.Component;
import xyz.jasenon.lab.auth.permission.Action;
import xyz.jasenon.lab.base.api.dto.UserCreate;
import xyz.jasenon.lab.base.handler.authorization.AbstractAppActionCommandHandler;

@Component
public class UserCreateActionHandler extends AbstractAppActionCommandHandler<UserCreate> {

    public UserCreateActionHandler() {
        super(UserCreate.class, Action.App.create_user);
    }
}
