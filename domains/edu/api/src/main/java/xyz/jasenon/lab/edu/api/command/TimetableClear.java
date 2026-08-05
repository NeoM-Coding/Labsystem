package xyz.jasenon.lab.edu.api.command;

import xyz.jasenon.lab.audit.api.Loggable;

import java.io.Serial;

public record TimetableClear(String semesterId, String laboratoryId) implements Loggable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String log() {
        return "清空实验室「" + laboratoryId + "」在学期「" + semesterId + "」的课表";
    }
}
