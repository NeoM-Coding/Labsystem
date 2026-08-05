package xyz.jasenon.lab.engine.event;

import java.util.Objects;

/**
 * 时间事件精确指向某个 Runtime、可复用时间条件组及其内部时间条件。
 */
public record TimeEventKey(
        String runtimeId,
        String timeConditionGroupId,
        String conditionId
) implements EventKey {

    public TimeEventKey {
        runtimeId = requireText(runtimeId, "runtimeId");
        timeConditionGroupId = requireText(timeConditionGroupId, "timeConditionGroupId");
        conditionId = requireText(conditionId, "conditionId");
    }

    @Override
    public EventType type() {
        return EventType.TIME;
    }

    @Override
    public String asString() {
        return EventType.TIME + ":" + runtimeId + ":" + timeConditionGroupId + ":" + conditionId;
    }

    @Override
    public String toString() {
        return asString();
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
