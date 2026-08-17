package xyz.jasenon.lab.engine.eval.v2;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/** 通过组合保存观察者，避免为不同职责的节点引入统一且容易膨胀的基类。 */
final class ObservableSupport<T> {

    private final List<ValueObserver<T>> observers = new CopyOnWriteArrayList<>();

    Observation add(ValueObserver<T> observer) {
        ValueObserver<T> checked = Objects.requireNonNull(observer, "observer");
        observers.add(checked);
        AtomicBoolean active = new AtomicBoolean(true);
        return new Observation() {
            @Override
            public boolean active() {
                return active.get();
            }

            @Override
            public void close() {
                if (active.compareAndSet(true, false)) {
                    observers.remove(checked);
                }
            }
        };
    }

    void publish(ObservableValue<T> source, T previous, T current) {
        if (Objects.equals(previous, current)) {
            return;
        }
        for (ValueObserver<T> observer : observers) {
            observer.onValueChanged(source, previous, current);
        }
    }

    int size() {
        return observers.size();
    }

    void clear() {
        observers.clear();
    }
}
