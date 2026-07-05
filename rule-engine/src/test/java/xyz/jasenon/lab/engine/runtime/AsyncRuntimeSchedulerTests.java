package xyz.jasenon.lab.engine.runtime;

import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.common.model.device.DeviceType;
import xyz.jasenon.lab.engine.action.Action;
import xyz.jasenon.lab.engine.action.ActionExecutionResult;
import xyz.jasenon.lab.engine.action.ActionGroup;
import xyz.jasenon.lab.engine.eval.EvalNode;
import xyz.jasenon.lab.engine.eval.LogicType;
import xyz.jasenon.lab.engine.eval.Operator;
import xyz.jasenon.lab.engine.event.TimeEvent;
import xyz.jasenon.lab.engine.event.TimeSignal;
import xyz.jasenon.lab.engine.time.CalendarConstraint;
import xyz.jasenon.lab.engine.time.TimeConditionGroup;
import xyz.jasenon.lab.engine.time.TimePointCondition;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncRuntimeSchedulerTests {

    @Test
    void executesOnlySatisfiedActionGroups() throws InterruptedException {
        RecordingRuntimeExecutor runtimeExecutor = new RecordingRuntimeExecutor(2);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        AsyncRuntimeScheduler scheduler = new AsyncRuntimeScheduler(runtimeExecutor, executorService);
        Runtime runtime = new Runtime("runtime-1");
        runtime.registerActionGroup(actionGroup("high-temperature", true));
        runtime.registerActionGroup(actionGroup("normal-temperature", true));
        runtime.registerActionGroup(actionGroup("too-high-temperature", false));

        try {
            scheduler.schedule(runtime);

            assertTrue(runtimeExecutor.await());
            assertEquals(
                    List.of("runtime-1:high-temperature", "runtime-1:normal-temperature"),
                    runtimeExecutor.executed
            );
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void coalescesDirtyRequestsAndKeepsOneTaskPerRuntime() throws InterruptedException {
        BlockingRuntimeExecutor runtimeExecutor = new BlockingRuntimeExecutor(2);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        AsyncRuntimeScheduler scheduler = new AsyncRuntimeScheduler(runtimeExecutor, executorService);
        Runtime runtime = runtime("runtime-1");

        try {
            scheduler.schedule(runtime);
            assertTrue(runtimeExecutor.awaitFirstStarted());

            scheduler.schedule(runtime);
            scheduler.schedule(runtime);
            scheduler.schedule(runtime);

            runtimeExecutor.releaseFirst();
            assertTrue(runtimeExecutor.awaitExecutions());
            Thread.sleep(100);

            assertEquals(2, runtimeExecutor.executionCount.get());
            assertEquals(1, runtimeExecutor.maxConcurrent.get());
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void unionsCandidateActionGroupsWhileRuntimeIsRunning() throws InterruptedException {
        BlockingRuntimeExecutor runtimeExecutor = new BlockingRuntimeExecutor(3);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        AsyncRuntimeScheduler scheduler = new AsyncRuntimeScheduler(runtimeExecutor, executorService);
        Runtime runtime = new Runtime("runtime-candidates");
        runtime.registerActionGroup(actionGroup("group-a", true));
        runtime.registerActionGroup(actionGroup("group-b", true));
        runtime.registerActionGroup(actionGroup("group-c", true));

        try {
            scheduler.schedule(runtime, RuntimeSignal.stateChanged(Set.of("group-a")));
            assertTrue(runtimeExecutor.awaitFirstStarted());

            scheduler.schedule(runtime, RuntimeSignal.stateChanged(Set.of("group-b")));
            scheduler.schedule(runtime, RuntimeSignal.stateChanged(Set.of("group-c")));

            runtimeExecutor.releaseFirst();
            assertTrue(runtimeExecutor.awaitExecutions());
            assertEquals(3, runtimeExecutor.executionCount.get());
            assertEquals(1, runtimeExecutor.maxConcurrent.get());
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void cancelPreventsDirtyRerun() throws InterruptedException {
        BlockingRuntimeExecutor runtimeExecutor = new BlockingRuntimeExecutor(1);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        AsyncRuntimeScheduler scheduler = new AsyncRuntimeScheduler(runtimeExecutor, executorService);
        Runtime runtime = runtime("runtime-1");

        try {
            scheduler.schedule(runtime);
            assertTrue(runtimeExecutor.awaitFirstStarted());
            scheduler.schedule(runtime);
            scheduler.cancel(runtime.getRuntimeId());

            runtimeExecutor.releaseFirst();
            assertTrue(runtimeExecutor.awaitExecutions());
            Thread.sleep(100);

            assertEquals(1, runtimeExecutor.executionCount.get());
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void preservesDistinctTimePointsAndDeduplicatesOccurrenceId() throws InterruptedException {
        RecordingRuntimeExecutor runtimeExecutor = new RecordingRuntimeExecutor(2);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        AsyncRuntimeScheduler scheduler = new AsyncRuntimeScheduler(runtimeExecutor, executorService);
        Runtime runtime = new Runtime("runtime-time");
        ActionGroup actionGroup = pointActionGroup("point-group", "point-1");
        runtime.registerActionGroup(actionGroup);
        Instant firstAt = Instant.parse("2026-07-05T00:00:00Z");
        Instant secondAt = firstAt.plusSeconds(60);
        TimeEvent first = new TimeEvent(
                runtime.getRuntimeId(),
                actionGroup.getTimeConditionGroupId(),
                "point-1",
                TimeSignal.TIME_POINT,
                firstAt,
                firstAt
        );
        TimeEvent second = new TimeEvent(
                runtime.getRuntimeId(),
                actionGroup.getTimeConditionGroupId(),
                "point-1",
                TimeSignal.TIME_POINT,
                secondAt,
                secondAt
        );

        try {
            scheduler.schedule(runtime, RuntimeSignal.timePoint(first));
            scheduler.schedule(runtime, RuntimeSignal.timePoint(first));
            scheduler.schedule(runtime, RuntimeSignal.timePoint(second));

            assertTrue(runtimeExecutor.await());
            Thread.sleep(100);
            assertEquals(2, runtimeExecutor.executed.size());
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void executesEveryActionGroupReferencingTheSameTimeConditionGroup() throws InterruptedException {
        RecordingRuntimeExecutor runtimeExecutor = new RecordingRuntimeExecutor(2);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        AsyncRuntimeScheduler scheduler = new AsyncRuntimeScheduler(runtimeExecutor, executorService);
        EvalNode dummy = new EvalNode();
        dummy.setResult(true);
        TimeConditionGroup sharedTimeGroup = new TimeConditionGroup(
                "shared-noon",
                List.of(new TimePointCondition(
                        "point-1",
                        CalendarConstraint.everyDay(ZoneOffset.UTC),
                        LocalTime.NOON
                ))
        );
        Runtime runtime = new Runtime("runtime-shared-time");
        ActionGroup firstGroup = new ActionGroup("notify-user", dummy, sharedTimeGroup);
        ActionGroup secondGroup = new ActionGroup("control-device", dummy, sharedTimeGroup);
        firstGroup.addAction(() -> Action.ActionType.Report);
        secondGroup.addAction(() -> Action.ActionType.Control);
        runtime.registerActionGroup(firstGroup);
        runtime.registerActionGroup(secondGroup);
        Instant scheduledAt = Instant.parse("2026-07-05T12:00:00Z");
        TimeEvent event = new TimeEvent(
                runtime.getRuntimeId(),
                sharedTimeGroup.getGroupId(),
                "point-1",
                TimeSignal.TIME_POINT,
                scheduledAt,
                scheduledAt
        );

        try {
            scheduler.schedule(runtime, RuntimeSignal.timePoint(event));

            assertTrue(runtimeExecutor.await());
            assertEquals(
                    List.of(
                            "runtime-shared-time:notify-user",
                            "runtime-shared-time:control-device"
                    ),
                    runtimeExecutor.executed
            );
        } finally {
            scheduler.shutdown();
        }
    }

    private static Runtime runtime(String runtimeId) {
        Runtime runtime = new Runtime(runtimeId);
        runtime.registerActionGroup(actionGroup("group-1", true));
        return runtime;
    }

    private static ActionGroup actionGroup(String actionGroupId, boolean result) {
        EvalNode dummy = new EvalNode();
        dummy.setResult(true);

        EvalNode node = new EvalNode();
        node.setNodeId(actionGroupId + "-node");
        node.setDeviceId("ac-1");
        node.setDeviceType(DeviceType.AirCondition);
        node.setField("roomTemperature");
        node.setOperator(Operator.GT);
        node.setValue("26");
        node.setLogicToPrev(LogicType.AND);
        node.setResult(result);
        dummy.setNext(node);
        ActionGroup actionGroup = new ActionGroup(actionGroupId, dummy);
        actionGroup.addAction(() -> Action.ActionType.Control);
        return actionGroup;
    }

    private static ActionGroup pointActionGroup(String actionGroupId, String conditionId) {
        EvalNode dummy = new EvalNode();
        dummy.setResult(true);
        TimeConditionGroup timeGroup = new TimeConditionGroup(List.of(
                new TimePointCondition(
                        conditionId,
                        CalendarConstraint.everyDay(ZoneOffset.UTC),
                        LocalTime.NOON
                )
        ));
        ActionGroup actionGroup = new ActionGroup(actionGroupId, dummy, timeGroup);
        actionGroup.addAction(() -> Action.ActionType.Control);
        return actionGroup;
    }

    private static class RecordingRuntimeExecutor implements RuntimeExecutor {

        private final CountDownLatch latch;
        private final List<String> executed = new CopyOnWriteArrayList<>();

        private RecordingRuntimeExecutor(int expectedExecutions) {
            latch = new CountDownLatch(expectedExecutions);
        }

        @Override
        public CompletableFuture<ActionExecutionResult> execute(
                Runtime runtime,
                ActionGroup actionGroup,
                Action action
        ) {
            executed.add(runtime.getRuntimeId() + ":" + actionGroup.getActionGroupId());
            latch.countDown();
            return CompletableFuture.completedFuture(success(runtime, actionGroup, action));
        }

        private boolean await() throws InterruptedException {
            return latch.await(2, TimeUnit.SECONDS);
        }
    }

    private static class BlockingRuntimeExecutor implements RuntimeExecutor {

        private final CountDownLatch firstStarted = new CountDownLatch(1);
        private final CountDownLatch executions;
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger maxConcurrent = new AtomicInteger();
        private final AtomicInteger executionCount = new AtomicInteger();
        private final CompletableFuture<ActionExecutionResult> firstExecution = new CompletableFuture<>();

        private BlockingRuntimeExecutor(int expectedExecutions) {
            executions = new CountDownLatch(expectedExecutions);
        }

        @Override
        public CompletableFuture<ActionExecutionResult> execute(
                Runtime runtime,
                ActionGroup actionGroup,
                Action action
        ) {
            int concurrent = active.incrementAndGet();
            maxConcurrent.accumulateAndGet(concurrent, Math::max);
            int execution = executionCount.incrementAndGet();
            CompletableFuture<ActionExecutionResult> future;
            if (execution == 1) {
                firstStarted.countDown();
                future = firstExecution;
            } else {
                future = CompletableFuture.completedFuture(success(runtime, actionGroup, action));
            }
            return future.whenComplete((ignored, throwable) -> {
                active.decrementAndGet();
                executions.countDown();
            });
        }

        private boolean awaitFirstStarted() throws InterruptedException {
            return firstStarted.await(2, TimeUnit.SECONDS);
        }

        private void releaseFirst() {
            firstExecution.complete(new ActionExecutionResult(
                    ActionExecutionResult.Status.SUCCESS,
                    "runtime-1",
                    "group-1",
                    Action.ActionType.Control,
                    "completed",
                    java.time.Instant.now()
            ));
        }

        private boolean awaitExecutions() throws InterruptedException {
            return executions.await(2, TimeUnit.SECONDS);
        }
    }

    private static ActionExecutionResult success(
            Runtime runtime,
            ActionGroup actionGroup,
            Action action
    ) {
        return ActionExecutionResult.success(
                runtime.getRuntimeId(),
                actionGroup.getActionGroupId(),
                action.is(),
                "completed"
        );
    }
}
