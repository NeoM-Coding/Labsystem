package xyz.jasenon.lab.engine.routing;

import xyz.jasenon.lab.observability.context.Tracing;

import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Serializes routing and topology commits. Does not evaluate rules or run actions.
 */
public final class EventLoop implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    private final Set<CompletableFuture<?>> outstanding = ConcurrentHashMap.newKeySet();

    public EventLoop(int capacity) {
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(capacity), r -> {
            Thread t = new Thread(r, "rule-v3-ingress");
            t.setDaemon(true);
            return t;
        }
                , new ThreadPoolExecutor.AbortPolicy());
    }

    public <T> CompletableFuture<T> submit(Supplier<T> operation) {
        CompletableFuture<T> result = new CompletableFuture<>();
        outstanding.add(result);
        result.whenComplete((v, e) -> outstanding.remove(result));
        var context = Tracing.capture();
        try {
            executor.execute(() -> context.get(() -> {
                        try {
                            result.complete(operation.get());
                        } catch (Throwable e) {
                            result.completeExceptionally(e);
                        }
                        return null;
                    }
            ));
        } catch (RejectedExecutionException e) {
            outstanding.remove(result);
            throw e;
        }
        return result;
    }

    public int depth() {
        return executor.getQueue().size();
    }

    public boolean internal(Runnable operation) {
        try {
            submit(() -> {
                        operation.run();
                        return null;
                    }
            ).whenComplete((v, e) -> {
                        if (e != null)
                            org.slf4j.LoggerFactory.getLogger(EventLoop.class).error("v3 internal command failed", e);
                    }
            );
            return true;
        } catch (RejectedExecutionException e) {
            return false;
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
        outstanding.forEach(f -> f.completeExceptionally(new CancellationException("ingress stopped")));
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
