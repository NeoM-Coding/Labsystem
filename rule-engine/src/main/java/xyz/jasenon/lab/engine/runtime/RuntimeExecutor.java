package xyz.jasenon.lab.engine.runtime;

import xyz.jasenon.lab.engine.action.Action;
import xyz.jasenon.lab.engine.action.ActionGroup;

import java.util.concurrent.CompletableFuture;

/**
 * Executes one action from a satisfied action group.
 *
 * <p>The returned future defines the action lifetime. RuntimeScheduler keeps the
 * runtime in its single-flight state until all action futures in that inference complete.</p>
 */
@FunctionalInterface
public interface RuntimeExecutor {

    /**
     * @return a future that always describes the final action outcome
     */
    CompletableFuture<ActionExecutionResult> execute(Runtime runtime, ActionGroup actionGroup, Action action);
}
