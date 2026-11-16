package xyz.jasenon.lab.bi.config;

import xyz.jasenon.lab.common.error.ErrorCode;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.jasenon.lab.auth.SourceType;
import xyz.jasenon.lab.auth.command.ActionCommand;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.handler.ActionCommandHandler;
import xyz.jasenon.lab.auth.permission.Action;
import xyz.jasenon.lab.auth.service.AuthService;
import xyz.jasenon.lab.bi.api.query.EduDashboardQuery;

@Configuration
public class BiAuthorizationConfiguration {

    @Bean
    ActionCommandHandler<EduDashboardQuery> eduDashboardAuthorization() {
        return new ActionCommandHandler<>(EduDashboardQuery.class) {
            @Override
            protected ActionCommand toAction(EduDashboardQuery source, UserContext context) {
                return new ActionCommand(
                        SourceType.app,
                        AuthService.GLOBAL_APP_ID,
                        Action.App.edu_data_analysis,
                        SourceType.user,
                        context.getUserId()
                );
            }
        };
    }
    @Bean
    ActionCommandHandler<xyz.jasenon.lab.bi.api.query.TelemetryQuery> telemetryAuthorization() {
        return new ActionCommandHandler<>(xyz.jasenon.lab.bi.api.query.TelemetryQuery.class) {
            @Override protected ActionCommand toAction(xyz.jasenon.lab.bi.api.query.TelemetryQuery source, UserContext context) {
                if (source.kind()==null) throw new xyz.jasenon.lab.common.exception.BusinessException(ErrorCode.VALIDATION_FAILED,"请选择分析类型");
                return new ActionCommand(SourceType.app,AuthService.GLOBAL_APP_ID,
                    source.kind()==xyz.jasenon.lab.bi.api.query.TelemetryQuery.Kind.AIR_CONDITION
                        ? Action.App.air_condition_data_analysis : Action.App.circuit_break_data_analysis,
                    SourceType.user,context.getUserId());
            }
        };
    }
}
