package xyz.jasenon.lab.engine.runtime;

import xyz.jasenon.lab.engine.action.ActionGroup;

import java.time.Clock;
import java.util.Objects;

/**
 * 统一判断 Runtime 生命周期、设备条件树和时间条件组。
 */
public final class ActionGroupEvaluator {

    private final Clock clock;

    public ActionGroupEvaluator(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public boolean shouldExecute(
            Runtime runtime,
            ActionGroup actionGroup,
            RuntimeSignal signal
    ) {
        if (!isCandidate(actionGroup, signal)
                || !isRuntimeActive(runtime)
                || !actionGroup.getDeviceConditionGroup().getRoot().isOk()) {
            return false;
        }
        return actionGroup.getTimeConditionGroup().allows(signal);
    }

    public boolean isRuntimeActive(Runtime runtime) {
        return runtime.isActiveAt(clock.instant());
    }

    private boolean isCandidate(ActionGroup actionGroup, RuntimeSignal signal) {
        if (signal instanceof RuntimeSignal.StateChanged stateChanged) {
            return stateChanged.targetsAll()
                    || stateChanged.candidateActionGroupIds().contains(actionGroup.getActionGroupId());
        }
        if (signal instanceof RuntimeSignal.TimePointOccurred point) {
            return actionGroup.getTimeConditionGroupId().equals(point.timeConditionGroupId());
        }
        return false;
    }
}
