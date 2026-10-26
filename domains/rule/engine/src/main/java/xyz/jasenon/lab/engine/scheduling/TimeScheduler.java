package xyz.jasenon.lab.engine.scheduling;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import xyz.jasenon.lab.engine.plan.RuntimePlan;
import xyz.jasenon.lab.engine.runtime.RuntimeInstance;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.Set;
import java.util.LinkedHashSet;

/**
 * Only timer intents cross threads. Window state is always applied by the owning worker.
 */
@Component
public final class TimeScheduler implements AutoCloseable {
    private final ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1, r -> {
        Thread t = new Thread(r, "rule-v3-timer");
        t.setDaemon(true);
        return t;
    }
    );

    public TimeScheduler() {
        timer.setRemoveOnCancelPolicy(true);
    }

    public Handle prepare(RuntimePlan plan, Instant now, Function<Tick, Boolean> deliver) {
        Handle handle = new Handle();
        arm(handle, plan, now, deliver);
        return handle;
    }

    public void arm(Handle handle, RuntimePlan plan, Instant now, Function<Tick, Boolean> deliver) {
        Instant next = null;
        if (plan.lifetime().pending(now)) next = plan.lifetime().activeFrom();
        else if (!plan.lifetime().expired(now)) {
            next = plan.lifetime().activeUntil();
            for (var windows : plan.timeGroups().values())
                for (var window : windows) {
                    var transition = window.nextTransitionAfter(now);
                    if (transition.isPresent() && (next == null || transition.get().scheduledAt().isBefore(next)))
                        next = transition.get().scheduledAt();
                }
        }
        if (next != null) {
            Set<String> points = new LinkedHashSet<>();
            Instant scheduled = next;
            plan.timeGroups().forEach((group, conditions) -> conditions.stream().filter(c -> !c.isWindow()).forEach(c -> {
                // Pending activation may coincide exactly with a point.
                c.nextTransitionAfter(scheduled.minusNanos(1)).filter(t -> t.scheduledAt().equals(scheduled))
                        .ifPresent(t -> points.add(RuntimeInstance.windowKey(group, c.conditionId())));
            }));
            Tick tick = new Tick(scheduled, points);
            Duration remaining = Duration.between(Instant.now(), scheduled);
            long delay = Math.max(0, remaining.toMillis() + (remaining.getNano() % 1_000_000 == 0 ? 0 : 1));
            handle.task(timer.schedule(() -> deliverOrRetry(handle, deliver, tick), delay, TimeUnit.MILLISECONDS));
        }
    }

    private void deliverOrRetry(Handle handle, Function<Tick, Boolean> deliver, Tick tick) {
        if (handle.cancelled) return;
        if (!deliver.apply(tick))
            handle.task(timer.schedule(() -> deliverOrRetry(handle, deliver, tick), 10, TimeUnit.MILLISECONDS));
    }

    public Map<String, Boolean> statuses(RuntimePlan plan, Instant at) {
        Map<String, Boolean> values = new LinkedHashMap<>();
        plan.timeGroups().forEach((id, windows) -> windows.stream().filter(w -> w.isWindow()).forEach(w -> values.put(RuntimeInstance.windowKey(id, w.conditionId()), w.isWindowActive(at))));
        return Map.copyOf(values);
    }

    @Override
    @PreDestroy
    public void close() {
        timer.shutdownNow();
    }

    public record Tick(Instant scheduledAt, Set<String> pointIds) {
        public Tick { pointIds = Set.copyOf(pointIds); }
    }

    public static final class Handle {
        private volatile ScheduledFuture<?> task;
        private volatile boolean cancelled;

        public synchronized void cancel() {
            cancelled = true;
            if (task != null) task.cancel(false);
        }

        synchronized void task(ScheduledFuture<?> future) {
            if (cancelled) future.cancel(false);
            else task = future;
        }
    }
}
