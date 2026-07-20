package xyz.jasenon.lab.engine.api.command;

public record SmartStrategyStatusChange(String runtimeId, boolean enabled) {
    public SmartStrategyStatusChange {
        if (runtimeId == null || runtimeId.isBlank()) {
            throw new IllegalArgumentException("runtimeId must not be blank");
        }
        runtimeId = runtimeId.trim();
    }
}
