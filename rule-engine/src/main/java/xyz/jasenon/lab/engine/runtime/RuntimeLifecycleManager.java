package xyz.jasenon.lab.engine.runtime;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 在用户配置的生命周期边界激活 PENDING Runtime，并主动注销到期 Runtime。
 *
 * <p>回调执行前会核对 slot 是否仍是当前实例，避免旧 Runtime 的延迟任务
 * 误操作同 runtimeId 的新实例。</p>
 */
@Component
public class RuntimeLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(RuntimeLifecycleManager.class);

    private final Clock clock;
    private final ScheduledExecutorService executor;
    private final ConcurrentHashMap<String, LifecycleSlot> slots = new ConcurrentHashMap<>();

    public RuntimeLifecycleManager() {
        this(
                Clock.systemUTC(),
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "rule-engine-lifecycle");
                    thread.setDaemon(true);
                    return thread;
                })
        );
    }

    RuntimeLifecycleManager(Clock clock, ScheduledExecutorService executor) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public Instant now() {
        return clock.instant();
    }

    public void track(Runtime runtime, Runnable onActivate, Runnable onExpire) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(onActivate, "onActivate");
        Objects.requireNonNull(onExpire, "onExpire");

        cancel(runtime.getRuntimeId());
        LifecycleSlot slot = new LifecycleSlot();
        slots.put(runtime.getRuntimeId(), slot);

        Instant now = clock.instant();
        if (runtime.getLifetime().isExpiredAt(now)) {
            slots.remove(runtime.getRuntimeId(), slot);
            runtime.expire();
            onExpire.run();
            return;
        }

        if (runtime.getLifetime().isPendingAt(now)) {
            slot.activateFuture = schedule(
                    runtime.getLifetime().activeFrom(),
                    () -> runIfCurrent(runtime.getRuntimeId(), slot, onActivate)
            );
        } else {
            onActivate.run();
        }

        if (runtime.getLifetime().activeUntil() != null) {
            slot.expireFuture = schedule(
                    runtime.getLifetime().activeUntil(),
                    () -> {
                        if (slots.remove(runtime.getRuntimeId(), slot)) {
                            slot.cancelled.set(true);
                            onExpire.run();
                        }
                    }
            );
        }
    }

    public void cancel(String runtimeId) {
        LifecycleSlot slot = slots.remove(runtimeId);
        if (slot != null) {
            slot.cancel();
        }
    }

    @PreDestroy
    public void shutdown() {
        slots.values().forEach(LifecycleSlot::cancel);
        slots.clear();
        executor.shutdownNow();
    }

    private ScheduledFuture<?> schedule(Instant instant, Runnable task) {
        long delay = Math.max(0, Duration.between(clock.instant(), instant).toNanos());
        return executor.schedule(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                log.warn("[RuleEngine] runtime lifecycle callback failed", e);
            }
        }, delay, TimeUnit.NANOSECONDS);
    }

    private void runIfCurrent(String runtimeId, LifecycleSlot slot, Runnable callback) {
        if (!slot.cancelled.get() && slots.get(runtimeId) == slot) {
            callback.run();
        }
    }

    private static final class LifecycleSlot {

        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile ScheduledFuture<?> activateFuture;
        private volatile ScheduledFuture<?> expireFuture;

        private void cancel() {
            if (!cancelled.compareAndSet(false, true)) {
                return;
            }
            if (activateFuture != null) {
                activateFuture.cancel(false);
            }
            if (expireFuture != null) {
                expireFuture.cancel(false);
            }
        }
    }
}
