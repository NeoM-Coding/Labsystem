package xyz.jasenon.lab.engine.authorization;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.jasenon.lab.auth.SourceType;
import xyz.jasenon.lab.auth.command.ActionCommand;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.handler.ActionCommandHandler;
import xyz.jasenon.lab.auth.permission.Action;
import xyz.jasenon.lab.auth.service.AuthService;
import xyz.jasenon.lab.engine.api.command.SmartStrategyCreate;
import xyz.jasenon.lab.engine.api.command.SmartStrategyDelete;
import xyz.jasenon.lab.engine.api.command.SmartStrategyGet;
import xyz.jasenon.lab.engine.api.command.SmartStrategyListQuery;
import xyz.jasenon.lab.engine.api.command.SmartStrategyStatusChange;
import xyz.jasenon.lab.engine.api.command.SmartStrategyUpdate;

@Configuration(proxyBeanMethods = false)
public class SmartStrategyAuthorizationConfiguration {

    @Bean
    ActionCommandHandler<SmartStrategyCreate> smartStrategyCreateAuthorization() {
        return appHandler(SmartStrategyCreate.class, Action.App.manage_smart_strategy);
    }

    @Bean
    ActionCommandHandler<SmartStrategyUpdate> smartStrategyUpdateAuthorization() {
        return appHandler(SmartStrategyUpdate.class, Action.App.manage_smart_strategy);
    }

    @Bean
    ActionCommandHandler<SmartStrategyDelete> smartStrategyDeleteAuthorization() {
        return appHandler(SmartStrategyDelete.class, Action.App.manage_smart_strategy);
    }

    @Bean
    ActionCommandHandler<SmartStrategyStatusChange> smartStrategyStatusAuthorization() {
        return appHandler(SmartStrategyStatusChange.class, Action.App.change_smart_strategy_status);
    }

    @Bean
    ActionCommandHandler<SmartStrategyGet> smartStrategyGetAuthorization() {
        return appHandler(SmartStrategyGet.class, Action.App.list_smart_strategies);
    }

    @Bean
    ActionCommandHandler<SmartStrategyListQuery> smartStrategyListAuthorization() {
        return appHandler(SmartStrategyListQuery.class, Action.App.list_smart_strategies);
    }

    private static <T> ActionCommandHandler<T> appHandler(Class<T> commandType, Action.App permission) {
        return new ActionCommandHandler<>(commandType) {
            @Override
            protected ActionCommand toAction(T source, UserContext context) {
                return new ActionCommand(
                        SourceType.app,
                        AuthService.GLOBAL_APP_ID,
                        permission,
                        SourceType.user,
                        context.getUserId()
                );
            }
        };
    }
}
