package xyz.jasenon.lab.engine.runtime;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xyz.jasenon.lab.engine.action.Action;
import xyz.jasenon.lab.engine.action.ActionExecutionResult;
import xyz.jasenon.lab.engine.action.ActionGroup;
import xyz.jasenon.lab.engine.action.ActionGroupEvaluator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.time.Clock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 不同 Runtime 可以并行执行，同一个 Runtime 始终保持单飞。
 *
 * <p>执行期间到达的状态变化合并成一次补跑；TimePoint 保存在每个 Runtime
 * 独立的 FIFO 中，并按 occurrenceId 去重。</p>
 */
@Component
public class AsyncRuntimeScheduler implements RuntimeScheduler {

    private static final Logger log = LoggerFactory.getLogger(AsyncRuntimeScheduler.class);

    private final RuntimeExecutor runtimeExecutor;
    private final ExecutorService executorService;
    private final ActionGroupEvaluator actionGroupEvaluator;
    private final ConcurrentHashMap<String, RuntimeSlot> slots = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    @Autowired
    public AsyncRuntimeScheduler(RuntimeExecutor runtimeExecutor) {
        this(
                runtimeExecutor,
                Executors.newFixedThreadPool(
                        Math.max(2, java.lang.Runtime.getRuntime().availableProcessors() / 2),
                        namedThreadFactory("rule-engine-runtime")
                ),
                new ActionGroupEvaluator(Clock.systemUTC())
        );
    }

    AsyncRuntimeScheduler(RuntimeExecutor runtimeExecutor, ExecutorService executorService) {
        this(runtimeExecutor, executorService, new ActionGroupEvaluator(Clock.systemUTC()));
    }

    AsyncRuntimeScheduler(
            RuntimeExecutor runtimeExecutor,
            ExecutorService executorService,
            ActionGroupEvaluator actionGroupEvaluator
    ) {
        this.runtimeExecutor = Objects.requireNonNull(runtimeExecutor, "runtimeExecutor");
        this.executorService = Objects.requireNonNull(executorService, "executorService");
        this.actionGroupEvaluator = Objects.requireNonNull(actionGroupEvaluator, "actionGroupEvaluator");
    }

    @Override
    public void schedule(Runtime runtime, RuntimeSignal signal) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(signal, "signal");
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
        slot.request(signal);
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
        RuntimeSignal signal = slot.nextSignal();
        if (slot.cancelled.get() || signal == null) {
            release(slot);
            return;
        }

        CompletableFuture<Void> execution;
        try {
            execution = executeSatisfiedActionGroups(slot.runtime, signal);
        } catch (RuntimeException e) {
            execution = CompletableFuture.failedFuture(e);
        }
        // 必须等待本轮所有异步 Action 完成，期间 running 始终为 true，避免控制动作重入。
        execution.whenComplete((ignored, throwable) -> continueOrRelease(slot, throwable));
    }

    private CompletableFuture<Void> executeSatisfiedActionGroups(
            Runtime runtime,
            RuntimeSignal signal
    ) {
        List<CompletableFuture<ActionExecutionResult>> executions = new ArrayList<>();
        for (ActionGroup actionGroup : runtime.getActionGroups()) {
            if (actionGroupEvaluator.shouldExecute(runtime, actionGroup, signal)) {
                log.info(
                        "[RuleEngine] action group triggered, runtime-id:{}, action-group-id:{}",
                        runtime.getRuntimeId(),
                        actionGroup.getActionGroupId()
                );
                for (Action action : actionGroup.getActions()) {
                    if (!actionGroupEvaluator.isRuntimeActive(runtime)) {
                        break;
                    }
                    try {
                        executions.add(runtimeExecutor.execute(runtime, actionGroup, action));
                    } catch (RuntimeException e) {
                        executions.add(CompletableFuture.failedFuture(e));
                    }
                }
            }
        }
        // 空 ActionGroup 立即完成，但仍保留触发日志，便于确认规则条件确实命中。
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
        if (slot.hasPendingSignals()) {
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
        // 关闭 schedule() 在 running 释放前刚写入信号的竞态窗口。
        if (!slot.cancelled.get() && slot.hasPendingSignals()) {
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
        // running 覆盖条件推演以及本轮全部异步 Action Future 的完整生命周期。
        private final AtomicBoolean running = new AtomicBoolean(false);
        private final Object stateLock = new Object();
        // 状态事件仍只补跑一次，同时保留这批事件涉及的 ActionGroup 并集。
        private boolean stateDirty;
        private boolean allStateCandidates;
        private final Set<String> stateCandidateActionGroupIds = new HashSet<>();
        private final Queue<RuntimeSignal.TimePointOccurred> timePoints = new ConcurrentLinkedQueue<>();
        private final OccurrenceLedger occurrenceLedger = new OccurrenceLedger(1024);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        private RuntimeSlot(Runtime runtime) {
            this.runtime = runtime;
        }

        private void request(RuntimeSignal signal) {
            if (cancelled.get()) {
                return;
            }
            boolean accepted;
            if (signal instanceof RuntimeSignal.TimePointOccurred point) {
                accepted = occurrenceLedger.markIfNew(point.event().occurrenceId());
                if (accepted) {
                    timePoints.add(point);
                }
            } else {
                mergeStateChanged((RuntimeSignal.StateChanged) signal);
                accepted = true;
            }
            if (accepted) {
                start(this);
            }
        }

        private RuntimeSignal nextSignal() {
            RuntimeSignal stateSignal = takeStateChanged();
            if (stateSignal != null) {
                return stateSignal;
            }
            return timePoints.poll();
        }

        private boolean hasPendingSignals() {
            synchronized (stateLock) {
                return stateDirty || !timePoints.isEmpty();
            }
        }

        private void cancel() {
            cancelled.set(true);
            synchronized (stateLock) {
                stateDirty = false;
                allStateCandidates = false;
                stateCandidateActionGroupIds.clear();
            }
            timePoints.clear();
        }

        private void mergeStateChanged(RuntimeSignal.StateChanged stateChanged) {
            synchronized (stateLock) {
                stateDirty = true;
                if (stateChanged.targetsAll()) {
                    allStateCandidates = true;
                    stateCandidateActionGroupIds.clear();
                } else if (!allStateCandidates) {
                    stateCandidateActionGroupIds.addAll(stateChanged.candidateActionGroupIds());
                }
            }
        }

        private RuntimeSignal takeStateChanged() {
            synchronized (stateLock) {
                if (!stateDirty) {
                    return null;
                }
                RuntimeSignal signal = allStateCandidates
                        ? RuntimeSignal.stateChanged()
                        : RuntimeSignal.stateChanged(Set.copyOf(stateCandidateActionGroupIds));
                stateDirty = false;
                allStateCandidates = false;
                stateCandidateActionGroupIds.clear();
                return signal;
            }
        }
    }

    private static final class OccurrenceLedger {

        private final int limit;
        private final Map<String, Boolean> ids = new LinkedHashMap<>();

        private OccurrenceLedger(int limit) {
            this.limit = limit;
        }

        private synchronized boolean markIfNew(String occurrenceId) {
            if (ids.containsKey(occurrenceId)) {
                return false;
            }
            ids.put(occurrenceId, Boolean.TRUE);
            while (ids.size() > limit) {
                String oldest = ids.keySet().iterator().next();
                ids.remove(oldest);
            }
            return true;
        }
    }
}
