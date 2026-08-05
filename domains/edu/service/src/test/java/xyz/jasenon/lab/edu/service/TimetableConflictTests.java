package xyz.jasenon.lab.edu.service;

import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.edu.api.model.WeekType;
import xyz.jasenon.lab.edu.model.Timetable;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimetableConflictTests {

    @Test
    void sameLaboratoryOverlappingBothWeeksConflicts() {
        Timetable first = timetable("lab-1", "张三", WeekType.Both, 1, 16, 1, "08:00", "09:40");
        Timetable second = timetable("lab-1", "李四", WeekType.Single, 3, 9, 1, "09:00", "10:00");
        assertTrue(TimetableServiceImpl.hasConflict(first, second));
    }

    @Test
    void sameTeacherAcrossLaboratoriesConflicts() {
        Timetable first = timetable("lab-1", " 张三 ", WeekType.Both, 1, 16, 2, "08:00", "09:40");
        Timetable second = timetable("lab-2", "张三", WeekType.Both, 1, 16, 2, "09:00", "10:00");
        assertTrue(TimetableServiceImpl.hasConflict(first, second));
    }

    @Test
    void singleAndDoubleWeeksDoNotConflict() {
        Timetable first = timetable("lab-1", "张三", WeekType.Single, 1, 16, 1, "08:00", "09:40");
        Timetable second = timetable("lab-1", "李四", WeekType.Double, 1, 16, 1, "08:00", "09:40");
        assertFalse(TimetableServiceImpl.hasConflict(first, second));
    }

    @Test
    void matchingParityMustExistInsideWeekIntersection() {
        Timetable first = timetable("lab-1", "张三", WeekType.Single, 2, 2, 1, "08:00", "09:40");
        Timetable second = timetable("lab-1", "李四", WeekType.Single, 2, 2, 1, "08:00", "09:40");
        assertFalse(TimetableServiceImpl.hasConflict(first, second));
    }

    @Test
    void adjacentHalfOpenTimeRangesDoNotConflict() {
        Timetable first = timetable("lab-1", "张三", WeekType.Both, 1, 16, 1, "08:00", "09:40");
        Timetable second = timetable("lab-1", "李四", WeekType.Both, 1, 16, 1, "09:40", "10:30");
        assertFalse(TimetableServiceImpl.hasConflict(first, second));
    }

    @Test
    void differentWeekdaysOrSubjectsDoNotConflict() {
        Timetable first = timetable("lab-1", "张三", WeekType.Both, 1, 16, 1, "08:00", "09:40");
        Timetable differentDay = timetable("lab-1", "李四", WeekType.Both, 1, 16, 2, "08:00", "09:40");
        Timetable differentSubject = timetable("lab-2", "李四", WeekType.Both, 1, 16, 1, "08:00", "09:40");
        assertFalse(TimetableServiceImpl.hasConflict(first, differentDay));
        assertFalse(TimetableServiceImpl.hasConflict(first, differentSubject));
    }

    private static Timetable timetable(
            String laboratoryId, String teacher, WeekType weekType,
            int startWeek, int endWeek, int weekday, String start, String end
    ) {
        Timetable timetable = new Timetable();
        timetable.setLaboratoryId(laboratoryId);
        timetable.setTeacherName(teacher);
        timetable.setWeekType(weekType);
        timetable.setStartWeek(startWeek);
        timetable.setEndWeek(endWeek);
        timetable.setWeekday(weekday);
        timetable.setStartTime(LocalTime.parse(start));
        timetable.setEndTime(LocalTime.parse(end));
        return timetable;
    }
}
