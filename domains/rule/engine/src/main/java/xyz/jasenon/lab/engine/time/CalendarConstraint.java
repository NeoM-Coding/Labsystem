package xyz.jasenon.lab.engine.time;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * 时间窗口和 TimePoint 共用的日期范围、星期和时区约束。
 *
 * <p>同一个 CalendarConstraint 内的约束按 AND 关系判断。</p>
 */
public final class CalendarConstraint {

    private final LocalDate startDate;
    private final LocalDate endDate;
    private final Set<DayOfWeek> weekdays;
    private final ZoneId zoneId;

    public CalendarConstraint(
            LocalDate startDate,
            LocalDate endDate,
            Set<DayOfWeek> weekdays,
            ZoneId zoneId
    ) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate must not be after endDate");
        }
        this.startDate = startDate;
        this.endDate = endDate;
        this.weekdays = weekdays == null || weekdays.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(weekdays));
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId");
    }

    public static CalendarConstraint everyDay(ZoneId zoneId) {
        return new CalendarConstraint(null, null, Set.of(), zoneId);
    }

    public ZoneId zoneId() {
        return zoneId;
    }

    public boolean matches(LocalDate date) {
        if (startDate != null && date.isBefore(startDate)) {
            return false;
        }
        if (endDate != null && date.isAfter(endDate)) {
            return false;
        }
        return weekdays.isEmpty() || weekdays.contains(date.getDayOfWeek());
    }

    public LocalDate nextMatchingDate(LocalDate date) {
        LocalDate candidate = startDate != null && date.isBefore(startDate) ? startDate : date;
        // ring 7 一周时间得到下一个调度 候选date
        for (int i = 0; i < 8; i++) {
            if (endDate != null && candidate.isAfter(endDate)) {
                return null;
            }
            if (matches(candidate)) {
                return candidate;
            }
            candidate = candidate.plusDays(1);
        }
        return null;
    }
}
