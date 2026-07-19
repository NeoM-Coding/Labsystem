package xyz.jasenon.lab.auth.handler;

import xyz.jasenon.lab.auth.command.ActionCommand;
import xyz.jasenon.lab.auth.context.UserContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ActionCommandHandlerRegistry {

    private final Map<Class<?>, ActionCommandHandler<?>> handlers;

    public ActionCommandHandlerRegistry(List<ActionCommandHandler<?>> handlers) {
        Map<Class<?>, ActionCommandHandler<?>> registered = new HashMap<>();
        for (ActionCommandHandler<?> handler : handlers) {
            ActionCommandHandler<?> previous = registered.putIfAbsent(handler.commandType(), handler);
            if (previous != null) {
                throw new IllegalStateException("重复的授权 Command Handler: " + handler.commandType().getName());
            }
        }
        this.handlers = Map.copyOf(registered);
    }

    public Optional<ActionCommand> handle(Object argument, UserContext context) {
        if (argument == null) return Optional.empty();
        ActionCommandHandler<?> handler = handlers.get(argument.getClass());
        return handler == null ? Optional.empty() : Optional.of(handler.handle(argument, context));
    }
}
