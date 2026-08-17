package xyz.jasenon.lab.engine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.device.model.DeviceType;
import xyz.jasenon.lab.engine.definition.RuntimePlan;
import xyz.jasenon.lab.engine.eval.EvalNode;
import xyz.jasenon.lab.engine.eval.LogicType;
import xyz.jasenon.lab.engine.eval.Operator;
import xyz.jasenon.lab.engine.eval.v2.EvalForest;
import xyz.jasenon.lab.engine.event.DeviceEvent;
import xyz.jasenon.lab.engine.event.DeviceEventKey;
import xyz.jasenon.lab.engine.runtime.Runtime;
import xyz.jasenon.lab.engine.runtime.RuntimeActionGroup;
import xyz.jasenon.lab.engine.runtime.RuntimeLifecycleManager;
import xyz.jasenon.lab.engine.runtime.RuntimeLifetime;
import xyz.jasenon.lab.engine.runtime.RuntimeScheduler;
import xyz.jasenon.lab.engine.runtime.RuntimeSignal;
import xyz.jasenon.lab.engine.time.TimeConditionGroup;
import xyz.jasenon.lab.engine.time.TimeScheduleService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineTests {

    private final EvalForest forest = new EvalForest();
    private final RecordingScheduler scheduler = new RecordingScheduler();
    private final RuntimeLifecycleManager lifecycle = new RuntimeLifecycleManager();
    private final TimeScheduleService time = new TimeScheduleService();
    private final Engine engine = new Engine(forest, scheduler, lifecycle, time);

    @AfterEach
    void closeServices() {
        lifecycle.shutdown();
        time.shutdown();
    }

    @Test
    void routesOneForestUpdateToEveryAffectedRuntime() {
        engine.register(plan("runtime-a", "warm", "action-a", "26"));
        engine.register(plan("runtime-b", "hot", "action-b", "30"));
        scheduler.clear();

        engine.accept(event("31"));

        assertEquals(Set.of("runtime-a", "runtime-b"), scheduler.runtimeIds());
        assertEquals(2, scheduler.signals.size());
        assertEquals(1, forest.eventSourceCount());
        assertEquals(2, forest.predicateCount());
    }

    @Test
    void atomicallyReplacesRuntimeAndPreservesSharedSourceValue() {
        Runtime first = engine.register(plan("runtime-a", "temperature", "action-a", "26"));
        engine.accept(event("28"));
        assertTrue(first.deviceConditionSatisfied("temperature"));
        scheduler.clear();

        Runtime replacement = engine.register(plan("runtime-a", "temperature", "action-a", "30"));

        assertNotSame(first, replacement);
        assertSame(replacement, engine.runtime("runtime-a").orElseThrow());
        assertEquals(1, forest.treeCount());
        assertEquals(1, forest.eventSourceCount());
        assertEquals(1, forest.predicateCount());
        assertTrue(!replacement.deviceConditionSatisfied("temperature"));

        scheduler.clear();
        engine.accept(event("31"));
        assertEquals(Set.of("runtime-a"), scheduler.runtimeIds());
    }

    @Test
    void failedReplacementKeepsPreviousRuntimeAndTopology() {
        Runtime current = engine.register(plan("runtime-a", "temperature", "action-a", "26"));
        EvalNode invalid = new EvalNode();
        RuntimePlan broken = new RuntimePlan(
                "runtime-a",
                RuntimeLifetime.always(),
                Map.of("temperature", invalid),
                Set.of(),
                Map.of("always", TimeConditionGroup.always("always")),
                List.of(),
                Set.of()
        );

        assertThrows(IllegalArgumentException.class, () -> engine.register(broken));
        assertSame(current, engine.runtime("runtime-a").orElseThrow());
        assertEquals(1, forest.treeCount());

        scheduler.clear();
        engine.accept(event("28"));
        assertEquals(Set.of("runtime-a"), scheduler.runtimeIds());
    }

    @Test
    void removeReclaimsUnobservedForestNodes() {
        engine.register(plan("runtime-a", "temperature", "action-a", "26"));

        engine.remove("runtime-a");

        assertEquals(0, engine.runtimeCount());
        assertEquals(0, forest.treeCount());
        assertEquals(0, forest.predicateCount());
        assertEquals(0, forest.eventSourceCount());
    }

    @Test
    void pendingRuntimeKeepsForestCurrentAndEvaluatesLatestStateWhenActivated()
            throws InterruptedException {
        RuntimePlan original = plan("runtime-pending", "temperature", "action-a", "26");
        RuntimePlan pending = new RuntimePlan(
                original.runtimeId(),
                new RuntimeLifetime(Instant.now().plusMillis(500), null),
                original.deviceChains(),
                original.constantTrueGroups(),
                original.timeConditionGroups(),
                original.actionGroups(),
                original.requiredEventKeys()
        );
        Runtime runtime = engine.register(pending);

        engine.accept(event("28"));

        assertTrue(runtime.deviceConditionSatisfied("temperature"));
        assertEquals(0, scheduler.signalCount());
        assertTrue(await(() -> scheduler.signalCount() == 1));
        assertEquals(Set.of("runtime-pending"), scheduler.runtimeIds());
    }

    private static RuntimePlan plan(
            String runtimeId,
            String groupId,
            String actionGroupId,
            String threshold
    ) {
        EvalNode node = new EvalNode();
        node.setNodeId(groupId + "-condition");
        node.setDeviceType(DeviceType.AirCondition);
        node.setDeviceId("ac-1");
        node.setField("roomTemperature");
        node.setOperator(Operator.GT);
        node.setValue(threshold);
        node.setLogicToPrev(LogicType.AND);
        TimeConditionGroup always = TimeConditionGroup.always("always");
        DeviceEventKey key = new DeviceEventKey(
                DeviceType.AirCondition,
                "ac-1",
                "roomTemperature"
        );
        return new RuntimePlan(
                runtimeId,
                RuntimeLifetime.always(),
                Map.of(groupId, node),
                Set.of(),
                Map.of(always.getGroupId(), always),
                List.of(new RuntimeActionGroup(
                        actionGroupId,
                        groupId,
                        always,
                        List.of()
                )),
                Set.of(key)
        );
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

    private static boolean await(java.util.function.BooleanSupplier condition)
            throws InterruptedException {
        for (int index = 0; index < 100; index++) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(20);
        }
        return false;
    }

    private static final class RecordingScheduler implements RuntimeScheduler {

        private final List<Scheduled> signals = new ArrayList<>();

        @Override
        public synchronized void schedule(Runtime runtime, RuntimeSignal signal) {
            signals.add(new Scheduled(runtime.runtimeId(), runtime.generation(), signal));
        }

        @Override
        public void cancel(String runtimeId) {
        }

        synchronized void clear() {
            signals.clear();
        }

        synchronized Set<String> runtimeIds() {
            return signals.stream().map(Scheduled::runtimeId)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        synchronized int signalCount() {
            return signals.size();
        }
    }

    private record Scheduled(String runtimeId, long generation, RuntimeSignal signal) {
    }
}
