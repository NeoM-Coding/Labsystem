package xyz.jasenon.lab.audit.service;

import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.audit.api.model.AuditEvent;
import xyz.jasenon.lab.audit.persistence.AuditLogStore;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AuditLogWriterServiceImplTests {

    private final AuditLogStore store = mock(AuditLogStore.class);
    private final AuditLogWriterServiceImpl service = new AuditLogWriterServiceImpl(store);
    private final AuditEvent event = new AuditEvent("user-1", "admin", "管理员", "device.control",
            List.of(), "trace-1", "request-1", LocalDateTime.of(2026, 11, 5, 0, 0));

    @Test
    void appendsOriginalGatewaySnapshot() {
        assertThat(service.append(event).successful()).isTrue();
        verify(store).append(event);
    }

    @Test
    void propagatesStorageFailureToGateway() {
        doThrow(new IllegalStateException("storage unavailable")).when(store).append(event);
        assertThatThrownBy(() -> service.append(event)).isInstanceOf(IllegalStateException.class);
    }
}
