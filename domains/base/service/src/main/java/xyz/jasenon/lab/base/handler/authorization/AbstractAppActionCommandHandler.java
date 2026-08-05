package xyz.jasenon.lab.base.handler.authorization;

import xyz.jasenon.lab.auth.SourceType;
import xyz.jasenon.lab.auth.command.ActionCommand;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.handler.ActionCommandHandler;
import xyz.jasenon.lab.auth.permission.Action;
import xyz.jasenon.lab.auth.service.AuthService;

public abstract class AbstractAppActionCommandHandler<T> extends ActionCommandHandler<T> {

    private final Action.App action;

    protected AbstractAppActionCommandHandler(Class<T> commandType, Action.App action) {
        super(commandType);
        this.action = action;
    }

    @Override
    protected final ActionCommand toAction(T source, UserContext context) {
        return new ActionCommand(
                SourceType.app,
                AuthService.GLOBAL_APP_ID,
                action,
                SourceType.user,
                context.getUserId()
        );
    }
}
