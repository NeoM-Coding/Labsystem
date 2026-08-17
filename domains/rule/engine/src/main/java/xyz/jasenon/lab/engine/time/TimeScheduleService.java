package xyz.jasenon.lab.engine.time;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xyz.jasenon.lab.engine.event.TimeEvent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 按唯一时间条件组调度每个条件的下一次边界，避免共享组重复注册和全局 Tick。
 */
@Component
public class TimeScheduleService {

    private static final Logger log = LoggerFactory.getLogger(TimeScheduleService.class);

    private final Clock clock;
    private final ScheduledExecutorService executor;
    private final ConcurrentHashMap<String, TimeScheduleSlot> slots = new ConcurrentHashMap<>();

    public TimeScheduleService() {
        this(
                Clock.systemUTC(),
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "rule-engine-time");
                    thread.setDaemon(true);
                    return thread;
                })
        );
    }

    TimeScheduleService(Clock clock, ScheduledExecutorService executor) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * 初始化当前窗口状态并调度后续边界。
     *
     * @return 当前是否至少有一个时间条件组处于有效窗口
     */
    /** 时间服务只生产边界事件，窗口状态保留在 Runtime 自身。 */
    public boolean track(
            xyz.jasenon.lab.engine.runtime.Runtime runtime,
            Consumer<TimeEvent> eventSink
    ) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(eventSink, "eventSink");

        return track(
                runtime.runtimeId(),
                runtime::initializeTimeConditions,
                runtime.timeConditionGroups().values(),
                eventSink
        );
    }

    private boolean track(
            String runtimeId,
            java.util.function.Function<Instant, Boolean> initializer,
            Iterable<TimeConditionGroup> timeConditionGroups,
            Consumer<TimeEvent> eventSink
    ) {

        cancel(runtimeId);
        TimeScheduleSlot slot = new TimeScheduleSlot(runtimeId, eventSink);
        slots.put(runtimeId, slot);

        Instant now = clock.instant();
        boolean activeWindow = initializer.apply(now);
        for (TimeConditionGroup timeConditionGroup : timeConditionGroups) {
            for (TimeCondition condition : timeConditionGroup.conditions()) {
                scheduleNext(slot, timeConditionGroup.getGroupId(), condition, now);
            }
        }
        return activeWindow;
    }

    public void cancel(String runtimeId) {
        TimeScheduleSlot slot = slots.remove(runtimeId);
        if (slot != null) {
            slot.cancel();
        }
    }

    @PreDestroy
    public void shutdown() {
        slots.values().forEach(TimeScheduleSlot::cancel);
        slots.clear();
        executor.shutdownNow();
    }

    private void scheduleNext(
            TimeScheduleSlot slot,
            String timeConditionGroupId,
            TimeCondition condition,
            Instant after
    ) {
        if (slot.cancelled.get()) {
            return;
        }
        condition.nextTransitionAfter(after).ifPresent(transition -> {
            long delay = Math.max(0, Duration.between(clock.instant(), transition.scheduledAt()).toNanos());
            slot.futures.removeIf(ScheduledFuture::isDone);
            ScheduledFuture<?> future = executor.schedule(
                    () -> emitAndContinue(slot, timeConditionGroupId, condition, transition),
                    delay,
                    TimeUnit.NANOSECONDS
            );
            slot.futures.add(future);
        });
    }

    private void emitAndContinue(
            TimeScheduleSlot slot,
            String timeConditionGroupId,
            TimeCondition condition,
            TimeTransition transition
    ) {
        if (slot.cancelled.get() || slots.get(slot.runtimeId) != slot) {
            return;
        }
        try {
            slot.eventSink.accept(new TimeEvent(
                    slot.runtimeId,
                    timeConditionGroupId,
                    condition.conditionId(),
                    transition.signal(),
                    transition.scheduledAt(),
                    clock.instant()
            ));
        } catch (RuntimeException e) {
            log.warn(
                    "[RuleEngine] time event delivery failed, runtime-id:{}, time-condition-group-id:{}, condition-id:{}",
                    slot.runtimeId,
                    timeConditionGroupId,
                    condition.conditionId(),
                    e
            );
        }
        // 回调延迟时跳过历史边界，从当前时间继续计算，当前默认采用 SKIP 型 misfire 语义。
        scheduleNext(slot, timeConditionGroupId, condition, clock.instant());
    }

    private static final class TimeScheduleSlot {

        private final String runtimeId;
        private final Consumer<TimeEvent> eventSink;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final ConcurrentLinkedQueue<ScheduledFuture<?>> futures = new ConcurrentLinkedQueue<>();

        private TimeScheduleSlot(String runtimeId, Consumer<TimeEvent> eventSink) {
            this.runtimeId = runtimeId;
            this.eventSink = eventSink;
        }

        private void cancel() {
            if (!cancelled.compareAndSet(false, true)) {
                return;
            }
            ScheduledFuture<?> future;
            while ((future = futures.poll()) != null) {
                future.cancel(false);
            }
        }
    }
}
