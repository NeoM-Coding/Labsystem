package xyz.jasenon.lab.engine.execution;

import xyz.jasenon.lab.engine.plan.RuntimePlan.Lifetime;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Only the cancellation fence is shared across threads; inference state never escapes its worker.
 */
public record ExecutionContext(String runtimeId, long generation, Lifetime lifetime, AtomicBoolean valid) {
    public boolean isActiveAt(Instant at) {
        return valid.get() && !lifetime.pending(at) && !lifetime.expired(at);
    }
}
