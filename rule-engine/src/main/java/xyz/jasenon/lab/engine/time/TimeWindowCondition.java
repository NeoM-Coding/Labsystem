package xyz.jasenon.lab.engine.time;

import xyz.jasenon.lab.engine.event.TimeSignal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * 每日重复的时间窗口。
 *
 * <p>跨午夜窗口的星期约束归属于窗口开始日，例如星期一 22:00-06:00
 * 会持续到星期二 06:00。</p>
 */
public final class TimeWindowCondition implements TimeCondition {

    private final String conditionId;
    private final CalendarConstraint calendar;
    private final LocalTime startTime;
    private final LocalTime endTime;

    public TimeWindowCondition(
            String conditionId,
            CalendarConstraint calendar,
            LocalTime startTime,
            LocalTime endTime
    ) {
        this.conditionId = requireText(conditionId, "conditionId");
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.startTime = Objects.requireNonNull(startTime, "startTime");
        this.endTime = Objects.requireNonNull(endTime, "endTime");
        if (startTime.equals(endTime)) {
            throw new IllegalArgumentException("startTime and endTime must be different");
        }
    }

    @Override
    public String conditionId() {
        return conditionId;
    }

    @Override
    public boolean isWindow() {
        return true;
    }

    @Override
    public boolean isWindowActive(Instant instant) {
        LocalDate localDate = instant.atZone(calendar.zoneId()).toLocalDate();
        return containsWindowStartedOn(localDate, instant)
                || containsWindowStartedOn(localDate.minusDays(1), instant);
    }

    @Override
    public Optional<TimeTransition> nextTransitionAfter(Instant instant) {
        LocalDate cursor = instant.atZone(calendar.zoneId()).toLocalDate().minusDays(1);
        TimeTransition nearest = null;
        for (int checked = 0; checked < 4; checked++) {
            LocalDate date = calendar.nextMatchingDate(cursor);
            if (date == null) {
                break;
            }
            Instant enter = start(date).toInstant();
            Instant exit = end(date).toInstant();
            nearest = earlierAfter(nearest, TimeSignal.WINDOW_ENTER, enter, instant);
            nearest = earlierAfter(nearest, TimeSignal.WINDOW_EXIT, exit, instant);
            if (nearest != null) {
                break;
            }
            cursor = date.plusDays(1);
        }
        return Optional.ofNullable(nearest);
    }

    private boolean containsWindowStartedOn(LocalDate date, Instant instant) {
        if (!calendar.matches(date)) {
            return false;
        }
        Instant start = start(date).toInstant();
        Instant end = end(date).toInstant();
        return !instant.isBefore(start) && instant.isBefore(end);
    }

    private ZonedDateTime start(LocalDate date) {
        return ZonedDateTime.of(date, startTime, calendar.zoneId());
    }

    private ZonedDateTime end(LocalDate date) {
        LocalDate endDate = endTime.isAfter(startTime) ? date : date.plusDays(1);
        return ZonedDateTime.of(endDate, endTime, calendar.zoneId());
    }

    private static TimeTransition earlierAfter(
            TimeTransition current,
            TimeSignal signal,
            Instant candidate,
            Instant instant
    ) {
        if (!candidate.isAfter(instant)) {
            return current;
        }
        if (current == null || candidate.isBefore(current.scheduledAt())) {
            return new TimeTransition(signal, candidate);
        }
        return current;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
