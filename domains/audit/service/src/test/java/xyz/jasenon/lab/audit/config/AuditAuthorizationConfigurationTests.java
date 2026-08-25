package xyz.jasenon.lab.audit.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import xyz.jasenon.lab.audit.api.model.AuditLogPageQuery;
import xyz.jasenon.lab.auth.context.UserContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditAuthorizationConfigurationTests {

    @Test
    void auditAutoConfigurationImportsAuthorizationHandlers() {
        Import imported = AuditAutoConfiguration.class.getAnnotation(Import.class);

        assertTrue(java.util.List.of(imported.value()).contains(AuditAuthorizationConfiguration.class));
    }

    @Test
    void mapsPageQueryToGlobalAuditPermission() {
        var handler = new AuditAuthorizationConfiguration().auditLogPageAuthorization();
        var context = UserContext.builder().userId("user-1").build();

        var action = handler.handle(
                new AuditLogPageQuery(1, 20, null, null, null, null, null, null,
                        null, null, null, null, null, null, null),
                context
        );

        assertEquals("app", action.entityType().name());
        assertEquals("list_audit_logs", action.action().str());
        assertEquals("user-1", action.subjectId());
    }
}
