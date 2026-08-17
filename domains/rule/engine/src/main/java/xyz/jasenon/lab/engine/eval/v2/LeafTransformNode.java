package xyz.jasenon.lab.engine.eval.v2;

import xyz.jasenon.lab.engine.eval.LogicType;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** 将共享谓词结果适配为当前表达式位置所需的布尔函数。 */
public final class LeafTransformNode implements ObservableValue<BooleanTransform>, ValueObserver<Boolean> {

    private final PredicateNode predicate;
    private final LogicType logicToPrevious;
    private final ObservableSupport<BooleanTransform> observable = new ObservableSupport<>();
    private final Observation predicateObservation;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile BooleanTransform value;

    private LeafTransformNode(
            PredicateNode predicate,
            LogicType logicToPrevious,
            BooleanTransform initialValue
    ) {
        this.predicate = Objects.requireNonNull(predicate, "predicate");
        this.logicToPrevious = logicToPrevious;
        this.value = Objects.requireNonNull(initialValue, "initialValue");
        this.predicateObservation = predicate.observe(this);
    }

    static LeafTransformNode predicate(PredicateNode predicate, LogicType logicToPrevious) {
        Objects.requireNonNull(predicate, "predicate");
        LogicType logic = logicToPrevious == null ? LogicType.AND : logicToPrevious;
        return new LeafTransformNode(
                predicate,
                logic,
                transform(predicate.value(), logic)
        );
    }

    public PredicateNode predicate() {
        return predicate;
    }

    public LogicType logicToPrevious() {
        return logicToPrevious;
    }

    @Override
    public BooleanTransform value() {
        return value;
    }

    @Override
    public Observation observe(ValueObserver<BooleanTransform> observer) {
        return observable.add(observer);
    }

    @Override
    public synchronized void onValueChanged(ObservableValue<Boolean> ignored, Boolean previous, Boolean current) {
        if (closed.get()) {
            return;
        }
        BooleanTransform next = transform(current, logicToPrevious);
        BooleanTransform old = value;
        value = next;
        observable.publish(this, old, next);
    }

    void close() {
        if (closed.compareAndSet(false, true)) {
            predicateObservation.close();
            observable.clear();
        }
    }

    private static BooleanTransform transform(boolean predicateResult, LogicType logic) {
        return logic == LogicType.OR
                ? BooleanTransform.or(predicateResult)
                : BooleanTransform.and(predicateResult);
    }
}
