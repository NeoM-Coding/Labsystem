package xyz.jasenon.lab.edu.api.command;

import xyz.jasenon.lab.audit.api.Loggable;
import xyz.jasenon.lab.edu.api.model.WeekType;

import java.io.Serial;
import java.time.LocalTime;

public record TimetableCreate(
        String semesterId,
        String laboratoryId,
        String courseName,
        String teacherName,
        WeekType weekType,
        Integer startWeek,
        Integer endWeek,
        Integer startSection,
        Integer endSection,
        LocalTime startTime,
        LocalTime endTime,
        Integer weekday
) implements Loggable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String log() {
        return "为实验室「" + laboratoryId + "」排课「" + display(courseName) + "」";
    }

    private static String display(String value) {
        return value == null || value.isBlank() ? "未命名" : value.trim();
    }
}
