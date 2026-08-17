package xyz.jasenon.lab.engine.eval.v2;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** EvalRootHandle 的默认生命周期实现，不暴露 RootNode 的内部结构。 */
final class DefaultEvalRootHandle implements EvalRootHandle {

    private final EvalRootKey key;
    private final RootNode root;
    private final Runnable closeAction;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    DefaultEvalRootHandle(EvalRootKey key, RootNode root, Runnable closeAction) {
        this.key = Objects.requireNonNull(key, "key");
        this.root = Objects.requireNonNull(root, "root");
        this.closeAction = Objects.requireNonNull(closeAction, "closeAction");
    }

    @Override
    public EvalRootKey key() {
        return key;
    }

    @Override
    public boolean value() {
        return root.value();
    }

    @Override
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
