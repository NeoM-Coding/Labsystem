package xyz.jasenon.lab.engine.scheduling;

import xyz.jasenon.lab.engine.event.*;
import xyz.jasenon.lab.engine.execution.ActionDispatcher;
import xyz.jasenon.lab.engine.plan.RuntimePlan;
import xyz.jasenon.lab.engine.runtime.RuntimeInstance;
import xyz.jasenon.lab.engine.runtime.RuntimeSnapshot;
import xyz.jasenon.lab.observability.context.Tracing;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Owns local selection, runtime state and execution progress. No runtime registry escapes this thread. */
public final class WorkerLoop implements AutoCloseable {
    private final RuntimeSelector selector = new RuntimeSelector();
    private final BlockingQueue<Job<?>> queue;
    private final Semaphore wake = new Semaphore(0);
    private final Queue<Runnable> completions = new ConcurrentLinkedQueue<>();
    private final Set<RuntimeInstance> runnable = new LinkedHashSet<>();
    private final Consumer<RuntimeSnapshot> snapshotSink;
    private final Consumer<String> removalSink;
    private final ActionDispatcher dispatcher;
    private final TimeScheduler timers;
    private final Thread thread;
    private volatile boolean closed;

    public WorkerLoop(int id, int capacity, ActionDispatcher dispatcher, TimeScheduler timers,
                      Consumer<RuntimeSnapshot> snapshotSink, Consumer<String> removalSink) {
        this.snapshotSink = snapshotSink;
        this.removalSink = removalSink;
        this.dispatcher = dispatcher;
        this.timers = timers;
        queue = new ArrayBlockingQueue<>(capacity);
        thread = new Thread(this::run, "rule-v3-worker-" + id);
        thread.setDaemon(true);
        thread.start();
    }

    public CompletableFuture<RuntimeSnapshot> install(RuntimePlan plan, long generation, int pendingCapacity) {
        RuntimeInstance candidate = new RuntimeInstance(plan, generation, pendingCapacity);
        return submit(() -> true, () -> {
            Instant now = Instant.now();
            candidate.windows(new TimeWindowStatusEvent(plan.runtimeId(), generation, generation,
                    timers.statuses(plan, now), now), now, true);
            RuntimeSnapshot snapshot = candidate.snapshot(now);
            selector.install(candidate);
            publish(candidate);
            return snapshot;
        });
    }

    public CompletableFuture<Void> remove(String id) {
        return submit(() -> true, () -> {
            RuntimeInstance previous = selector.remove(id);
            if (previous != null) {
                previous.cancel();
                runnable.remove(previous);
            }
            removalSink.accept(id);
            return null;
        });
    }

    /** Select and advance local targets only after the entire broadcast has been admitted. */
    public CompletableFuture<Void> accept(EngineEvent event, CompletableFuture<Void> admission) {
        var propagation = Tracing.capture();
        admission.whenComplete((ignored, failure) -> wake.release());
        return submit(() -> {
            if (!admission.isDone()) return false;
            if (admission.isCompletedExceptionally()) return true;
            return selector.select(event).stream().allMatch(this::room);
        }, () -> {
            admission.join();
            return propagation.get(() -> {
                Instant now = Instant.now();
                for (RuntimeInstance runtime : selector.select(event)) {
                    if (!runtime.valid()) continue;
                    if (event instanceof DeviceEvent device) runtime.observe(device, now);
                    else if (event instanceof TimePointEvent point) runtime.pulse(point, now, () -> attempt(runtime));
                    else runtime.windows((TimeWindowStatusEvent) event, now, false);
                    publish(runtime);
                }
                return null;
            });
        });
    }

