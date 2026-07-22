package xyz.jasenon.lab.common.realtime;

import java.io.Serializable;
import java.time.Instant;

public record UserContextChangedEvent(
        String userId,
        Operation operation,
        Instant occurredAt
) implements Serializable {

    public enum Operation {
        UPSERT,
        DELETE
    }
}
