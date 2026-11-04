package xyz.jasenon.lab.web.audit;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.jasenon.lab.audit.api.model.AuditEvent;
import xyz.jasenon.lab.audit.api.service.AuditLogWriterService;
import xyz.jasenon.lab.common.rpc.RpcResult;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RpcAuditLogStoreTests {

    @Test
    void forwardsSnapshotOnceAndRejectsInvalidResponse() {
        var writer = mock(AuditLogWriterService.class);
        var store = new RpcAuditLogStore();
        ReflectionTestUtils.setField(store, "writer", writer);
        var event = new AuditEvent("user-1", "admin", "管理员", "device.control",
                List.of(), "trace-1", "request-1", LocalDateTime.of(2026, 11, 5, 0, 0));
        when(writer.append(event)).thenReturn(RpcResult.success()).thenReturn(null);

        store.append(event);
        verify(writer).append(event);
        assertThatThrownBy(() -> store.append(event)).isInstanceOf(RuntimeException.class);
        verify(writer, times(2)).append(event);
        verifyNoMoreInteractions(writer);
    }
}
