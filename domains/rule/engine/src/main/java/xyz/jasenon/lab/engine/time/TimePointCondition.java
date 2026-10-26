package xyz.jasenon.lab.engine.time;

import xyz.jasenon.lab.engine.event.TimeSignal;
import java.time.*;
import java.util.*;

/** Immutable schedule; its runtime leaf is true only during a pulse command. */
public final class TimePointCondition implements TimeCondition {
    private final String conditionId;
    private final CalendarConstraint calendar;
    private final LocalTime time;
    public TimePointCondition(String conditionId, CalendarConstraint calendar, LocalTime time) {
        if (conditionId == null || conditionId.isBlank()) throw new IllegalArgumentException("conditionId required");
        this.conditionId = conditionId;
        this.calendar = Objects.requireNonNull(calendar);
        this.time = Objects.requireNonNull(time);
    }
    public String conditionId() { return conditionId; }
    public boolean isWindow() { return false; }
    public boolean isWindowActive(Instant at) { return false; }
    public Optional<TimeTransition> nextTransitionAfter(Instant at) {
        LocalDate date = at.atZone(calendar.zoneId()).toLocalDate();
        for (int i = 0; i < 3; i++) {
            date = calendar.nextMatchingDate(date);
            if (date == null) return Optional.empty();
            Instant next = ZonedDateTime.of(date, time, calendar.zoneId()).toInstant();
            if (next.isAfter(at)) return Optional.of(new TimeTransition(TimeSignal.TIME_POINT, next));
            date = date.plusDays(1);
        }
        return Optional.empty();
    }
}
