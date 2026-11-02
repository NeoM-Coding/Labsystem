package xyz.jasenon.lab.edu.api.command;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public record TimetableExportQuery(String semesterId, List<String> laboratoryIds) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
