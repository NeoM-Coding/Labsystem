package xyz.jasenon.lab.engine.eval.v2;

/** 特化网络节点之间共享的最小可观察值契约。 */
public interface ObservableValue<T> {

    T value();

    Observation observe(ValueObserver<T> observer);
}
