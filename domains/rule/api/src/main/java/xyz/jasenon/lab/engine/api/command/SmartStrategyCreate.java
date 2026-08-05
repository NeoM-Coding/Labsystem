package xyz.jasenon.lab.engine.api.command;

import xyz.jasenon.lab.engine.definition.RuntimeRevision;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public record SmartStrategyCreate(RuntimeRevision revision) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public SmartStrategyCreate {
        Objects.requireNonNull(revision, "revision");
    }
}
