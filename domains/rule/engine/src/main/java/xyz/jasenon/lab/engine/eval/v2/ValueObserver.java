package xyz.jasenon.lab.engine.eval.v2;

@FunctionalInterface
public interface ValueObserver<T> {

    void onValueChanged(ObservableValue<T> source, T previous, T current);
}
