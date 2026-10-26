package xyz.jasenon.lab.engine.event;

import java.time.Instant;
import java.util.*;

/** One immutable occurrence batch, including simultaneous window transitions. */
public record TimePointEvent(String runtimeId, long generation, long timerToken,
                             Set<String> conditionIds, Map<String, Boolean> statuses,
                             Instant scheduledAt, Instant occurredAt) implements EngineEvent {
    public TimePointEvent {
        Objects.requireNonNull(runtimeId);
        conditionIds = Set.copyOf(conditionIds);
        statuses = Map.copyOf(statuses);
        Objects.requireNonNull(scheduledAt);
        Objects.requireNonNull(occurredAt);
    }
    public TimePointEvent(String runtimeId, long generation, long timerToken, Set<String> conditionIds,
                          Map<String, Boolean> statuses, Instant scheduledAt) {
        this(runtimeId, generation, timerToken, conditionIds, statuses, scheduledAt, scheduledAt);
    }
    public EventKey eventKey() { return new PointKey(runtimeId); }
    private record PointKey(String runtimeId) implements EventKey {
        public EventType type() { return EventType.TIME; }
        public String asString() { return "POINT:" + runtimeId; }
    }
}
