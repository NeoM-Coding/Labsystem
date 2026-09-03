package xyz.jasenon.lab.bi.api.view;

import xyz.jasenon.lab.bi.api.model.EduTimeRange;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record EduDashboardView(
        EduTimeRange range,
        LocalDate periodStart,
        LocalDate periodEnd,
        SemesterBrief currentSemester,
        EduProgressSummary summary,
        List<EduChartPoint> timeline,
        List<EduChartPoint> weekdays,
        List<EduChartPoint> sections,
        List<EduActiveCourse> activeCourses,
        LocalDateTime calculatedAt
) implements Serializable {
}
