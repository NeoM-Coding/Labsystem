package xyz.jasenon.lab.engine.runtime;

/**
 * Accepts runtimes that need inference without exposing scheduling details to Engine.
 */
public interface RuntimeScheduler {

    /**
     * Coalesces repeated requests for the same runtime and schedules asynchronous inference.
     */
    void schedule(Runtime runtime);

    /**
     * Prevents queued or dirty reruns after a runtime is removed from Engine.
     */
    void cancel(String runtimeId);
}
