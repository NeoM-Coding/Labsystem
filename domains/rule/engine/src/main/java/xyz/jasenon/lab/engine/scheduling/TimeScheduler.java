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
import java.util.function.BooleanSupplier;

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

    public Handle prepare(RuntimePlan plan, Instant now, BooleanSupplier deliver) {
        Handle handle = new Handle();
        arm(handle, plan, now, deliver);
        return handle;
    }

    public void arm(Handle handle, RuntimePlan plan, Instant now, BooleanSupplier deliver) {
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
        if (next != null)
            handle.task(timer.schedule(() -> deliverOrRetry(handle, deliver), Math.max(0, Duration.between(Instant.now(), next).toMillis()), TimeUnit.MILLISECONDS));
    }

    private void deliverOrRetry(Handle handle, BooleanSupplier deliver) {
        if (handle.cancelled) return;
        if (!deliver.getAsBoolean())
            handle.task(timer.schedule(() -> deliverOrRetry(handle, deliver), 10, TimeUnit.MILLISECONDS));
    }

    public Map<String, Boolean> statuses(RuntimePlan plan, Instant at) {
        Map<String, Boolean> values = new LinkedHashMap<>();
        plan.timeGroups().forEach((id, windows) -> windows.forEach(w -> values.put(RuntimeInstance.windowKey(id, w.conditionId()), w.isWindowActive(at))));
        return Map.copyOf(values);
    }

    @Override
    @PreDestroy
    public void close() {
        timer.shutdownNow();
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
