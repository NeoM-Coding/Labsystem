package xyz.jasenon.lab.engine.execution;

import xyz.jasenon.lab.engine.action.Action;
import xyz.jasenon.lab.engine.action.ActionExecutionResult;
import xyz.jasenon.lab.engine.plan.RuntimePlan.ActionGroup;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface RuntimeExecutor {
    CompletableFuture<ActionExecutionResult> execute(ExecutionContext runtime, ActionGroup group, Action action);
}
