package xyz.jasenon.lab.audit.handler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AuditHandlerRegistry {

    private final Map<Class<?>, AuditLogHandler<?>> handlers;

    public AuditHandlerRegistry(List<AuditLogHandler<?>> handlers) {
        Map<Class<?>, AuditLogHandler<?>> registered = new HashMap<>();
        for (AuditLogHandler<?> handler : handlers) {
            AuditLogHandler<?> previous = registered.putIfAbsent(handler.eventType(), handler);
            if (previous != null) {
                throw new IllegalStateException("重复的审计事件 Handler: " + handler.eventType().getName());
            }
        }
        this.handlers = Map.copyOf(registered);
    }

    public Optional<AuditFragment> handle(Object argument) {
        return handle(argument, null);
    }

    public Optional<AuditFragment> handle(Object argument, Object result) {
        if (argument == null) return Optional.empty();
        AuditLogHandler<?> handler = handlers.get(argument.getClass());
        return handler == null ? Optional.empty() : Optional.of(handler.handle(argument, result));
    }
}
