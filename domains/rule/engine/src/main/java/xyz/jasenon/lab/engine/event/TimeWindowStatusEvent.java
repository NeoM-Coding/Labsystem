package xyz.jasenon.lab.engine.event;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * A complete window-state batch for one runtime; token fences cancelled timers.
 */
public record TimeWindowStatusEvent(String runtimeId, long generation, long timerToken,
                                    Map<String, Boolean> statuses, Instant occurredAt) implements EngineEvent {
    public TimeWindowStatusEvent {
        Objects.requireNonNull(runtimeId);
        statuses = Map.copyOf(statuses);
        Objects.requireNonNull(occurredAt);
    }

    @Override
    public EventKey eventKey() {
        return new WindowKey(runtimeId);
    }

    private record WindowKey(String runtimeId) implements EventKey {
        public EventType type() {
            return EventType.TIME;
        }

        public String asString() {
            return "WINDOW:" + runtimeId;
        }
    }
}
