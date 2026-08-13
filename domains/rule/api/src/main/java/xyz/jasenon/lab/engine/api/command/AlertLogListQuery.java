package xyz.jasenon.lab.engine.api.command;

import java.io.Serializable;
import java.time.Instant;

public record AlertLogListQuery(
        long current,
        long size,
        String runtimeId,
        String actionGroupId,
        String status,
        Instant matchedFrom,
        Instant matchedTo
) implements Serializable {
}
