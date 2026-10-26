package xyz.jasenon.lab.engine.scheduling;

import org.junit.jupiter.api.*;
import xyz.jasenon.lab.device.model.DeviceType;
import xyz.jasenon.lab.engine.action.ActionExecutionResult;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.TriggerMode;
import xyz.jasenon.lab.engine.eval.*;
import xyz.jasenon.lab.engine.event.*;
import xyz.jasenon.lab.engine.execution.ActionDispatcher;
import xyz.jasenon.lab.engine.plan.RuntimePlan;
import xyz.jasenon.lab.engine.plan.RuntimePlan.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkerSelectorTests {
    private final DeviceEventKey key = new DeviceEventKey(DeviceType.AirCondition, "ac-1", "opened");
    private final Queue<String> snapshotThreads = new ConcurrentLinkedQueue<>();
    private final Queue<String> snapshotIds = new ConcurrentLinkedQueue<>();
    private TimeScheduler timers;
    private ActionDispatcher actions;
    private WorkerLoop first;
    private WorkerLoop second;

    @BeforeEach void setup() {
        var options = new EngineOptions();
        timers = new TimeScheduler();
        actions = new ActionDispatcher((context, group, action) -> CompletableFuture.completedFuture(
                ActionExecutionResult.success(context.runtimeId(), group.actionGroupId(), action.is(), "ok")),
                notice -> {}, options);
        first = new WorkerLoop(0, 8, actions, timers, snapshot -> {snapshotThreads.add(Thread.currentThread().getName());snapshotIds.add(snapshot.runtimeId());}, id -> {});
        second = new WorkerLoop(1, 8, actions, timers, snapshot -> {snapshotThreads.add(Thread.currentThread().getName());snapshotIds.add(snapshot.runtimeId());}, id -> {});
    }

    @AfterEach void close() {
        first.close();
        second.close();
        timers.close();
        actions.close();
    }

    private RuntimePlan plan(String id, DeviceEventKey eventKey) {
        return new RuntimePlan(id, new Lifetime(null, null),
                Map.of("d", List.of(new Predicate("p", eventKey, Operator.EQ, "true", LogicType.AND))),
                Map.of("t", List.of()), Map.of("a", new ActionGroup("a", List.of())),
                List.of(new Binding("b", "d", "t", "a", TriggerMode.EDGE)));
    }

    private DeviceEvent event(DeviceEventKey eventKey) {
        return new DeviceEvent(eventKey.deviceType(), eventKey.deviceId(), eventKey.field(), "true", Instant.now());
    }

    @Test void eachWorkerSelectsAllMatchingLocalRuntimesOnly() throws Exception {
        var other = new DeviceEventKey(DeviceType.AirCondition, "ac-other", "opened");
        first.install(plan("a", key), 1, 16).join();
        first.install(plan("b", key), 2, 16).join();
        first.install(plan("unrelated", other), 3, 16).join();
        second.install(plan("c", key), 4, 16).join();
        snapshotIds.clear();

        var admission = new CompletableFuture<Void>();
        var delivery = event(key);
        var a = first.accept(delivery, admission);
        var b = second.accept(delivery, admission);
        assertFalse(a.isDone());
        assertFalse(b.isDone());
        admission.complete(null);
        CompletableFuture.allOf(a, b).get(2, TimeUnit.SECONDS);

        assertTrue(first.inspect("a").join().roots().get("b"));
        assertTrue(first.inspect("b").join().roots().get("b"));
        assertTrue(second.inspect("c").join().roots().get("b"));
        assertFalse(first.inspect("unrelated").join().roots().get("b"));
        assertNull(second.inspect("a").join());
        assertFalse(snapshotIds.contains("unrelated"));
        assertTrue(snapshotThreads.contains("rule-v3-worker-0"));
        assertTrue(snapshotThreads.contains("rule-v3-worker-1"));
        assertTrue(snapshotThreads.stream().allMatch(name -> name.startsWith("rule-v3-worker-")));
    }

    @Test void failedBroadcastAdmissionAdvancesNoTrees() {
        first.install(plan("a", key), 1, 16).join();
        second.install(plan("b", key), 2, 16).join();
        var admission = new CompletableFuture<Void>();
        var a = first.accept(event(key), admission);
        var b = second.accept(event(key), admission);

        admission.completeExceptionally(new RejectedExecutionException("broadcast aborted"));

        assertThrows(CompletionException.class, a::join);
        assertThrows(CompletionException.class, b::join);
        assertFalse(first.inspect("a").join().roots().get("b"));
        assertFalse(second.inspect("b").join().roots().get("b"));
    }

    @Test void replacementAndRemovalUpdateLocalSelector() {
        var replacementKey = new DeviceEventKey(DeviceType.AirCondition, "ac-2", "opened");
        first.install(plan("a", key), 1, 16).join();
        first.install(plan("a", replacementKey), 2, 16).join();

        first.accept(event(key), CompletableFuture.completedFuture(null)).join();
        assertFalse(first.inspect("a").join().roots().get("b"));
        first.accept(event(replacementKey), CompletableFuture.completedFuture(null)).join();
        assertTrue(first.inspect("a").join().roots().get("b"));

        first.remove("a").join();
        first.accept(event(replacementKey), CompletableFuture.completedFuture(null)).join();
        assertNull(first.inspect("a").join());
    }

    @Test void windowEventsAreSelectedByOwnerAndGeneration() {
        RuntimePlan base = plan("a", key);
        var window = new xyz.jasenon.lab.engine.time.TimeWindowCondition("w",
                xyz.jasenon.lab.engine.time.CalendarConstraint.everyDay(java.time.ZoneOffset.UTC),
                java.time.LocalTime.of(1, 0), java.time.LocalTime.of(2, 0));
        var times = Map.of("t", List.of(window));
        first.install(new RuntimePlan("a", base.lifetime(), base.conditionGroups(), times,
                base.actionGroups(), base.bindings()), 1, 16).join();
        second.install(new RuntimePlan("b", base.lifetime(), base.conditionGroups(), times,
                base.actionGroups(), base.bindings()), 2, 16).join();
        String windowKey = xyz.jasenon.lab.engine.runtime.RuntimeInstance.windowKey("t", "w");
        first.accept(new TimeWindowStatusEvent("a", 1, 1, Map.of(windowKey, false), Instant.now()),
                CompletableFuture.completedFuture(null)).join();
        second.accept(new TimeWindowStatusEvent("b", 2, 2, Map.of(windowKey, false), Instant.now()),
                CompletableFuture.completedFuture(null)).join();
        first.accept(event(key), CompletableFuture.completedFuture(null)).join();
        second.accept(event(key), CompletableFuture.completedFuture(null)).join();

        var change = new TimeWindowStatusEvent("a", 1, 1, Map.of(windowKey, true), Instant.now());
        var admission = new CompletableFuture<Void>();
        var a = first.accept(change, admission);
        var b = second.accept(change, admission);
        admission.complete(null);
        CompletableFuture.allOf(a, b).join();

        assertTrue(first.inspect("a").join().roots().get("b"));
        assertFalse(second.inspect("b").join().roots().get("b"));
        first.accept(new TimeWindowStatusEvent("a", 99, 99, Map.of(windowKey, false), Instant.now()),
                CompletableFuture.completedFuture(null)).join();
        first.accept(new TimeWindowStatusEvent("a", 1, 99, Map.of(windowKey, false), Instant.now()),
                CompletableFuture.completedFuture(null)).join();
        assertTrue(first.inspect("a").join().roots().get("b"));
    }
}
