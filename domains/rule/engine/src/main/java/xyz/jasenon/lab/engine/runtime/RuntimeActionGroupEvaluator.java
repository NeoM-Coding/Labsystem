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
        return xyz.jasenon.lab.observability.context.Tracing.operation("rule.evaluate")
                .attribute("rule.runtime_id", runtime.runtimeId())
                .attribute("rule.generation", runtime.generation())
                .attribute("rule.action_group", actionGroup.actionGroupId()).get(() -> {
            boolean candidate = isCandidate(actionGroup, signal);
            boolean active = runtime.isActiveAt(clock.instant());
            boolean device = runtime.deviceConditionSatisfied(actionGroup.deviceConditionGroupId());
            boolean time = actionGroup.timeConditionGroup().allows(signal);
            String reason = !candidate ? "not_candidate" : !active ? "runtime_inactive"
                    : !device ? "device_condition_false" : !time ? "outside_time_window" : "matched";
            xyz.jasenon.lab.observability.context.Tracing.attribute("rule.device_satisfied", device);
            xyz.jasenon.lab.observability.context.Tracing.attribute("rule.time_satisfied", time);
            xyz.jasenon.lab.observability.context.Tracing.attribute("rule.reason", reason);
            org.slf4j.LoggerFactory.getLogger(RuntimeActionGroupEvaluator.class).info(
                    "rule_decision runtime_id={} action_group={} reason={} device_satisfied={} time_satisfied={}",
                    runtime.runtimeId(), actionGroup.actionGroupId(), reason, device, time);
            return candidate && active && device && time;
        });
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
