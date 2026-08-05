package xyz.jasenon.lab.edu.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.jasenon.lab.audit.api.AuditAction;
import xyz.jasenon.lab.audit.api.Loggable;
import xyz.jasenon.lab.audit.handler.AuditLogHandler;
import xyz.jasenon.lab.edu.api.command.SemesterCreate;
import xyz.jasenon.lab.edu.api.command.SemesterDelete;
import xyz.jasenon.lab.edu.api.command.SemesterUpdate;
import xyz.jasenon.lab.edu.api.command.TimetableClear;
import xyz.jasenon.lab.edu.api.command.TimetableCreate;
import xyz.jasenon.lab.edu.api.command.TimetableDelete;
import xyz.jasenon.lab.edu.api.command.TimetableImport;
import xyz.jasenon.lab.edu.api.command.TimetableUpdate;

import java.util.function.Function;

@Configuration
public class EduAuditConfiguration {

    @Bean AuditLogHandler<SemesterCreate> semesterCreateAudit() {
        return handler(SemesterCreate.class, AuditAction.CREATE, "semester", ignored -> "");
    }
    @Bean AuditLogHandler<SemesterUpdate> semesterUpdateAudit() {
        return handler(SemesterUpdate.class, AuditAction.EDIT, "semester", SemesterUpdate::semesterId);
    }
    @Bean AuditLogHandler<SemesterDelete> semesterDeleteAudit() {
        return handler(SemesterDelete.class, AuditAction.DELETE, "semester", SemesterDelete::semesterId);
    }
    @Bean AuditLogHandler<TimetableCreate> timetableCreateAudit() {
        return handler(TimetableCreate.class, AuditAction.CREATE, "timetable", ignored -> "");
    }
    @Bean AuditLogHandler<TimetableUpdate> timetableUpdateAudit() {
        return handler(TimetableUpdate.class, AuditAction.EDIT, "timetable", TimetableUpdate::timetableId);
    }
    @Bean AuditLogHandler<TimetableDelete> timetableDeleteAudit() {
        return handler(TimetableDelete.class, AuditAction.DELETE, "timetable", TimetableDelete::timetableId);
    }
    @Bean AuditLogHandler<TimetableClear> timetableClearAudit() {
        return handler(TimetableClear.class, AuditAction.DELETE, "timetable",
                command -> command.semesterId() + ":" + command.laboratoryId());
    }
    @Bean AuditLogHandler<TimetableImport> timetableImportAudit() {
        return handler(TimetableImport.class, AuditAction.CREATE, "timetable",
                command -> command.semesterId() + ":" + command.laboratoryId());
    }

    private static <T extends Loggable> AuditLogHandler<T> handler(
            Class<T> type,
            AuditAction action,
            String objectType,
            Function<T, String> objectId
    ) {
        return new AuditLogHandler<>(type) {
            @Override protected AuditAction action(T event) {
                return action;
            }
            @Override protected String objectType(T event) {
                return objectType;
            }
            @Override protected String objectId(T event) {
                return objectId.apply(event);
            }
        };
    }
}
