package xyz.jasenon.lab.engine.eval.v2;

import java.util.Objects;

/** 一棵表达式树可被观察的网络出口。 */
public final class RootNode implements ObservableValue<Boolean>, ValueObserver<BooleanTransform> {

    private final String groupId;
    private final ObservableValue<BooleanTransform> input;
    private final RootChangeListener listener;
    private final ObservableSupport<Boolean> observable = new ObservableSupport<>();
    private volatile boolean value;

    RootNode(String groupId, ObservableValue<BooleanTransform> input, RootChangeListener listener) {
        this.groupId = requireText(groupId);
        this.input = Objects.requireNonNull(input, "input");
        this.listener = Objects.requireNonNull(listener, "listener");
        // true 是 AND 折叠的单位元，首项在编译时统一归一为 AND。
        this.value = input.value().apply(true);
        input.observe(this);
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
    public void observe(ValueObserver<Boolean> observer) {
        observable.add(observer);
    }

    @Override
    public synchronized void onValueChanged(
            ObservableValue<BooleanTransform> source,
            BooleanTransform previous,
            BooleanTransform current
    ) {
        boolean next = current.apply(true);
        boolean old = value;
        value = next;
        if (old != next) {
            listener.changed(groupId, old, next);
            observable.publish(this, old, next);
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
