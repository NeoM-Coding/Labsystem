package xyz.jasenon.lab.engine.runtime;

import xyz.jasenon.lab.engine.event.TimeEvent;

import java.util.Objects;
import java.util.Set;

/**
 * RuntimeScheduler 的输入信号。
 *
 * <p>状态变化允许合并，只需推演最新状态；TimePoint 必须逐个保留。</p>
 */
public sealed interface RuntimeSignal {

    record StateChanged(Set<String> candidateActionGroupIds) implements RuntimeSignal {

        public StateChanged {
            candidateActionGroupIds = candidateActionGroupIds == null
                    ? Set.of()
                    : Set.copyOf(candidateActionGroupIds);
        }

        public boolean targetsAll() {
            return candidateActionGroupIds.isEmpty();
        }
    }

    record TimePointOccurred(
            String timeConditionGroupId,
            TimeEvent event
    ) implements RuntimeSignal {

        public TimePointOccurred {
            Objects.requireNonNull(timeConditionGroupId, "timeConditionGroupId");
            Objects.requireNonNull(event, "event");
        }
    }

    static RuntimeSignal stateChanged() {
        return new StateChanged(Set.of());
    }

    static RuntimeSignal stateChanged(Set<String> candidateActionGroupIds) {
        return new StateChanged(candidateActionGroupIds);
    }

    static RuntimeSignal timePoint(TimeEvent event) {
        return new TimePointOccurred(event.key().timeConditionGroupId(), event);
    }
}
