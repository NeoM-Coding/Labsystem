package xyz.jasenon.lab.engine.runtime;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.LongAdder;

/**
 * In-memory action metrics and bounded diagnostic history.
 *
 * <p>Counters are cumulative for the process lifetime. Failure history is diagnostic
 * only and is capped to avoid retaining unbounded exception metadata.</p>
 */
@Component
public class ActionExecutionTracker {

    private static final int DEFAULT_FAILURE_HISTORY_LIMIT = 100;

    private final LongAdder successCount = new LongAdder();
    private final LongAdder failureCount = new LongAdder();
    private final ConcurrentLinkedDeque<ActionFailure> recentFailures = new ConcurrentLinkedDeque<>();
    private final int failureHistoryLimit;

    public ActionExecutionTracker() {
        this(DEFAULT_FAILURE_HISTORY_LIMIT);
    }

    ActionExecutionTracker(int failureHistoryLimit) {
        if (failureHistoryLimit <= 0) {
            throw new IllegalArgumentException("failureHistoryLimit must be positive");
        }
        this.failureHistoryLimit = failureHistoryLimit;
    }

    public void recordSuccess() {
        successCount.increment();
    }

    public synchronized void recordFailure(ActionFailure failure) {
        failureCount.increment();
        recentFailures.addFirst(failure);
        // Keep newest failures at the head and evict the oldest entries.
        while (recentFailures.size() > failureHistoryLimit) {
            recentFailures.pollLast();
        }
    }

    public long successCount() {
        return successCount.sum();
    }

    public long failureCount() {
        return failureCount.sum();
    }

    public List<ActionFailure> recentFailures() {
        return List.copyOf(recentFailures);
    }
}
