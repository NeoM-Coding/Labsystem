package xyz.jasenon.lab.engine.runtime;


import java.time.Clock;
import java.util.Objects;

/** 汇合全局设备 Root、Runtime 生命周期与局部时间条件。 */
public final class RuntimeActionGroupEvaluator {

    private final Clock clock;

    public RuntimeActionGroupEvaluator(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public boolean shouldExecute(
            Runtime runtime,
            RuntimeActionGroup actionGroup,
            RuntimeSignal signal
    ) {
        return isCandidate(actionGroup, signal)
                && runtime.isActiveAt(clock.instant())
                && runtime.deviceConditionSatisfied(actionGroup.deviceConditionGroupId())
                && actionGroup.timeConditionGroup().allows(signal);
    }

    public boolean isRuntimeActive(Runtime runtime) {
        return runtime.isActiveAt(clock.instant());
    }

    private boolean isCandidate(RuntimeActionGroup actionGroup, RuntimeSignal signal) {
        if (signal instanceof RuntimeSignal.StateChanged changed) {
            return changed.targetsAll()
                    || changed.candidateActionGroupIds().contains(actionGroup.actionGroupId());
        }
        if (signal instanceof RuntimeSignal.TimePointOccurred point) {
            return actionGroup.timeConditionGroupId().equals(point.timeConditionGroupId());
        }
        return false;
    }
}
