package xyz.jasenon.lab.edu.api.command;

import xyz.jasenon.lab.audit.api.Loggable;

import java.io.Serial;

public record TimetableDelete(String timetableId) implements Loggable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String log() {
        return "删除课表「" + timetableId + "」";
    }
}
