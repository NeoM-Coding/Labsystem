package xyz.jasenon.lab.audit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.jasenon.lab.audit.api.model.AuditLogPageQuery;
import xyz.jasenon.lab.auth.SourceType;
import xyz.jasenon.lab.auth.command.ActionCommand;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.handler.ActionCommandHandler;
import xyz.jasenon.lab.auth.permission.Action;
import xyz.jasenon.lab.auth.service.AuthService;

@Configuration(proxyBeanMethods = false)
public class AuditAuthorizationConfiguration {

    @Bean
    ActionCommandHandler<AuditLogPageQuery> auditLogPageAuthorization() {
        return new ActionCommandHandler<>(AuditLogPageQuery.class) {
            @Override
            protected ActionCommand toAction(AuditLogPageQuery source, UserContext context) {
                return new ActionCommand(
                        SourceType.app,
                        AuthService.GLOBAL_APP_ID,
                        Action.App.list_audit_logs,
                        SourceType.user,
                        context.getUserId()
                );
            }
        };
    }
}
