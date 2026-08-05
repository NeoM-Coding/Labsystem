package xyz.jasenon.lab.audit.handler;

import xyz.jasenon.lab.audit.api.Loggable;
import xyz.jasenon.lab.audit.api.AuditAction;

public abstract class AuditLogHandler<T extends Loggable> {

    private final Class<T> eventType;

    protected AuditLogHandler(Class<T> eventType) {
        this.eventType = eventType;
    }

    public final Class<T> eventType() {
        return eventType;
    }

    public final AuditFragment handle(Object source) {
        return handle(source, null);
    }

    public final AuditFragment handle(Object source, Object result) {
        T event = eventType.cast(source);
        return new AuditFragment(
                action(event),
                objectType(event),
                objectId(event, result),
                event.eventType().getName(),
                event.log()
        );
    }

    protected abstract AuditAction action(T event);

    protected abstract String objectType(T event);

    protected abstract String objectId(T event);

    protected String objectId(T event, Object result) {
        return objectId(event);
    }
}
