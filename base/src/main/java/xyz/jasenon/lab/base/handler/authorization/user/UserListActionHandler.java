package xyz.jasenon.lab.base.handler.authorization.user;

import org.springframework.stereotype.Component;
import xyz.jasenon.lab.auth.permission.Action;
import xyz.jasenon.lab.base.api.dto.UserListQuery;
import xyz.jasenon.lab.base.handler.authorization.AbstractAppActionCommandHandler;

@Component
public class UserListActionHandler extends AbstractAppActionCommandHandler<UserListQuery> {

    public UserListActionHandler() {
        super(UserListQuery.class, Action.App.list_user);
    }
}
