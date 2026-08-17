package xyz.jasenon.lab.engine.eval.v2;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** 一棵表达式树可被观察的网络出口。 */
public final class RootNode implements ObservableValue<Boolean>, ValueObserver<BooleanTransform> {

    private final String groupId;
    private final ObservableValue<BooleanTransform> input;
    private final RootChangeListener listener;
    private final ObservableSupport<Boolean> observable = new ObservableSupport<>();
    private final Observation inputObservation;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile boolean value;

    RootNode(String groupId, ObservableValue<BooleanTransform> input, RootChangeListener listener) {
        this.groupId = requireText(groupId);
        this.input = Objects.requireNonNull(input, "input");
        this.listener = Objects.requireNonNull(listener, "listener");
        // true 是 AND 折叠的单位元，首项在编译时统一归一为 AND。
        this.value = input.value().apply(true);
        this.inputObservation = input.observe(this);
    }

    public String groupId() {
        return groupId;
    }

    public ObservableValue<BooleanTransform> input() {
        return input;
    }

    @Override
    public Boolean value() {
        return value;
    }

    @Override
    public Observation observe(ValueObserver<Boolean> observer) {
        return observable.add(observer);
    }

    @Override
    public synchronized void onValueChanged(
            ObservableValue<BooleanTransform> source,
            BooleanTransform previous,
            BooleanTransform current
    ) {
        if (closed.get()) {
            return;
        }
        boolean next = current.apply(true);
        boolean old = value;
        value = next;
        if (old != next) {
            listener.changed(groupId, old, next);
            observable.publish(this, old, next);
        }
    }

    void close() {
        if (closed.compareAndSet(false, true)) {
            inputObservation.close();
            observable.clear();
        }
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("groupId must not be blank");
        }
        return value;
    }

    @FunctionalInterface
    interface RootChangeListener {
        void changed(String groupId, boolean previous, boolean current);
    }
}
