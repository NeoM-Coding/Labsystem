package xyz.jasenon.lab.edu.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.jasenon.lab.auth.SourceType;
import xyz.jasenon.lab.auth.command.ActionCommand;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.handler.ActionCommandHandler;
import xyz.jasenon.lab.auth.permission.Action;
import xyz.jasenon.lab.auth.service.AuthService;
import xyz.jasenon.lab.edu.api.command.SemesterCreate;
import xyz.jasenon.lab.edu.api.command.SemesterDelete;
import xyz.jasenon.lab.edu.api.command.SemesterListQuery;
import xyz.jasenon.lab.edu.api.command.SemesterUpdate;
import xyz.jasenon.lab.edu.api.command.TimetableClear;
import xyz.jasenon.lab.edu.api.command.TimetableCreate;
import xyz.jasenon.lab.edu.api.command.TimetableDelete;
import xyz.jasenon.lab.edu.api.command.TimetableImport;
import xyz.jasenon.lab.edu.api.command.TimetableListQuery;
import xyz.jasenon.lab.edu.api.command.TimetableUpdate;

@Configuration
public class EduAuthorizationConfiguration {

    @Bean ActionCommandHandler<SemesterListQuery> semesterListAuthorization() {
        return app(SemesterListQuery.class, Action.App.list_semester);
    }
    @Bean ActionCommandHandler<SemesterCreate> semesterCreateAuthorization() {
        return app(SemesterCreate.class, Action.App.manage_semester);
    }
    @Bean ActionCommandHandler<SemesterUpdate> semesterUpdateAuthorization() {
        return app(SemesterUpdate.class, Action.App.manage_semester);
    }
    @Bean ActionCommandHandler<SemesterDelete> semesterDeleteAuthorization() {
        return app(SemesterDelete.class, Action.App.manage_semester);
    }
    @Bean ActionCommandHandler<TimetableListQuery> timetableListAuthorization() {
        return app(TimetableListQuery.class, Action.App.view_timetable);
    }
    @Bean ActionCommandHandler<TimetableCreate> timetableCreateAuthorization() {
        return app(TimetableCreate.class, Action.App.manage_timetable);
    }
    @Bean ActionCommandHandler<TimetableUpdate> timetableUpdateAuthorization() {
        return app(TimetableUpdate.class, Action.App.manage_timetable);
    }
    @Bean ActionCommandHandler<TimetableDelete> timetableDeleteAuthorization() {
        return app(TimetableDelete.class, Action.App.manage_timetable);
    }
    @Bean ActionCommandHandler<TimetableClear> timetableClearAuthorization() {
        return app(TimetableClear.class, Action.App.manage_timetable);
    }
    @Bean ActionCommandHandler<TimetableImport> timetableImportAuthorization() {
        return app(TimetableImport.class, Action.App.manage_timetable);
    }

    private static <T> ActionCommandHandler<T> app(Class<T> type, Action.App action) {
        return new ActionCommandHandler<>(type) {
            @Override
            protected ActionCommand toAction(T source, UserContext context) {
                return new ActionCommand(
                        SourceType.app,
                        AuthService.GLOBAL_APP_ID,
                        action,
                        SourceType.user,
                        context.getUserId()
                );
            }
        };
    }
}
