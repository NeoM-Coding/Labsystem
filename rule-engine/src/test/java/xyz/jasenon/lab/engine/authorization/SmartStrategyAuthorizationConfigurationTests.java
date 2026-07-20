package xyz.jasenon.lab.engine.authorization;

import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.auth.command.ActionCommand;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.permission.Action;
import xyz.jasenon.lab.engine.api.command.SmartStrategyCreate;
import xyz.jasenon.lab.engine.api.command.SmartStrategyGet;
import xyz.jasenon.lab.engine.api.command.SmartStrategyStatusChange;
import xyz.jasenon.lab.engine.definition.RuntimeRevision;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmartStrategyAuthorizationConfigurationTests {

    private final SmartStrategyAuthorizationConfiguration configuration =
            new SmartStrategyAuthorizationConfiguration();
    private final UserContext context = UserContext.of("user-1", "operator", "Operator", List.of());

    @Test
    void mutationCommandsUseManagePermission() {
        ActionCommand command = configuration.smartStrategyCreateAuthorization()
                .handle(new SmartStrategyCreate(revision()), context);

        assertEquals(Action.App.manage_smart_strategy, command.action());
    }

    @Test
    void statusCommandsUseStatusPermission() {
        ActionCommand command = configuration.smartStrategyStatusAuthorization()
                .handle(new SmartStrategyStatusChange("runtime-1", true), context);

        assertEquals(Action.App.change_smart_strategy_status, command.action());
    }

    @Test
    void queryCommandsUseListPermission() {
        ActionCommand command = configuration.smartStrategyGetAuthorization()
                .handle(new SmartStrategyGet("runtime-1"), context);

        assertEquals(Action.App.list_smart_strategies, command.action());
    }

    private RuntimeRevision revision() {
        return new RuntimeRevision("runtime-1", true, null, null, List.of(), List.of(), List.of());
    }
}
