package xyz.jasenon.lab.engine.v3;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.device.model.DeviceType;
import xyz.jasenon.lab.engine.Engine;
import xyz.jasenon.lab.engine.action.Action;
import xyz.jasenon.lab.engine.action.ActionExecutionResult;
import xyz.jasenon.lab.engine.action.ControlAction;
import xyz.jasenon.lab.engine.compiler.RuntimeRevisionCompiler;
import xyz.jasenon.lab.engine.definition.RuntimeRevision;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.*;
import xyz.jasenon.lab.engine.eval.ConditionTree;
import xyz.jasenon.lab.engine.eval.LogicType;
import xyz.jasenon.lab.engine.eval.Operator;
import xyz.jasenon.lab.engine.event.DeviceEvent;
import xyz.jasenon.lab.engine.event.DeviceEventKey;
import xyz.jasenon.lab.engine.event.TimeWindowStatusEvent;
import xyz.jasenon.lab.engine.execution.ActionDispatcher;
import xyz.jasenon.lab.engine.plan.RuntimePlan;
import xyz.jasenon.lab.engine.plan.RuntimePlan.ActionSpec;
import xyz.jasenon.lab.engine.plan.RuntimePlan.Binding;
import xyz.jasenon.lab.engine.plan.RuntimePlan.Lifetime;
import xyz.jasenon.lab.engine.plan.RuntimePlan.Predicate;
import xyz.jasenon.lab.engine.runtime.RuntimeInstance;
import xyz.jasenon.lab.engine.scheduling.EngineOptions;
import xyz.jasenon.lab.engine.scheduling.TimeScheduler;
import xyz.jasenon.lab.engine.time.CalendarConstraint;
import xyz.jasenon.lab.engine.time.TimeWindowCondition;