    public CompletableFuture<RuntimeSnapshot> synchronizeTime(String id, long generation, TimeScheduler.Tick tick) {
        return submit(() -> {
            RuntimeInstance runtime = selector.find(id);
            return runtime == null || runtime.generation != generation || room(runtime);
        }, () -> {
            RuntimeInstance runtime = selector.find(id);
            if (runtime == null || runtime.generation != generation) return null;
            Instant now = Instant.now();
            boolean activation = runtime.snapshot(now).state().equals("PENDING") && !runtime.plan.lifetime().pending(now);
            if (tick.pointIds().isEmpty()) {
                runtime.windows(new TimeWindowStatusEvent(id, generation, runtime.timerToken,
                        timers.statuses(runtime.plan, now), now), now, activation);
            } else {
                // Activate without evaluating an intermediate time root; the batch owns the decision.
                runtime.pulse(new TimePointEvent(id, generation, runtime.timerToken, tick.pointIds(),
                        timers.statuses(runtime.plan, now), tick.scheduledAt(), now), now, activation, () -> attempt(runtime));
            }
            publish(runtime);
            return runtime.snapshot(now);
        });
    }

    public CompletableFuture<RuntimeSnapshot> inspect(String id) {
        return submit(() -> true, () -> {
            RuntimeInstance runtime = selector.find(id);
            return runtime == null ? null : runtime.snapshot(Instant.now());
        });
    }

    private boolean room(RuntimeInstance runtime) {
        if (runtime.plan.lifetime().expired(Instant.now())) runtime.cancel();
        return !runtime.valid() || runtime.hasRoom();
    }

    private void publish(RuntimeInstance runtime) {
        snapshotSink.accept(runtime.snapshot(Instant.now()));
        touch(runtime);
    }

    private void touch(RuntimeInstance runtime) {
        if (runtime.needsPump()) runnable.add(runtime);
    }

    public int depth() {
        return queue.size();
    }

    private <T> CompletableFuture<T> submit(BooleanSupplier ready, Supplier<T> work) {
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            if (closed) throw new RejectedExecutionException("worker closed");
            Job<T> job = new Job<>(ready, work, result);
            while (!queue.offer(job, 10, TimeUnit.MILLISECONDS)) {
                if (closed) throw new RejectedExecutionException("worker closed");
            }
            if (closed && queue.remove(job)) result.completeExceptionally(new CancellationException("worker stopped"));
            wake.release();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result.completeExceptionally(e);
        }
        return result;
    }

    private void pump() {
        Runnable completion;
        while ((completion = completions.poll()) != null) completion.run();
        for (var iterator = runnable.iterator(); iterator.hasNext();) {
            RuntimeInstance runtime = iterator.next();
            attempt(runtime);
            if (selector.find(runtime.plan.runtimeId()) == runtime)
                snapshotSink.accept(runtime.snapshot(Instant.now()));
            if (!runtime.needsPump()) iterator.remove();
        }
    }

    private void attempt(RuntimeInstance runtime) {
        runtime.pump(dispatcher, () -> {
            completions.add(() -> { runtime.completed(); touch(runtime); });
            wake.release();
        }, Instant.now());
    }

    private <T> void execute(Job<T> job) {
        try {
            while (!closed && !job.ready().getAsBoolean()) {
                pump();
                wake.tryAcquire(2, TimeUnit.MILLISECONDS);
            }
            if (closed) throw new CancellationException("worker stopped");
            job.result().complete(job.work().get());
        } catch (Throwable e) {
            job.result().completeExceptionally(e);
        }
    }

    private void run() {
        try {
            while (!closed) {
                pump();
                Job<?> job = queue.poll();
                if (job != null) execute(job);
                else wake.tryAcquire(10, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            selector.close();
            runnable.clear();
            Job<?> job;
            while ((job = queue.poll()) != null)
                job.result().completeExceptionally(new CancellationException("worker stopped"));
        }
    }

    @Override
    public void close() {
        closed = true;
        thread.interrupt();
        try {
            thread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record Job<T>(BooleanSupplier ready, Supplier<T> work, CompletableFuture<T> result) {}
}
