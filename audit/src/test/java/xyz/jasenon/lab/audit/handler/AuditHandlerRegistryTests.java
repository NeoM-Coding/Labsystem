package xyz.jasenon.lab.audit.handler;

import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.audit.api.AuditAction;
import xyz.jasenon.lab.audit.api.Loggable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditHandlerRegistryTests {

    @Test
    void dispatchesByExactRuntimeClassAndSkipsUnknownArguments() {
        AuditHandlerRegistry registry = new AuditHandlerRegistry(List.of(new SampleHandler()));

        assertThat(registry.handle(new SampleEvent("42")))
                .get()
                .extracting(AuditFragment::objectId, AuditFragment::description)
                .containsExactly("42", "编辑示例 42");
        assertThat(registry.handle("not-loggable")).isEmpty();
        assertThat(registry.handle(null)).isEmpty();
    }

    @Test
    void rejectsDuplicateHandlers() {
        assertThatThrownBy(() -> new AuditHandlerRegistry(List.of(new SampleHandler(), new SampleHandler())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(SampleEvent.class.getName());
    }

    record SampleEvent(String id) implements Loggable {
        @Override
        public String log() {
            return "编辑示例 " + id;
        }
    }

    static class SampleHandler extends AuditLogHandler<SampleEvent> {
        SampleHandler() {
            super(SampleEvent.class);
        }

        @Override
        protected AuditAction action(SampleEvent event) {
            return AuditAction.EDIT;
        }

        @Override
        protected String objectType(SampleEvent event) {
            return "sample";
        }

        @Override
        protected String objectId(SampleEvent event) {
            return event.id();
        }
    }
}
