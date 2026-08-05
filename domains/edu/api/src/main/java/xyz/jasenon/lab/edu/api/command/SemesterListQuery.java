package xyz.jasenon.lab.edu.api.command;

import java.io.Serial;
import java.io.Serializable;

public record SemesterListQuery(String keyword) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
