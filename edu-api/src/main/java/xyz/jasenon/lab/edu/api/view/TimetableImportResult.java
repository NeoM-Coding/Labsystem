package xyz.jasenon.lab.edu.api.view;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public record TimetableImportResult(
        int ok,
        int fail,
        List<TimetableImportError> errors
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public TimetableImportResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
