package xyz.jasenon.lab.engine.time;

import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.engine.action.ActionGroup;
import xyz.jasenon.lab.engine.eval.EvalNode;
import xyz.jasenon.lab.engine.event.TimeEvent;
import xyz.jasenon.lab.engine.event.TimeSignal;
import xyz.jasenon.lab.engine.runtime.Runtime;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeScheduleServiceTests {

    @Test
    void emitsScheduledTimePointWithoutPolling() throws InterruptedException {
        TimeScheduleService service = new TimeScheduleService();
        Instant target = Instant.now().plusMillis(500);
        ZonedDateTime targetTime = target.atZone(ZoneOffset.UTC);
        TimeConditionGroup timeGroup = new TimeConditionGroup("shared-time-group", List.of(
                new TimePointCondition(
                        "point-1",
                        new CalendarConstraint(
                                targetTime.toLocalDate(),
                                targetTime.toLocalDate(),
                                Set.of(),
                                ZoneOffset.UTC
                        ),
                        targetTime.toLocalTime()
                )
        ));
        EvalNode dummy = new EvalNode();
        dummy.setResult(true);
        Runtime runtime = new Runtime("runtime-time-service");
        runtime.registerActionGroup(new ActionGroup("group-1", dummy, timeGroup));
        runtime.registerActionGroup(new ActionGroup("group-2", dummy, timeGroup));
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<TimeEvent> received = new AtomicReference<>();
        AtomicInteger deliveryCount = new AtomicInteger();

        try {
            service.track(runtime, event -> {
                received.set(event);
                deliveryCount.incrementAndGet();
                delivered.countDown();
            });

            assertTrue(delivered.await(2, TimeUnit.SECONDS));
            Thread.sleep(100);
            assertEquals(TimeSignal.TIME_POINT, received.get().signal());
            assertEquals("runtime-time-service", received.get().key().runtimeId());
            assertEquals("shared-time-group", received.get().key().timeConditionGroupId());
            assertEquals("point-1", received.get().key().conditionId());
            assertEquals(1, deliveryCount.get());
        } finally {
            service.cancel(runtime.getRuntimeId());
            service.shutdown();
        }
    }
}
