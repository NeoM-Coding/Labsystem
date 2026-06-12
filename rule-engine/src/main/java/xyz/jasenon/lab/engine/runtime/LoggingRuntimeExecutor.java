package xyz.jasenon.lab.engine.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xyz.jasenon.lab.engine.action.ActionGroup;

@Component
public class LoggingRuntimeExecutor implements RuntimeExecutor {

    private static final Logger log = LoggerFactory.getLogger(LoggingRuntimeExecutor.class);

    @Override
    public void execute(Runtime runtime, ActionGroup actionGroup) {
        log.info(
                "[RuleEngine] action group triggered, runtime-id:{}, action-group-id:{}",
                runtime.getRuntimeId(),
                actionGroup.getActionGroupId()
        );
    }
}
