package xyz.jasenon.lab.engine.time;

import xyz.jasenon.lab.engine.event.TimeSignal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * 按日期、星期和时区约束重复发生的瞬时时间点。
 */
public final class TimePointCondition implements TimeCondition {

    private final String conditionId;
    private final CalendarConstraint calendar;
    private final LocalTime time;

    public TimePointCondition(
            String conditionId,
            CalendarConstraint calendar,
            LocalTime time
    ) {
        this.conditionId = requireText(conditionId, "conditionId");
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.time = Objects.requireNonNull(time, "time");
    }

    @Override
    public String conditionId() {
        return conditionId;
    }

    @Override
    public boolean isWindow() {
        return false;
    }

    @Override
    public boolean isWindowActive(Instant instant) {
        return false;
    }

    @Override
    public Optional<TimeTransition> nextTransitionAfter(Instant instant) {
        LocalDate cursor = instant.atZone(calendar.zoneId()).toLocalDate();
        for (int checked = 0; checked < 3; checked++) {
            LocalDate date = calendar.nextMatchingDate(cursor);
            if (date == null) {
                return Optional.empty();
            }
            Instant occurrence = ZonedDateTime.of(date, time, calendar.zoneId()).toInstant();
            if (occurrence.isAfter(instant)) {
                return Optional.of(new TimeTransition(TimeSignal.TIME_POINT, occurrence));
            }
            cursor = date.plusDays(1);
        }
        return Optional.empty();
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
