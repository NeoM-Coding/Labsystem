package xyz.jasenon.lab.engine.runtime;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.LongAdder;

/**
 * Action 执行的内存计数器和有界失败诊断记录。
 *
 * <p>计数在进程生命周期内累计；失败历史只用于诊断，必须限制容量，
 * 避免长期持有无限增长的异常信息。</p>
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
        // 最新失败放在队首，超过容量时从队尾淘汰最旧记录。
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
