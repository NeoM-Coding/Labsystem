package xyz.jasenon.lab.engine.eval.v2;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** 一个 Runtime 在全局森林中注册的全部设备条件根。 */
public final class EvalForestRegistration implements AutoCloseable {

    private final String runtimeId;
    private final Map<String, EvalRootHandle> roots;
    private final Runnable closeAction;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    EvalForestRegistration(
            String runtimeId,
            Map<String, EvalRootHandle> roots,
            Runnable closeAction
    ) {
        this.runtimeId = Objects.requireNonNull(runtimeId, "runtimeId");
        this.roots = Map.copyOf(Objects.requireNonNull(roots, "roots"));
        this.closeAction = Objects.requireNonNull(closeAction, "closeAction");
    }

    public String runtimeId() {
        return runtimeId;
    }

    public Map<String, EvalRootHandle> roots() {
        return roots;
    }

    public EvalRootHandle root(String conditionGroupId) {
        return roots.get(conditionGroupId);
    }

    public boolean closed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            closeAction.run();
        }
    }
}
