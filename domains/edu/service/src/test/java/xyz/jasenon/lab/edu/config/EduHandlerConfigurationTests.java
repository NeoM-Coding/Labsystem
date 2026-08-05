package xyz.jasenon.lab.edu.config;

import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.handler.ActionCommandHandler;
import xyz.jasenon.lab.audit.handler.AuditLogHandler;
import xyz.jasenon.lab.edu.api.command.SemesterCreate;
import xyz.jasenon.lab.edu.api.command.SemesterListQuery;
import xyz.jasenon.lab.edu.api.command.TimetableClear;
import xyz.jasenon.lab.edu.api.command.TimetableImport;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EduHandlerConfigurationTests {

    private final UserContext context = UserContext.builder().userId("user-1").build();

    @Test
    void authorizationHandlersMapExactDtoTypesToReservedAppActions() {
        EduAuthorizationConfiguration configuration = new EduAuthorizationConfiguration();
        assertAction(configuration.semesterListAuthorization(), new SemesterListQuery(null), "list_semester");
        assertAction(configuration.semesterCreateAuthorization(),
                new SemesterCreate("2026-2027 第1学期", LocalDate.now(), LocalDate.now().plusDays(1)),
                "manage_semester");
        assertAction(configuration.timetableClearAuthorization(),
                new TimetableClear("semester-1", "lab-1"), "manage_timetable");
    }

    @Test
    void importAuditIsOneAggregateOperation() {
        EduAuditConfiguration configuration = new EduAuditConfiguration();
        AuditLogHandler<TimetableImport> handler = configuration.timetableImportAudit();
        var fragment = handler.handle(new TimetableImport(
                "semester-1", "lab-1", "schedule.xlsx", new byte[]{1}
        ));
        assertEquals("timetable", fragment.objectType());
        assertEquals("semester-1:lab-1", fragment.objectId());
        assertEquals("向实验室「lab-1」导入学期「semester-1」课表", fragment.description());
    }

    private <T> void assertAction(ActionCommandHandler<T> handler, T command, String expected) {
        assertEquals(expected, handler.handle(command, context).action().str());
    }
}
