package xyz.jasenon.lab.engine.runtime;

/**
 * 接收需要推演的 Runtime，向 Engine 隐藏线程池和 mailbox 细节。
 */
public interface RuntimeScheduler {

    /**
     * 以普通状态变化信号调度 Runtime，重复请求可以合并。
     */
    default void schedule(Runtime runtime) {
        schedule(runtime, RuntimeSignal.stateChanged());
    }

    /**
     * 调度带具体语义的信号，TimePoint 等瞬时事件不可合并。
     */
    void schedule(Runtime runtime, RuntimeSignal signal);

    /**
     * Runtime 注销后取消排队信号，并阻止后续补跑。
     */
    void cancel(String runtimeId);
}
