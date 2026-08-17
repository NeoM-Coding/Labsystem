package xyz.jasenon.lab.engine.eval.v2;

import xyz.jasenon.lab.engine.eval.Operator;
import xyz.jasenon.lab.engine.eval.TypedValueParser;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** 类似 Rete Alpha 节点的原子条件比较，但不维护 Alpha Memory。 */
public final class PredicateNode implements ObservableValue<Boolean>, ValueObserver<String> {

    private final EventSourceNode source;
    private final Operator operator;
    private final String targetValue;
    private final ObservableSupport<Boolean> observable = new ObservableSupport<>();
    private final Observation sourceObservation;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile boolean value;

    PredicateNode(EventSourceNode source, Operator operator, String targetValue, boolean initialValue) {
        this.source = Objects.requireNonNull(source, "source");
        this.operator = Objects.requireNonNull(operator, "operator");
        this.targetValue = Objects.requireNonNull(targetValue, "targetValue");
        this.value = source.value() == null ? initialValue : evaluate(source.value());
        this.sourceObservation = source.observe(this);
    }

    public EventSourceNode source() {
        return source;
    }

    public Operator operator() {
        return operator;
    }

    public String targetValue() {
        return targetValue;
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
    public synchronized void onValueChanged(ObservableValue<String> ignored, String previous, String current) {
        if (closed.get()) {
            return;
        }
        boolean next = evaluate(current);
        boolean old = value;
        value = next;
        observable.publish(this, old, next);
    }

    int leafObserverCount() {
        return observable.size();
    }

    void close() {
        if (closed.compareAndSet(false, true)) {
            sourceObservation.close();
            observable.clear();
        }
    }

    private boolean evaluate(String eventValue) {
        try {
            return TypedValueParser.compare(
                    source.eventKey().deviceType(),
                    source.eventKey().field(),
                    operator,
                    eventValue,
                    targetValue
            );
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
