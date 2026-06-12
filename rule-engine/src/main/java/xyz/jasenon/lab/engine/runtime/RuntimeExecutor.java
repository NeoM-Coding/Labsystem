package xyz.jasenon.lab.engine.runtime;

import xyz.jasenon.lab.engine.action.ActionGroup;

@FunctionalInterface
public interface RuntimeExecutor {

    void execute(Runtime runtime, ActionGroup actionGroup);
}
