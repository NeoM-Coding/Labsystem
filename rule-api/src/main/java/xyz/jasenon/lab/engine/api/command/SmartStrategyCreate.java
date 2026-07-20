package xyz.jasenon.lab.engine.api.command;

import xyz.jasenon.lab.engine.definition.RuntimeRevision;

import java.util.Objects;

public record SmartStrategyCreate(RuntimeRevision revision) {
    public SmartStrategyCreate {
        Objects.requireNonNull(revision, "revision");
    }
}
