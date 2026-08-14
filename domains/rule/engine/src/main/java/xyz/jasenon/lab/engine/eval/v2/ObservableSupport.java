package xyz.jasenon.lab.engine.eval.v2;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/** 通过组合保存观察者，避免为不同职责的节点引入统一且容易膨胀的基类。 */
final class ObservableSupport<T> {

    private final List<ValueObserver<T>> observers = new CopyOnWriteArrayList<>();

    void add(ValueObserver<T> observer) {
        observers.add(Objects.requireNonNull(observer, "observer"));
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
}
