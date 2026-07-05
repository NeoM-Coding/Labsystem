package xyz.jasenon.lab.engine.event;

import java.time.Instant;
import java.util.Objects;

/**
 * 一次精确的窗口边界或 TimePoint 发生事件。
 *
 * <p>occurrenceId 用于识别重复投递，同一个时间点只应推演一次。</p>
 */
public record TimeEvent(
        TimeEventKey key,
        TimeSignal signal,
        Instant scheduledAt,
        Instant occurredAt,
        String occurrenceId
) implements EngineEvent {

    public TimeEvent {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(signal, "signal");
        scheduledAt = scheduledAt == null ? Instant.now() : scheduledAt;
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        occurrenceId = requireText(occurrenceId, "occurrenceId");
    }

    public TimeEvent(
            String runtimeId,
            String timeConditionGroupId,
            String conditionId,
            TimeSignal signal,
            Instant scheduledAt,
            Instant occurredAt
    ) {
        this(
                new TimeEventKey(runtimeId, timeConditionGroupId, conditionId),
                signal,
                scheduledAt,
                occurredAt,
                occurrenceId(runtimeId, timeConditionGroupId, conditionId, signal, scheduledAt)
        );
    }

    @Override
    public EventKey eventKey() {
        return key;
    }

    private static String occurrenceId(
            String runtimeId,
            String timeConditionGroupId,
            String conditionId,
            TimeSignal signal,
            Instant scheduledAt
    ) {
        return runtimeId + ":" + timeConditionGroupId + ":" + conditionId + ":" + signal + ":"
                + Objects.requireNonNull(scheduledAt, "scheduledAt");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
