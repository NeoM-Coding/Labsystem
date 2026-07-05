package xyz.jasenon.lab.engine;

import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.common.model.device.DeviceType;
import xyz.jasenon.lab.engine.action.ActionGroup;
import xyz.jasenon.lab.engine.eval.DeviceConditionGroup;
import xyz.jasenon.lab.engine.eval.EvalNode;
import xyz.jasenon.lab.engine.eval.LogicType;
import xyz.jasenon.lab.engine.eval.Operator;
import xyz.jasenon.lab.engine.event.DeviceEvent;
import xyz.jasenon.lab.engine.event.TimeEvent;
import xyz.jasenon.lab.engine.event.TimeSignal;
import xyz.jasenon.lab.engine.runtime.Runtime;
import xyz.jasenon.lab.engine.runtime.RuntimeLifetime;
import xyz.jasenon.lab.engine.runtime.RuntimeSignal;
import xyz.jasenon.lab.engine.runtime.RuntimeScheduler;
import xyz.jasenon.lab.engine.runtime.RuntimeState;
import xyz.jasenon.lab.engine.time.CalendarConstraint;
import xyz.jasenon.lab.engine.time.TimeConditionGroup;
import xyz.jasenon.lab.engine.time.TimePointCondition;
import xyz.jasenon.lab.engine.time.TimeWindowCondition;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineTests {

    @Test
    void refreshesMatchedRuntimeAndDelegatesScheduling() {
        RecordingRuntimeScheduler scheduler = new RecordingRuntimeScheduler();
        Engine engine = new Engine(scheduler);
        Runtime runtime = runtime("runtime-1");
        engine.register(runtime);

        engine.accept(event("27"));

        assertEquals(List.of("runtime-1"), scheduler.scheduledRuntimeIds);
        assertTrue(runtime.getActionGroups().get(0).getRoot().isOk());
    }

    @Test
    void fansOutSharedDeviceConditionGroupToEveryReferencingActionGroup() {
        RecordingRuntimeScheduler scheduler = new RecordingRuntimeScheduler();
        Engine engine = new Engine(scheduler);
        DeviceConditionGroup sharedDeviceGroup = new DeviceConditionGroup(
                "shared-high-temperature",
                chain("ac-1", "roomTemperature", Operator.GT, "26", false)
        );
        TimeConditionGroup always = TimeConditionGroup.always("shared-always");
        Runtime runtime = new Runtime("runtime-shared-device", List.of(
                new ActionGroup("notify-user", sharedDeviceGroup, always),
                new ActionGroup("close-circuit", sharedDeviceGroup, always)
        ));

        try {
            engine.register(runtime);
            engine.accept(event("27"));

            RuntimeSignal.StateChanged signal =
                    (RuntimeSignal.StateChanged) scheduler.signals.get(0);
            assertEquals(
                    Set.of("notify-user", "close-circuit"),
                    signal.candidateActionGroupIds()
            );
            assertTrue(sharedDeviceGroup.getRoot().isOk());
        } finally {
            engine.remove(runtime.getRuntimeId());
        }
    }

    @Test
    void ignoresUnregisteredEvents() {
        RecordingRuntimeScheduler scheduler = new RecordingRuntimeScheduler();
        Engine engine = new Engine(scheduler);

        engine.accept(event("27"));

        assertTrue(scheduler.scheduledRuntimeIds.isEmpty());
    }

    @Test
    void removeClearsEventIndexAndCancelsRuntimeScheduling() {
        RecordingRuntimeScheduler scheduler = new RecordingRuntimeScheduler();
        Engine engine = new Engine(scheduler);
        engine.register(runtime("runtime-1"));

        engine.remove("runtime-1");
        engine.accept(event("27"));

        assertEquals(List.of("runtime-1"), scheduler.cancelledRuntimeIds);
        assertTrue(scheduler.scheduledRuntimeIds.isEmpty());
    }

    @Test
    void activatesPendingRuntimeAndProactivelyRemovesItAtExpiry() throws InterruptedException {
        RecordingRuntimeScheduler scheduler = new RecordingRuntimeScheduler();
        Engine engine = new Engine(scheduler);
        Instant now = Instant.now();
        Runtime runtime = new Runtime(
                "runtime-lifecycle",
                new RuntimeLifetime(
                        now.plus(Duration.ofMillis(100)),
                        now.plus(Duration.ofMillis(500))
                ),
                List.of(new ActionGroup(
                        "high-temperature",
                        chain("ac-1", "roomTemperature", Operator.GT, "26", false)
                ))
        );
        engine.register(runtime);

        engine.accept(event("27"));
        assertTrue(scheduler.scheduledRuntimeIds.isEmpty());

        assertTrue(await(() -> runtime.getState() == RuntimeState.ACTIVE));
        engine.accept(event("27"));
        assertEquals(List.of("runtime-lifecycle"), scheduler.scheduledRuntimeIds);

        assertTrue(await(() -> runtime.getState() == RuntimeState.EXPIRED));
        assertTrue(engine.runtimeTable().get(runtime.getRuntimeId()).isEmpty());
        assertEquals(List.of("runtime-lifecycle"), scheduler.cancelledRuntimeIds);
    }

    @Test
    void activeWindowSchedulesInitialInference() {
        RecordingRuntimeScheduler scheduler = new RecordingRuntimeScheduler();
        Engine engine = new Engine(scheduler);
        LocalTime now = LocalTime.now(ZoneOffset.UTC);
        TimeConditionGroup timeGroup = new TimeConditionGroup(List.of(
                new TimeWindowCondition(
                        "current-window",
                        CalendarConstraint.everyDay(ZoneOffset.UTC),
                        now.minusMinutes(1),
                        now.plusMinutes(1)
                )
        ));
        Runtime runtime = new Runtime("runtime-window");
        runtime.registerActionGroup(new ActionGroup(
                "window-group",
                chain("ac-1", "roomTemperature", Operator.GT, "26", true),
                timeGroup
        ));

        try {
            engine.register(runtime);

            assertEquals(List.of("runtime-window"), scheduler.scheduledRuntimeIds);
            assertTrue(scheduler.signals.get(0) instanceof RuntimeSignal.StateChanged);
        } finally {
            engine.remove(runtime.getRuntimeId());
        }
    }

    @Test
    void routesTimePointAsLosslessRuntimeSignal() {
        RecordingRuntimeScheduler scheduler = new RecordingRuntimeScheduler();
        Engine engine = new Engine(scheduler);
        TimeConditionGroup timeGroup = new TimeConditionGroup(List.of(
                new TimePointCondition(
                        "point-1",
                        CalendarConstraint.everyDay(ZoneOffset.UTC),
                        LocalTime.NOON
                )
        ));
        Runtime runtime = new Runtime("runtime-point");
        runtime.registerActionGroup(new ActionGroup(
                "point-group",
                chain("ac-1", "roomTemperature", Operator.GT, "26", true),
                timeGroup
        ));
        Instant scheduledAt = Instant.parse("2026-07-05T12:00:00Z");
        TimeEvent event = new TimeEvent(
                runtime.getRuntimeId(),
                timeGroup.getGroupId(),
                "point-1",
                TimeSignal.TIME_POINT,
                scheduledAt,
                scheduledAt
        );

        try {
            engine.register(runtime);
            engine.accept(event);

            assertEquals(List.of("runtime-point"), scheduler.scheduledRuntimeIds);
            RuntimeSignal.TimePointOccurred signal =
                    (RuntimeSignal.TimePointOccurred) scheduler.signals.get(0);
            assertEquals(event.occurrenceId(), signal.event().occurrenceId());
        } finally {
            engine.remove(runtime.getRuntimeId());
        }
    }

    private static Runtime runtime(String runtimeId) {
        Runtime runtime = new Runtime(runtimeId);
        runtime.registerActionGroup(new ActionGroup(
                "high-temperature",
                chain("ac-1", "roomTemperature", Operator.GT, "26", false)
        ));
        return runtime;
    }

    private static DeviceEvent event(String value) {
        return new DeviceEvent(
                DeviceType.AirCondition,
                "ac-1",
                "roomTemperature",
                value,
                Instant.now()
        );
    }

    private static EvalNode chain(
            String deviceId,
            String field,
            Operator operator,
            String value,
            boolean initialResult
    ) {
        EvalNode dummy = new EvalNode();
        dummy.setResult(true);

        EvalNode node = new EvalNode();
        node.setNodeId("node-1");
        node.setDeviceId(deviceId);
        node.setDeviceType(DeviceType.AirCondition);
        node.setField(field);
        node.setOperator(operator);
        node.setValue(value);
        node.setLogicToPrev(LogicType.AND);
        node.setResult(initialResult);
        dummy.setNext(node);
        return dummy;
    }

    private static boolean await(BooleanSupplier condition) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }

    private static class RecordingRuntimeScheduler implements RuntimeScheduler {

        private final List<String> scheduledRuntimeIds = new ArrayList<>();
        private final List<String> cancelledRuntimeIds = new ArrayList<>();
        private final List<RuntimeSignal> signals = new ArrayList<>();

        @Override
        public void schedule(Runtime runtime, RuntimeSignal signal) {
            scheduledRuntimeIds.add(runtime.getRuntimeId());
            signals.add(signal);
        }

        @Override
        public void cancel(String runtimeId) {
            cancelledRuntimeIds.add(runtimeId);
        }
    }
}
