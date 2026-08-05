package xyz.jasenon.lab.edu.api.command;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public record TimetableListQuery(String semesterId, List<String> laboratoryIds) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public TimetableListQuery {
        laboratoryIds = laboratoryIds == null ? List.of() : List.copyOf(laboratoryIds);
    }
}
