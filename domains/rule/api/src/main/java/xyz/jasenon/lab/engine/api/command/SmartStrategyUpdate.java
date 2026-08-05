package xyz.jasenon.lab.engine.api.command;

import xyz.jasenon.lab.engine.definition.RuntimeRevision;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public record SmartStrategyUpdate(String runtimeId, RuntimeRevision revision) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public SmartStrategyUpdate {
        runtimeId = requireText(runtimeId);
        Objects.requireNonNull(revision, "revision");
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("runtimeId must not be blank");
        }
        return value.trim();
    }
}
