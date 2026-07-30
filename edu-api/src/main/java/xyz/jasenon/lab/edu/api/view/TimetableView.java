package xyz.jasenon.lab.edu.api.view;

import xyz.jasenon.lab.edu.api.model.WeekType;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalTime;

public record TimetableView(
        String id,
        String semesterId,
        SemesterView semester,
        String laboratoryId,
        String laboratoryName,
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
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
