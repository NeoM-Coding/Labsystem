package xyz.jasenon.lab.engine.event;

import org.springframework.stereotype.Component;
import xyz.jasenon.lab.engine.runtime.Runtime;
import xyz.jasenon.lab.engine.runtime.RuntimeSignal;
import xyz.jasenon.lab.engine.time.TimeConditionGroup;

import java.util.Set;

/**
 * 更新时间窗口状态，并将 TimePoint 保留为不可合并的调度脉冲。
 */
@Component
public class TimeEventHandler {

    public RuntimeSignal handle(Runtime runtime, TimeEvent event) {
        TimeConditionGroup timeGroup = runtime.timeConditionGroup(event.key().timeConditionGroupId());
        if (timeGroup == null || !timeGroup.apply(event)) {
            return null;
        }
        Set<String> candidates = runtime.actionGroupIdsForTimeGroup(timeGroup.getGroupId());
        if (candidates.isEmpty()) {
            return null;
        }
        if (event.signal() == TimeSignal.TIME_POINT) {
            return RuntimeSignal.timePoint(event);
        }
        return RuntimeSignal.stateChanged(candidates);
    }
}
