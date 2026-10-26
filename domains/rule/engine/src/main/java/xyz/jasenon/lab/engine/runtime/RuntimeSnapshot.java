package xyz.jasenon.lab.engine.runtime;

import java.util.Map;

public record RuntimeSnapshot(String runtimeId, long generation, String state, Map<String, Boolean> roots,
                              int pendingDecisions, boolean executing) {
    public RuntimeSnapshot {
        roots = Map.copyOf(roots);
    }
}
