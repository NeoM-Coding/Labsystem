package xyz.jasenon.lab.bi.api.view;

import java.io.Serializable;
import java.time.LocalDateTime;

public record EduActiveCourse(
        String timetableId,
        String semesterId,
        String laboratoryId,
        String laboratoryName,
        String courseName,
        String teacherName,
        LocalDateTime startAt,
        LocalDateTime endAt
) implements Serializable {
}
