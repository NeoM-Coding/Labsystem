package xyz.jasenon.lab.engine.routing;

import xyz.jasenon.lab.engine.event.*;
import xyz.jasenon.lab.engine.execution.ActionDispatcher;
import xyz.jasenon.lab.engine.plan.RuntimePlan;
import xyz.jasenon.lab.engine.runtime.RuntimeSnapshot;
import xyz.jasenon.lab.engine.scheduling.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Orders broadcasts and management barriers. Runtime matching belongs exclusively to workers. */
public final class EngineEventLoop implements AutoCloseable {
    private final EventLoop ingress;
    private final WorkerLoop[] workers;
    private final TimeScheduler timers;
    private final EngineOptions options;
    private final AtomicLong generations = new AtomicLong();
    // Timer/topology metadata only; no event-to-runtime subscription index here.
    private final Map<String, Route> routes = new HashMap<>();
    private final ConcurrentHashMap<String, RuntimeSnapshot> snapshots = new ConcurrentHashMap<>();

    public EngineEventLoop(ActionDispatcher actions, TimeScheduler timers, EngineOptions options) {
        this.timers = timers;
        this.options = options;
        ingress = new EventLoop(options.getIngressCapacity());
        workers = new WorkerLoop[options.getWorkers()];
        for (int i = 0; i < workers.length; i++)
            workers[i] = new WorkerLoop(i, options.getWorkerCapacity(), actions, timers,
                    snapshot -> snapshots.put(snapshot.runtimeId(), snapshot), snapshots::remove);
    }

    private WorkerLoop owner(String runtimeId) {
        return workers[Math.floorMod(runtimeId.hashCode(), workers.length)];
    }

    public CompletableFuture<RuntimeSnapshot> register(RuntimePlan plan) {
        Objects.requireNonNull(plan);
        if (plan.lifetime().expired(Instant.now()))
            throw new IllegalArgumentException("cannot install an expired runtime");
        long generation = generations.incrementAndGet();
        return ingress.submit(() -> {
            TimeScheduler.Handle handle = timers.prepare(plan, Instant.now(),
                    tick -> ingress.internal(() -> tick(plan.runtimeId(), generation, tick)));
            try {
                RuntimeSnapshot installed = owner(plan.runtimeId())
                        .install(plan, generation, options.getPendingCapacity()).join();
                Route previous = routes.put(plan.runtimeId(), new Route(plan, generation, handle));
                if (previous != null) previous.timer().cancel();
                return installed;
            } catch (RuntimeException e) {
                handle.cancel();
                throw e;
            }
        });
    }

    public CompletableFuture<Void> remove(String id) {
        return ingress.submit(() -> {
            removeAtIngress(id);
            return null;
        });
    }

    private void removeAtIngress(String id) {
        owner(id).remove(id).join();
        Route previous = routes.remove(id);
        if (previous != null) previous.timer().cancel();
    }

    public CompletableFuture<Void> accept(EngineEvent event) {
        Objects.requireNonNull(event);
        if (!(event instanceof DeviceEvent) && !(event instanceof TimeWindowStatusEvent) && !(event instanceof TimePointEvent))
            throw new IllegalArgumentException("unsupported event: " + event.getClass().getName());
        return ingress.submit(() -> broadcast(event)).thenCompose(result -> result);
    }

    private CompletableFuture<Void> broadcast(EngineEvent event) {
        CompletableFuture<Void> admission = new CompletableFuture<>();
        List<CompletableFuture<Void>> deliveries = new ArrayList<>();
        try {
            for (WorkerLoop worker : workers) {
                CompletableFuture<Void> delivery = worker.accept(event, admission);
                deliveries.add(delivery);
                // Before admission, an exceptional result can only be an enqueue/shutdown failure.
                if (delivery.isCompletedExceptionally()) delivery.join();
            }
            admission.complete(null);
        } catch (RuntimeException e) {
            admission.completeExceptionally(e);
            throw e;
        }
        return CompletableFuture.allOf(deliveries.toArray(CompletableFuture[]::new));
    }

    private void tick(String id, long generation, TimeScheduler.Tick tick) {
        Route route = routes.get(id);
        if (route == null || route.generation() != generation) return;
        if (route.plan().lifetime().expired(Instant.now())) {
            removeAtIngress(id);
            return;
        }
        RuntimeSnapshot snapshot = owner(id).synchronizeTime(id, generation, tick).join();
        if (snapshot != null && snapshot.state().equals("EXPIRED")) {
            removeAtIngress(id);
            return;
        }
        timers.arm(route.timer(), route.plan(), Instant.now(),
                next -> ingress.internal(() -> tick(id, generation, next)));
    }

    public Optional<RuntimeSnapshot> runtime(String id) {
        return Optional.ofNullable(snapshots.get(id));
    }

    public int runtimeCount() {
        return snapshots.size();
    }

    public RuntimeSnapshot inspect(String id) {
        return ingress.submit(() -> owner(id).inspect(id).join()).join();
    }

    public Map<String, Integer> queueDepths() {
        Map<String, Integer> depths = new LinkedHashMap<>();
        depths.put("ingress", ingress.depth());
        for (int i = 0; i < workers.length; i++) depths.put("worker-" + i, workers[i].depth());
        return Map.copyOf(depths);
    }

    @Override
    public void close() {
        ingress.close();
        for (WorkerLoop worker : workers) worker.close();
    }

    private record Route(RuntimePlan plan, long generation, TimeScheduler.Handle timer) {}
}