import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class V3EngineTests {
    static final DeviceEventKey KEY = new DeviceEventKey(DeviceType.AirCondition, "ac-1", "opened");
    static final RuntimePlan.ActionGroup REPORT = new RuntimePlan.ActionGroup("a", List.of(new ActionSpec(Action.ActionType.Report, null, List.of(), null, null, List.of(), Set.of(), "test")));
    static final Predicate P = new Predicate("p", KEY, Operator.EQ, "1", LogicType.AND);
    final AtomicInteger calls = new AtomicInteger();
    final List<CompletableFuture<ActionExecutionResult>> gates = new CopyOnWriteArrayList<>();
    boolean block;
    Engine engine;
    TimeScheduler timers;
    ActionDispatcher actions;

    @BeforeEach
    void setup() {
        EngineOptions options = new EngineOptions();
        options.setWorkers(2);
        options.setPendingCapacity(4);
        options.setWorkerCapacity(8);
        options.setIngressCapacity(16);
        actions = new ActionDispatcher((rt, group, action) -> {
            calls.incrementAndGet();
            if (block) {
                CompletableFuture<ActionExecutionResult> gate = new CompletableFuture<>();
                gates.add(gate);
                return gate;
            }
            return CompletableFuture.completedFuture(ActionExecutionResult.success(rt.runtimeId(), group.actionGroupId(), action.is(), "ok"));
        }
                , notice -> {
        }
                , options);
        timers = new TimeScheduler();
        engine = new Engine(actions, timers, options);
    }

    @AfterEach
    void close() {
        engine.close();
        timers.close();
        actions.close();
    }

    RuntimePlan plan(String id, TriggerMode mode, List<TimeWindowCondition> windows, Lifetime life) {
        return new RuntimePlan(id, life, Map.of("d", List.of(P)), Map.of("t", windows), Map.of("a", REPORT), List.of(new Binding("b", "d", "t", "a", mode)));
    }

    RuntimePlan plan(TriggerMode mode) {
        return plan("r", mode, List.of(), new Lifetime(null, null));
    }

    void observe(String value) {
        engine.accept(new DeviceEvent(KEY.deviceType(), KEY.deviceId(), KEY.field(), value, Instant.now()));
    }

    void eventually(int count) {
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertEquals(count, calls.get()));
    }

    void complete(int i) {
        gates.get(i).complete(ActionExecutionResult.success("r", "a", Action.ActionType.Report, "ok"));
    }

    TimeWindowCondition window() {
        return new TimeWindowCondition("w", CalendarConstraint.everyDay(ZoneOffset.UTC), LocalTime.of(1, 0), LocalTime.of(2, 0));
    }

    void window(boolean active, long generation) {
        engine.accept(new TimeWindowStatusEvent("r", generation, generation, Map.of(RuntimeInstance.windowKey("t", "w"), active), Instant.now()));
    }

    @Test
    void startsFalseAndEdgeIgnoresUnchangedObservations() {
        engine.register(plan(TriggerMode.EDGE));
        assertFalse(engine.inspect("r").roots().get("b"));
        observe("1");
        eventually(1);
        observe("1");
        assertEquals(1, calls.get());
        observe("0");
        observe("1");
        eventually(2);
    }

    @Test
    void reconcileObservesSameValueAndDoesNotSelfLoop() {
        engine.register(plan(TriggerMode.RECONCILE));
        observe("1");
        eventually(1);
        await().until(() -> !engine.inspect("r").executing());
        observe("1");
        eventually(2);
        await().until(() -> !engine.inspect("r").executing());
        assertEquals(2, calls.get());
    }

    @Test
    void edgesAreRetainedWhileActionsAreRunning() {
        block = true;
        engine.register(plan(TriggerMode.EDGE));
        observe("1");
        eventually(1);
        observe("0");
        observe("1");
        observe("0");
        observe("1");
        assertEquals(2, engine.inspect("r").pendingDecisions());
        complete(0);
        eventually(2);
        complete(1);
        eventually(3);
        complete(2);
    }

    @Test
    void reconcileCoalescesAndRechecksLatestTree() {
        block = true;
        engine.register(plan(TriggerMode.RECONCILE));
        observe("1");
        eventually(1);
        for (int i = 0; i < 5; i++) observe("1");
        complete(0);
        eventually(2);
        observe("1");
        observe("0");
        complete(1);
        await().until(() -> !engine.inspect("r").executing());
        assertEquals(2, calls.get());
    }

    @Test
    void windowTransitionsParticipateInCompleteEdge() {
        var snapshot = engine.register(plan("r", TriggerMode.EDGE, List.of(window()), new Lifetime(null, null)));
        window(false, snapshot.generation());
        observe("1");
        assertEquals(0, calls.get());
        window(true, snapshot.generation());
        eventually(1);
        window(true, snapshot.generation());
        assertEquals(1, calls.get());
        window(false, snapshot.generation());
        window(true, snapshot.generation());
        eventually(2);
    }

    @Test
    void oldWindowEventsCannotMutateReplacement() {
        var old = engine.register(plan("r", TriggerMode.EDGE, List.of(window()), new Lifetime(null, null)));
        var current = engine.register(plan("r", TriggerMode.EDGE, List.of(window()), new Lifetime(null, null)));
        window(false, current.generation());
        observe("1");
        window(true, old.generation());
        assertFalse(engine.inspect("r").roots().get("b"));
        assertEquals(0, calls.get());
    }

    @Test
    void replacementResetsDeviceStateAndPreservesRuntimeSingleFlight() {
        block = true;
        engine.register(plan(TriggerMode.EDGE));
        observe("1");
        eventually(1);
        engine.register(plan(TriggerMode.EDGE));
        assertFalse(engine.inspect("r").roots().get("b"));
        observe("1");
        assertEquals(1, calls.get());
        complete(0);
        eventually(2);
        complete(1);
    }

    @Test
    void removeCancelsQueuedDecisionsAndIsAnAcknowledgedBarrier() {
        block = true;
        engine.register(plan(TriggerMode.EDGE));
        observe("1");
        eventually(1);
        observe("0");
        observe("1");
        engine.remove("r");
        assertTrue(engine.runtime("r").isEmpty());
        complete(0);
        observe("1");
        assertEquals(1, calls.get());
    }

    @Test
    void pendingMaintainsDeviceStateAndActivationChecksOnce() {
        Instant start = Instant.now().plusMillis(250);
        engine.register(plan("r", TriggerMode.EDGE, List.of(), new Lifetime(start, start.plusSeconds(5))));
        observe("1");
        assertEquals(0, calls.get());
        eventually(1);
    }

    @Test
    void expirationPreventsQueuedActions() {
        block = true;
        Instant end = Instant.now().plusMillis(300);
        engine.register(plan("r", TriggerMode.EDGE, List.of(), new Lifetime(null, end)));
        observe("1");
        eventually(1);
        observe("0");
        observe("1");
        await().until(() -> engine.runtime("r").isEmpty());
        complete(0);
        assertEquals(1, calls.get());
    }

    @Test
    void workerProgressesWhileAnotherRuntimeActionIsBlocked() {
        block = true;
        engine.register(plan(TriggerMode.EDGE));
        observe("1");
        eventually(1);
        engine.register(plan("other", TriggerMode.EDGE, List.of(), new Lifetime(null, null)));
        observe("1");
        eventually(2);
        complete(0);
        complete(1);
    }

    @Test
    void failedPlanPreparationLeavesOldRuntimeInstalled() {
        var before = engine.register(plan(TriggerMode.EDGE));
        assertThrows(IllegalArgumentException.class, () -> new RuntimePlan("r", new Lifetime(null, null), Map.of(), Map.of(), Map.of(), List.of(new Binding("bad", "missing", "missing", "missing", TriggerMode.EDGE))));
        assertEquals(before.generation(), engine.inspect("r").generation());
        observe("1");
        eventually(1);
    }

    @Test
    void bindingModesShareConditionsButHaveIndependentPolicies() {
        RuntimePlan p = plan(TriggerMode.EDGE);
        engine.register(new RuntimePlan("r", p.lifetime(), p.conditionGroups(), p.timeGroups(), p.actionGroups(), List.of(
                new Binding("edge", "d", "t", "a", TriggerMode.EDGE), new Binding("reconcile", "d", "t", "a", TriggerMode.RECONCILE))));
        observe("1");
        eventually(2);
        await().until(() -> !engine.inspect("r").executing());
        observe("1");
        eventually(3);
    }

    @Test
    void leftFoldAndMultiLeafUpdateExposeOnlyFinalRoot() {
        Predicate a = new Predicate("a", KEY, Operator.EQ, "1", LogicType.AND);
        Predicate b = new Predicate("b", KEY, Operator.EQ, "0", LogicType.OR);
        Predicate c = new Predicate("c", KEY, Operator.EQ, "1", LogicType.AND);
        ConditionTree tree = new ConditionTree(List.of(a, b, c), List.of());
        tree.observe(KEY, "0");
        assertFalse(tree.value());
        tree.observe(KEY, "1");
        assertTrue(tree.value());
        tree.observe(KEY, "0");
        assertFalse(tree.value());
    }

    @Test
    void overlappingWindowsAreAppliedAsOneBatch() {
        ConditionTree tree = new ConditionTree(List.of(), List.of("first", "second"));
        tree.windows(Map.of("first", true));
        assertTrue(tree.value());
        tree.windows(Map.of("first", false, "second", true));
        assertTrue(tree.value());
    }

    @Test
    void timePointDefinitionsAreRejectedBeforeInstall() {
        var point = new TimeConditionDefinition("point", TimeConditionType.TIME_POINT, null, null, Set.of(), "UTC", null, null, LocalTime.NOON);
        var revision = new RuntimeRevision("r", null, null, List.of(), List.of(new TimeConditionGroupDefinition("t", List.of(point))), List.of());
        var e = assertThrows(IllegalArgumentException.class, () -> new RuntimeRevisionCompiler().compile(revision));
        assertTrue(e.getMessage().contains("TIME_POINT"));
    }

    @Test
    void calendarCrossMidnightAndBoundariesArePreserved() {
        var window = new TimeWindowCondition("night", new CalendarConstraint(null, null, Set.of(DayOfWeek.MONDAY), ZoneOffset.UTC), LocalTime.of(22, 0), LocalTime.of(6, 0));
        assertTrue(window.isWindowActive(Instant.parse("2026-10-20T05:59:59Z")));
        assertFalse(window.isWindowActive(Instant.parse("2026-10-20T06:00:00Z")));
        assertThrows(IllegalArgumentException.class, () -> new TimeWindowCondition("bad", CalendarConstraint.everyDay(ZoneOffset.UTC), LocalTime.NOON, LocalTime.NOON));
    }

    @Test
    void boundedIngressRejectsExplicitlyAndReleasesWaitersOnShutdown() throws Exception {
        var loop = new xyz.jasenon.lab.engine.routing.EventLoop(1);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        try {
            var first = loop.submit(() -> {
                        entered.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return 1;
                    }
            );
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            var second = loop.submit(() -> 2);
            assertThrows(RejectedExecutionException.class, () -> loop.submit(() -> 3));
            release.countDown();
            assertEquals(1, first.get(1, TimeUnit.SECONDS));
            assertEquals(2, second.get(1, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            loop.close();
        }
        assertThrows(RejectedExecutionException.class, () -> loop.submit(() -> 4));
    }

    @Test
    void compilerCopiesMutableControlArguments() {
        int[] args = {
                7
        };
        var task = new xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto(xyz.jasenon.lab.mqtt.protocol.command.CommandLine.OPEN_AIR_CONDITION_RS485, args, DeviceType.AirCondition, "ac-1");
        var action = new ActionDefinition(ActionType.Control, task, List.of(), Set.of(), null);
        var revision = new RuntimeRevision("r", null, null, List.of(new DeviceConditionGroupDefinition("d", List.of())), List.of(new TimeConditionGroupDefinition("t", List.of())), List.of(new ActionGroupDefinition("a", "d", "t", List.of(action))));
        var plan = new RuntimeRevisionCompiler().compile(revision);
        args[0] = 99;
        task.setDeviceId("changed");
        var first = (ControlAction) plan.actionGroups().get("a").actions().get(0).materialize("a");
        assertEquals(7, first.getControl().getArgs()[0]);
        assertEquals("ac-1", first.getControl().getDeviceId());
        first.getControl().getArgs()[0] = 88;
        var second = (ControlAction) plan.actionGroups().get("a").actions().get(0).materialize("a");
        assertEquals(7, second.getControl().getArgs()[0]);
    }

    @Test
    void staleWindowStatusCannotOverwriteNewerStatus() {
        var snapshot = engine.register(plan("r", TriggerMode.EDGE, List.of(window()), new Lifetime(null, null)));
        observe("1");
        Instant newest = Instant.now().plusSeconds(1);
        String key = RuntimeInstance.windowKey("t", "w");
        engine.accept(new TimeWindowStatusEvent("r", snapshot.generation(), snapshot.generation(), Map.of(key, true), newest));
        engine.accept(new TimeWindowStatusEvent("r", snapshot.generation(), snapshot.generation(), Map.of(key, false), newest.minusMillis(1)));
        assertTrue(engine.inspect("r").roots().get("b"));
    }

    @Test
    void backpressureRetainsEdgesUntilExecutionCanDrain() throws Exception {
        block = true;
        engine.register(plan(TriggerMode.EDGE));
        observe("1");
        eventually(1);
        for (int i = 0; i < 4; i++) {
            observe("0");
            observe("1");
        }
        var blocked = engine.acceptAsync(new DeviceEvent(KEY.deviceType(), KEY.deviceId(), KEY.field(), "0", Instant.now()));
        assertFalse(blocked.isDone());
        complete(0);
        eventually(2);
        blocked.get(2, TimeUnit.SECONDS);
        for (int i = 1; i <= 4; i++) {
            complete(i);
            if (i < 4) eventually(i + 2);
        }
    }

    @Test
    void reconcileWaitingForOldGenerationDoesNotExecuteStaleMatch() {
        block = true;
        engine.register(plan(TriggerMode.EDGE));
        observe("1");
        eventually(1);
        engine.register(plan(TriggerMode.RECONCILE));
        observe("1");
        observe("0");
        complete(0);
        await().until(() -> !engine.inspect("r").executing());
        assertEquals(1, calls.get());
    }

    @Test
    void registerInitializesCurrentlyActiveWindowWithoutDeviceReplay() {
        LocalTime now = LocalTime.now(ZoneOffset.UTC);
        TimeWindowCondition current = new TimeWindowCondition("w", CalendarConstraint.everyDay(ZoneOffset.UTC),
                now.minusMinutes(1), now.plusMinutes(1));
        engine.register(plan("r", TriggerMode.EDGE, List.of(current), new Lifetime(null, null)));
        assertFalse(engine.inspect("r").roots().get("b"));
        observe("1");
        eventually(1);
    }

    @Test
    void ingressBroadcastReachesMatchingRuntimesOnDifferentWorkers() {
        // "r" and "s" belong to different workers with the two-worker fixture.
        engine.register(plan("r", TriggerMode.EDGE, List.of(), new Lifetime(null, null)));
        engine.register(plan("s", TriggerMode.EDGE, List.of(), new Lifetime(null, null)));
        observe("1");
        eventually(2);
        assertTrue(engine.inspect("r").roots().get("b"));
        assertTrue(engine.inspect("s").roots().get("b"));
    }

}
