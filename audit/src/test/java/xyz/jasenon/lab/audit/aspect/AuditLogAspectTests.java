package xyz.jasenon.lab.audit.aspect;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import xyz.jasenon.lab.audit.api.AuditAction;
import xyz.jasenon.lab.audit.api.Loggable;
import xyz.jasenon.lab.audit.api.annotation.Audited;
import xyz.jasenon.lab.audit.handler.AuditHandlerRegistry;
import xyz.jasenon.lab.audit.handler.AuditLogHandler;
import xyz.jasenon.lab.audit.model.AuditEvent;
import xyz.jasenon.lab.audit.persistence.AuditLogStore;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.context.UserContextHolder;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditLogAspectTests {

    private final RecordingStore store = new RecordingStore();

    @AfterEach
    void clearContext() {
        UserContextHolder.clear();
    }

    @Test
    void aggregatesAllHandledArgumentsIntoOneAuditEvent() {
        UserContextHolder.set(UserContext.builder().userId("u-1").username("admin").displayName("管理员").build());
        SampleService service = proxy();

        assertThat(service.change(new SampleEvent("1"), "ignored", new SampleEvent("2"))).isEqualTo("ok");

        assertThat(store.events).hasSize(1);
        AuditEvent event = store.events.get(0);
        assertThat(event.subjectId()).isEqualTo("u-1");
        assertThat(event.operation()).isEqualTo("sample.change");
        assertThat(event.fragments()).extracting(fragment -> fragment.objectId()).containsExactly("1", "2");
    }

    @Test
    void doesNotWriteAuditEventWhenBusinessMethodFails() {
        UserContextHolder.set(UserContext.builder().userId("u-1").build());
        SampleService service = proxy();

        assertThatThrownBy(() -> service.fail(new SampleEvent("1"))).isInstanceOf(IllegalStateException.class);
        assertThat(store.events).isEmpty();
    }

    private SampleService proxy() {
        AuditHandlerRegistry registry = new AuditHandlerRegistry(List.of(new SampleHandler()));
        AspectJProxyFactory factory = new AspectJProxyFactory(new SampleService());
        factory.addAspect(new AuditLogAspect(registry, store));
        return factory.getProxy();
    }

    static class SampleService {
        @Audited("sample.change")
        public String change(SampleEvent first, String ignored, SampleEvent second) {
            return "ok";
        }

        @Audited("sample.fail")
        public void fail(SampleEvent event) {
            throw new IllegalStateException("failed");
        }
    }

    record SampleEvent(String id) implements Loggable {
        @Override
        public String log() {
            return "编辑示例 " + id;
        }
    }

    static class SampleHandler extends AuditLogHandler<SampleEvent> {
        SampleHandler() { super(SampleEvent.class); }
        @Override protected AuditAction action(SampleEvent event) { return AuditAction.EDIT; }
        @Override protected String objectType(SampleEvent event) { return "sample"; }
        @Override protected String objectId(SampleEvent event) { return event.id(); }
    }

    static class RecordingStore implements AuditLogStore {
        final List<AuditEvent> events = new ArrayList<>();
        @Override public void append(AuditEvent event) { events.add(event); }
    }
}
