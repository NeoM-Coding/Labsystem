package xyz.jasenon.lab.edu.api.view;

import java.io.Serial;
import java.io.Serializable;

public record TimetableImportError(
        Integer rowIndex,
        Integer columnIndex,
        String rawContent,
        String reason
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
