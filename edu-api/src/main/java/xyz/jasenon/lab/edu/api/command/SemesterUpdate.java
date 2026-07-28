package xyz.jasenon.lab.edu.api.command;

import xyz.jasenon.lab.audit.api.Loggable;

import java.io.Serial;
import java.time.LocalDate;

public record SemesterUpdate(
        String semesterId,
        String name,
        LocalDate startDate,
        LocalDate endDate
) implements Loggable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String log() {
        return "修改学期「" + (name == null || name.isBlank() ? semesterId : name.trim()) + "」";
    }
}
