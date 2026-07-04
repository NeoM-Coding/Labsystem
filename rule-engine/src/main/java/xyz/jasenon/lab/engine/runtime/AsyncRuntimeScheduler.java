package xyz.jasenon.lab.engine.runtime;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xyz.jasenon.lab.engine.action.Action;
import xyz.jasenon.lab.engine.action.ActionGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs different runtimes concurrently while keeping each individual runtime single-flight.
 *
 * <p>Events received during execution only mark the slot dirty. After the current
 * action futures complete, all dirty requests are coalesced into one rerun.</p>
 */
@Component
public class AsyncRuntimeScheduler implements RuntimeScheduler {

    private static final Logger log = LoggerFactory.getLogger(AsyncRuntimeScheduler.class);

    private final RuntimeExecutor runtimeExecutor;
    private final ExecutorService executorService;
    private final ConcurrentHashMap<String, RuntimeSlot> slots = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    @Autowired
    public AsyncRuntimeScheduler(RuntimeExecutor runtimeExecutor) {
        this(
                runtimeExecutor,
                Executors.newFixedThreadPool(
                        Math.max(2, java.lang.Runtime.getRuntime().availableProcessors() / 2),
                        namedThreadFactory("rule-engine-runtime")
                )
        );
    }

    AsyncRuntimeScheduler(RuntimeExecutor runtimeExecutor, ExecutorService executorService) {
        this.runtimeExecutor = Objects.requireNonNull(runtimeExecutor, "runtimeExecutor");
        this.executorService = Objects.requireNonNull(executorService, "executorService");
    }

    @Override
    public void schedule(Runtime runtime) {
        Objects.requireNonNull(runtime, "runtime");
        if (closed.get()) {
            throw new RejectedExecutionException("runtime scheduler is closed");
        }

        RuntimeSlot slot = slots.compute(runtime.getRuntimeId(), (runtimeId, current) -> {
            if (current == null || current.cancelled.get()) {
                return new RuntimeSlot(runtime);
            }
            current.runtime = runtime;
            return current;
        });
        slot.request();
    }

    @Override
    public void cancel(String runtimeId) {
        if (runtimeId == null) {
            return;
        }
        slots.computeIfPresent(runtimeId, (ignored, slot) -> {
            slot.cancel();
            return null;
        });
    }

    @PreDestroy
    public void shutdown() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        slots.values().forEach(RuntimeSlot::cancel);
        slots.clear();
        executorService.shutdownNow();
    }

    private void start(RuntimeSlot slot) {
        if (slot.cancelled.get() || !slot.running.compareAndSet(false, true)) {
            return;
        }
        try {
            submitDrain(slot);
        } catch (RejectedExecutionException e) {
            slot.running.set(false);
            throw e;
        }
    }

    private void drain(RuntimeSlot slot) {
        if (slot.cancelled.get() || !slot.dirty.getAndSet(false)) {
            release(slot);
            return;
        }

        CompletableFuture<Void> execution;
        try {
            execution = executeSatisfiedActionGroups(slot.runtime);
        } catch (RuntimeException e) {
            execution = CompletableFuture.failedFuture(e);
        }
        // Keep running=true until every asynchronous action in this inference has completed.
        execution.whenComplete((ignored, throwable) -> continueOrRelease(slot, throwable));
    }

    private CompletableFuture<Void> executeSatisfiedActionGroups(Runtime runtime) {
        List<CompletableFuture<ActionExecutionResult>> executions = new ArrayList<>();
        for (ActionGroup actionGroup : runtime.getActionGroups()) {
            if (actionGroup.getRoot().isOk()) {
                log.info(
                        "[RuleEngine] action group triggered, runtime-id:{}, action-group-id:{}",
                        runtime.getRuntimeId(),
                        actionGroup.getActionGroupId()
                );
                for (Action action : actionGroup.getActions()) {
                    try {
                        executions.add(runtimeExecutor.execute(runtime, actionGroup, action));
                    } catch (RuntimeException e) {
                        executions.add(CompletableFuture.failedFuture(e));
                    }
                }
            }
        }
        // An empty action group completes immediately but still emits the trigger log above.
        return CompletableFuture.allOf(executions.toArray(CompletableFuture[]::new));
    }

    private void continueOrRelease(RuntimeSlot slot, Throwable throwable) {
        if (throwable != null) {
            log.warn("[RuleEngine] runtime action execution completed exceptionally, runtime-id:{}",
                    slot.runtime.getRuntimeId(), throwable);
        }
        if (slot.cancelled.get()) {
            slot.running.set(false);
            return;
        }
        if (slot.dirty.get()) {
            try {
                submitDrain(slot);
            } catch (RejectedExecutionException e) {
                slot.running.set(false);
                log.warn("[RuleEngine] reject dirty runtime rerun, runtime-id:{}",
                        slot.runtime.getRuntimeId(), e);
            }
            return;
        }
        release(slot);
    }

    private void release(RuntimeSlot slot) {
        slot.running.set(false);
        // Close the race where schedule() sets dirty just before running becomes false.
        if (!slot.cancelled.get() && slot.dirty.get()) {
            start(slot);
        }
    }

    private void submitDrain(RuntimeSlot slot) {
        executorService.execute(() -> drain(slot));
    }

    private static ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private final class RuntimeSlot {

        private volatile Runtime runtime;
        // Covers inference and all asynchronous action futures for this runtime.
        private final AtomicBoolean running = new AtomicBoolean(false);
        // Boolean by design: any number of events during one run produce one latest-state rerun.
        private final AtomicBoolean dirty = new AtomicBoolean(false);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        private RuntimeSlot(Runtime runtime) {
            this.runtime = runtime;
        }

        private void request() {
            if (cancelled.get()) {
                return;
            }
            dirty.set(true);
            start(this);
        }

        private void cancel() {
            cancelled.set(true);
            dirty.set(false);
        }
    }
}
