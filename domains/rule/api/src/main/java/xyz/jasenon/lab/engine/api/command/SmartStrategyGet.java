package xyz.jasenon.lab.engine.api.command;

import java.io.Serial;
import java.io.Serializable;

public record SmartStrategyGet(String runtimeId) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public SmartStrategyGet {
        if (runtimeId == null || runtimeId.isBlank()) {
            throw new IllegalArgumentException("runtimeId must not be blank");
        }
        runtimeId = runtimeId.trim();
    }
}
