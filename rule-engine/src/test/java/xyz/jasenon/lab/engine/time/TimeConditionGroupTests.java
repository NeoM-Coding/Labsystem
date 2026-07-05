package xyz.jasenon.lab.engine.time;

import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.engine.event.TimeEvent;
import xyz.jasenon.lab.engine.event.TimeSignal;
import xyz.jasenon.lab.engine.runtime.RuntimeSignal;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeConditionGroupTests {

    @Test
    void appliesCalendarConstraintsInsideOneWindowAndOrAcrossWindows() {
        CalendarConstraint mondayInJuly = new CalendarConstraint(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                Set.of(DayOfWeek.MONDAY),
                ZoneOffset.UTC
        );
        TimeConditionGroup group = new TimeConditionGroup("morning-group", List.of(
                new TimeWindowCondition(
                        "monday-office",
                        mondayInJuly,
                        LocalTime.of(9, 0),
                        LocalTime.of(18, 0)
                ),
                new TimeWindowCondition(
                        "night",
                        CalendarConstraint.everyDay(ZoneOffset.UTC),
                        LocalTime.of(22, 0),
                        LocalTime.of(6, 0)
                )
        ));

        assertTrue(group.initialize(Instant.parse("2026-07-06T10:00:00Z")));
        assertTrue(group.allows(RuntimeSignal.stateChanged()));

        assertTrue(group.initialize(Instant.parse("2026-07-07T02:00:00Z")));
        assertTrue(group.allows(RuntimeSignal.stateChanged()));

        assertFalse(group.initialize(Instant.parse("2026-07-07T10:00:00Z")));
        assertFalse(group.allows(RuntimeSignal.stateChanged()));
    }

    @Test
    void timePointIsValidOnlyForItsOccurrenceSignal() {
        TimeConditionGroup group = new TimeConditionGroup(List.of(
                new TimePointCondition(
                        "morning",
                        CalendarConstraint.everyDay(ZoneOffset.UTC),
                        LocalTime.of(8, 0)
                )
        ));
        Instant scheduledAt = Instant.parse("2026-07-05T08:00:00Z");
        TimeEvent event = new TimeEvent(
                "runtime-1",
                group.getGroupId(),
                "morning",
                TimeSignal.TIME_POINT,
                scheduledAt,
                scheduledAt
        );

        group.initialize(scheduledAt);

        assertFalse(group.allows(RuntimeSignal.stateChanged()));
        assertTrue(group.apply(event));
        assertTrue(group.allows(RuntimeSignal.timePoint(event)));
    }

    @Test
    void calculatesNextWindowBoundaryAndPointOccurrence() {
        TimeWindowCondition window = new TimeWindowCondition(
                "office",
                CalendarConstraint.everyDay(ZoneOffset.UTC),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0)
        );
        TimePointCondition point = new TimePointCondition(
                "alarm",
                CalendarConstraint.everyDay(ZoneOffset.UTC),
                LocalTime.of(12, 0)
        );

        TimeTransition exit = window.nextTransitionAfter(
                Instant.parse("2026-07-05T10:00:00Z")
        ).orElseThrow();
        TimeTransition nextPoint = point.nextTransitionAfter(
                Instant.parse("2026-07-05T12:00:00Z")
        ).orElseThrow();

        assertEquals(TimeSignal.WINDOW_EXIT, exit.signal());
        assertEquals(Instant.parse("2026-07-05T18:00:00Z"), exit.scheduledAt());
        assertEquals(Instant.parse("2026-07-06T12:00:00Z"), nextPoint.scheduledAt());
    }
}
