package xyz.jasenon.lab.audit.persistence;

import xyz.jasenon.lab.audit.handler.AuditFragment;
import xyz.jasenon.lab.audit.model.AuditEvent;
import xyz.jasenon.lab.audit.persistence.mapper.AuditLogMapper;

import java.util.UUID;
import java.util.function.Function;

public class MybatisAuditLogStore implements AuditLogStore {

    private static final String SEPARATOR = ",";
    private final AuditLogMapper mapper;

    public MybatisAuditLogStore(AuditLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void append(AuditEvent event) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setSubjectId(event.subjectId());
        entity.setSubjectName(event.subjectName());
        entity.setSubjectDisplayName(event.subjectDisplayName());
        entity.setOperation(event.operation());
        entity.setActions(join(event, fragment -> fragment.action().name()));
        entity.setObjectTypes(join(event, AuditFragment::objectType));
        entity.setObjectIds(join(event, AuditFragment::objectId));
        entity.setEventTypes(join(event, AuditFragment::eventType));
        entity.setDescription(subjectLabel(event) + " " + join(event, AuditFragment::description, "；"));
        entity.setTraceId(event.traceId());
        entity.setRequestId(event.requestId());
        entity.setOccurredAt(event.occurredAt());
        mapper.insert(entity);
    }

    private static String subjectLabel(AuditEvent event) {
        if (event.subjectDisplayName() != null && !event.subjectDisplayName().isBlank()) return event.subjectDisplayName();
        if (event.subjectName() != null && !event.subjectName().isBlank()) return event.subjectName();
        return event.subjectId();
    }

    private static String join(AuditEvent event, Function<AuditFragment, String> reader) {
        return join(event, reader, SEPARATOR);
    }

    private static String join(AuditEvent event, Function<AuditFragment, String> reader, String separator) {
        return event.fragments().stream().map(reader).filter(MybatisAuditLogStore::hasText).distinct().reduce((a, b) -> a + separator + b).orElse("");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
