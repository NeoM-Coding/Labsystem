package xyz.jasenon.lab.engine.runtime;

import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.engine.action.Action;
import xyz.jasenon.lab.engine.action.ActionExecutionResult;
import xyz.jasenon.lab.engine.eval.v2.EvalForest;
import xyz.jasenon.lab.engine.event.TimeEvent;
import xyz.jasenon.lab.engine.event.TimeSignal;
import xyz.jasenon.lab.engine.notification.RuleExecutionNotice;
import xyz.jasenon.lab.engine.time.CalendarConstraint;
import xyz.jasenon.lab.engine.time.TimeConditionGroup;
import xyz.jasenon.lab.engine.time.TimePointCondition;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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

    private static final Action CONTROL = () -> Action.ActionType.Control;
    private static final TimeConditionGroup ALWAYS_TIME = TimeConditionGroup.always("always-time");

    @Test
    void executesOnlyCandidateActionGroups() throws Exception {
        RecordingExecutor executor = new RecordingExecutor(2);
        AsyncRuntimeScheduler scheduler = scheduler(executor);
        Runtime runtime = runtime("runtime-1", List.of(
                group("a", CONTROL), group("b", CONTROL), group("c", CONTROL)
        ));
        try {
            scheduler.schedule(runtime, RuntimeSignal.stateChanged(Set.of("a", "c")));
            assertTrue(executor.await());
            assertEquals(Set.of("runtime-1:a", "runtime-1:c"), Set.copyOf(executor.executed));
        } finally {
            scheduler.shutdown();
            runtime.close();
        }
    }

    @Test
    void coalescesDirtySignalsAndKeepsRuntimeSingleFlight() throws Exception {
        BlockingExecutor executor = new BlockingExecutor(2);
        AsyncRuntimeScheduler scheduler = scheduler(executor);
        Runtime runtime = runtime("runtime-1", List.of(group("a", CONTROL)));
        try {
            scheduler.schedule(runtime);
            assertTrue(executor.firstStarted.await(2, TimeUnit.SECONDS));
            scheduler.schedule(runtime);
            scheduler.schedule(runtime);
            executor.releaseFirst();
            assertTrue(executor.completed.await(2, TimeUnit.SECONDS));
            assertEquals(2, executor.count.get());
            assertEquals(1, executor.maxConcurrent.get());
        } finally {
            scheduler.shutdown();
            runtime.close();
        }
    }

    @Test
    void unionsCandidateGroupsWhileRuntimeIsRunning() throws Exception {
        BlockingExecutor executor = new BlockingExecutor(3);
        AsyncRuntimeScheduler scheduler = scheduler(executor);
        Runtime runtime = runtime("runtime-1", List.of(
                group("a", CONTROL), group("b", CONTROL), group("c", CONTROL)
        ));
        try {
            scheduler.schedule(runtime, RuntimeSignal.stateChanged(Set.of("a")));
            assertTrue(executor.firstStarted.await(2, TimeUnit.SECONDS));
            scheduler.schedule(runtime, RuntimeSignal.stateChanged(Set.of("b")));
            scheduler.schedule(runtime, RuntimeSignal.stateChanged(Set.of("c")));
            executor.releaseFirst();
            assertTrue(executor.completed.await(2, TimeUnit.SECONDS));
            assertEquals(3, executor.count.get());
        } finally {
            scheduler.shutdown();
            runtime.close();
        }
    }

    @Test
    void preservesTimePointFifoAndDeduplicatesOccurrence() throws Exception {
        TimeConditionGroup pointGroup = new TimeConditionGroup("point-time", List.of(
                new TimePointCondition(
                        "point-1",
                        CalendarConstraint.everyDay(ZoneOffset.UTC),
                        LocalTime.NOON
                )
        ));
        RuntimeActionGroup actionGroup = new RuntimeActionGroup(
                "point-action", "always", pointGroup, List.of(CONTROL)
        );
        Runtime runtime = runtime("runtime-time", List.of(actionGroup), pointGroup);
        RecordingExecutor executor = new RecordingExecutor(2);
        AsyncRuntimeScheduler scheduler = scheduler(executor);
        Instant firstAt = Instant.parse("2026-07-05T12:00:00Z");
        TimeEvent first = new TimeEvent(
                runtime.runtimeId(), pointGroup.getGroupId(), "point-1",
                TimeSignal.TIME_POINT, firstAt, firstAt
        );
        TimeEvent second = new TimeEvent(
                runtime.runtimeId(), pointGroup.getGroupId(), "point-1",
                TimeSignal.TIME_POINT, firstAt.plusSeconds(60), firstAt.plusSeconds(60)
        );
        try {
            scheduler.schedule(runtime, RuntimeSignal.timePoint(first));
            scheduler.schedule(runtime, RuntimeSignal.timePoint(first));
            scheduler.schedule(runtime, RuntimeSignal.timePoint(second));
            assertTrue(executor.await());
            assertEquals(2, executor.executed.size());
        } finally {
            scheduler.shutdown();
            runtime.close();
        }
    }

    @Test
    void publishesNoticeAfterActionsComplete() throws Exception {
        RecordingExecutor executor = new RecordingExecutor(1);
        List<RuleExecutionNotice> notices = new CopyOnWriteArrayList<>();
        CountDownLatch published = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        AsyncRuntimeScheduler scheduler = new AsyncRuntimeScheduler(
                executor,
                notice -> {
                    notices.add(notice);
                    published.countDown();
                },
                pool,
                new RuntimeActionGroupEvaluator(Clock.systemUTC())
        );
        Runtime runtime = runtime("runtime-notice", List.of(group("notice", CONTROL)));
        try {
            scheduler.schedule(runtime);
            assertTrue(published.await(2, TimeUnit.SECONDS));
            assertEquals("runtime-notice", notices.get(0).runtimeId());
            assertEquals("notice", notices.get(0).actionGroupId());
            assertEquals(1, notices.get(0).actions().size());
        } finally {
            scheduler.shutdown();
            runtime.close();
        }
    }

    private static AsyncRuntimeScheduler scheduler(RuntimeExecutor executor) {
        return new AsyncRuntimeScheduler(executor, Executors.newFixedThreadPool(2));
    }

    private static RuntimeActionGroup group(String id, Action... actions) {
        return new RuntimeActionGroup(
                id,
                "always",
                ALWAYS_TIME,
                List.of(actions)
        );
    }

    private static Runtime runtime(String id, List<RuntimeActionGroup> groups) {
        return runtime(id, groups, groups.get(0).timeConditionGroup());
    }

    private static Runtime runtime(
            String id,
            List<RuntimeActionGroup> groups,
            TimeConditionGroup timeGroup
    ) {
        EvalForest forest = new EvalForest();
        return new Runtime(
                id,
                RuntimeLifetime.always(),
                forest.registerRuntime(id, Map.of(), Set.of("always")),
                Map.of(timeGroup.getGroupId(), timeGroup),
                groups
        );
    }

    private static ActionExecutionResult success(
            Runtime runtime,
            RuntimeActionGroup group,
            Action action
    ) {
        return ActionExecutionResult.success(
                runtime.runtimeId(), group.actionGroupId(), action.is(), "completed"
        );
    }

    private static class RecordingExecutor implements RuntimeExecutor {
        private final CountDownLatch completed;
        private final List<String> executed = new CopyOnWriteArrayList<>();

        private RecordingExecutor(int count) {
            this.completed = new CountDownLatch(count);
        }

        @Override
        public CompletableFuture<ActionExecutionResult> execute(
                Runtime runtime,
                RuntimeActionGroup group,
                Action action
        ) {
            executed.add(runtime.runtimeId() + ":" + group.actionGroupId());
            completed.countDown();
            return CompletableFuture.completedFuture(success(runtime, group, action));
        }

        boolean await() throws InterruptedException {
            return completed.await(2, TimeUnit.SECONDS);
        }
    }

    private static final class BlockingExecutor implements RuntimeExecutor {
        private final CountDownLatch firstStarted = new CountDownLatch(1);
        private final CountDownLatch completed;
        private final CompletableFuture<ActionExecutionResult> first = new CompletableFuture<>();
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger maxConcurrent = new AtomicInteger();
        private final AtomicInteger count = new AtomicInteger();

        private BlockingExecutor(int expected) {
            this.completed = new CountDownLatch(expected);
        }

        @Override
        public CompletableFuture<ActionExecutionResult> execute(
                Runtime runtime,
                RuntimeActionGroup group,
                Action action
        ) {
            int concurrent = active.incrementAndGet();
            maxConcurrent.accumulateAndGet(concurrent, Math::max);
            CompletableFuture<ActionExecutionResult> result;
            if (count.incrementAndGet() == 1) {
                firstStarted.countDown();
                result = first;
            } else {
                result = CompletableFuture.completedFuture(success(runtime, group, action));
            }
            return result.whenComplete((ignored, failure) -> {
                active.decrementAndGet();
                completed.countDown();
            });
        }

        void releaseFirst() {
            first.complete(new ActionExecutionResult(
                    ActionExecutionResult.Status.SUCCESS,
                    "runtime-1", "a", Action.ActionType.Control, "completed", Instant.now()
            ));
        }
    }
}
