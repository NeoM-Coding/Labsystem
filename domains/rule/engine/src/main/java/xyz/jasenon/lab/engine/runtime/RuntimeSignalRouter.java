package xyz.jasenon.lab.engine.runtime;

import xyz.jasenon.lab.engine.eval.v2.EvalUpdate;
import xyz.jasenon.lab.engine.event.TimeEvent;
import xyz.jasenon.lab.engine.event.TimeSignal;
import xyz.jasenon.lab.engine.time.TimeConditionGroup;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 把全局 Forest 根变化与 Runtime 私有时间事件转换为调度信号。 */
public final class RuntimeSignalRouter {

    /**
     * Runtime 激活时同步读取一次当前设备 Root 与时间窗口，只把此刻已经成立的
     * ActionGroup 交给异步 Scheduler。这样初始为假的全量信号不会在稍后执行时
     * 读取到更新后的 Root，并与真正触发该变化的设备事件重复执行。
     */
    public RuntimeSignal routeActivation(Runtime runtime) {
        Objects.requireNonNull(runtime, "runtime");
        RuntimeSignal probe = RuntimeSignal.stateChanged();
        Set<String> candidates = runtime.actionGroups().stream()
                .filter(group -> runtime.deviceConditionSatisfied(group.deviceConditionGroupId()))
                .filter(group -> group.timeConditionGroup().allows(probe))
                .map(RuntimeActionGroup::actionGroupId)
                .collect(Collectors.toUnmodifiableSet());
        return candidates.isEmpty() ? null : RuntimeSignal.stateChanged(candidates);
    }

    public RuntimeSignal route(Runtime runtime, EvalUpdate update) {
        Objects.requireNonNull(runtime, "runtime");
        Set<String> candidates = runtime.actionGroupIdsFor(update);
        return candidates.isEmpty() ? null : RuntimeSignal.stateChanged(candidates);
    }

    public RuntimeSignal route(Runtime runtime, TimeEvent event) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(event, "event");
        if (!runtime.runtimeId().equals(event.key().runtimeId())) {
            return null;
        }
        TimeConditionGroup timeGroup = runtime.timeConditionGroup(
                event.key().timeConditionGroupId()
        );
        if (timeGroup == null || !timeGroup.apply(event)) {
            return null;
        }
        Set<String> candidates = runtime.actionGroupIdsForTimeGroup(timeGroup.getGroupId());
        if (candidates.isEmpty()) {
            return null;
        }
        return event.signal() == TimeSignal.TIME_POINT
                ? RuntimeSignal.timePoint(event)
                : RuntimeSignal.stateChanged(candidates);
    }
}
