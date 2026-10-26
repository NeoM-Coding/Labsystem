package xyz.jasenon.lab.engine;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import xyz.jasenon.lab.engine.event.EngineEvent;
import xyz.jasenon.lab.engine.execution.ActionDispatcher;
import xyz.jasenon.lab.engine.plan.RuntimePlan;
import xyz.jasenon.lab.engine.routing.EngineEventLoop;
import xyz.jasenon.lab.engine.runtime.RuntimeSnapshot;
import xyz.jasenon.lab.engine.scheduling.EngineOptions;
import xyz.jasenon.lab.engine.scheduling.TimeScheduler;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Public facade. Ordering/distribution belongs to the event loop; selection/inference belongs to workers. */
@Component
public final class Engine implements AutoCloseable {
    private final EngineEventLoop eventLoop;

    public Engine(ActionDispatcher actions, TimeScheduler timers, EngineOptions options) {
        eventLoop = new EngineEventLoop(actions, timers, options);
    }

    public RuntimeSnapshot register(RuntimePlan plan) {
        return registerAsync(plan).join();
    }

    public CompletableFuture<RuntimeSnapshot> registerAsync(RuntimePlan plan) {
        return eventLoop.register(plan);
    }

    public void remove(String id) {
        if (id == null || id.isBlank()) return;
        removeAsync(id).join();
    }

    public CompletableFuture<Void> removeAsync(String id) {
        return eventLoop.remove(id);
    }

    /** Completes after all workers have consumed the event, not after external actions finish. */
    public void accept(EngineEvent event) {
        acceptAsync(event).join();
    }

    public CompletableFuture<Void> acceptAsync(EngineEvent event) {
        return eventLoop.accept(event);
    }

    public Optional<RuntimeSnapshot> runtime(String id) {
        return eventLoop.runtime(id);
    }

    public int runtimeCount() {
        return eventLoop.runtimeCount();
    }

    public RuntimeSnapshot inspect(String id) {
        return eventLoop.inspect(id);
    }

    public Map<String, Integer> queueDepths() {
        return eventLoop.queueDepths();
    }

    @Override
    @PreDestroy
    public void close() {
        eventLoop.close();
    }
}
