package xyz.jasenon.lab.engine.api.command;

public record SmartStrategyGet(String runtimeId) {
    public SmartStrategyGet {
        if (runtimeId == null || runtimeId.isBlank()) {
            throw new IllegalArgumentException("runtimeId must not be blank");
        }
        runtimeId = runtimeId.trim();
    }
}
