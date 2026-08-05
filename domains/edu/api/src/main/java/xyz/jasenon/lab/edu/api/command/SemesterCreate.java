package xyz.jasenon.lab.edu.api.command;

import xyz.jasenon.lab.audit.api.Loggable;

import java.io.Serial;
import java.time.LocalDate;

public record SemesterCreate(String name, LocalDate startDate, LocalDate endDate) implements Loggable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String log() {
        return "创建学期「" + display(name) + "」";
    }

    private static String display(String value) {
        return value == null || value.isBlank() ? "未命名" : value.trim();
    }
}
