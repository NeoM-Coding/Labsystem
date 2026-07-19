package xyz.jasenon.lab.auth.handler;

import xyz.jasenon.lab.auth.command.ActionCommand;
import xyz.jasenon.lab.auth.context.UserContext;

public abstract class ActionCommandHandler<T> {

    private final Class<T> commandType;

    protected ActionCommandHandler(Class<T> commandType) {
        this.commandType = commandType;
    }

    public final Class<T> commandType() {
        return commandType;
    }

    public final ActionCommand handle(Object source, UserContext context) {
        return toAction(commandType.cast(source), context);
    }

    protected abstract ActionCommand toAction(T source, UserContext context);
}
