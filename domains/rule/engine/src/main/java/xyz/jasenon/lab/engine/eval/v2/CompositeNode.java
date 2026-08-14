package xyz.jasenon.lab.engine.eval.v2;

import java.util.Objects;

/** 类似 Rete Beta 网络的函数复合节点，但不进行元组连接，也不维护 Beta Memory。 */
public final class CompositeNode implements ObservableValue<BooleanTransform>, ValueObserver<BooleanTransform> {

    private final ObservableValue<BooleanTransform> left;
    private final ObservableValue<BooleanTransform> right;
    private final ObservableSupport<BooleanTransform> observable = new ObservableSupport<>();
    private volatile BooleanTransform leftValue;
    private volatile BooleanTransform rightValue;
    private volatile BooleanTransform value;

    public CompositeNode(
            ObservableValue<BooleanTransform> left,
            ObservableValue<BooleanTransform> right
    ) {
        this.left = Objects.requireNonNull(left, "left");
        this.right = Objects.requireNonNull(right, "right");
        this.leftValue = left.value();
        this.rightValue = right.value();
        this.value = compose();
        left.observe(this);
        right.observe(this);
    }

    public ObservableValue<BooleanTransform> left() {
        return left;
    }

    public ObservableValue<BooleanTransform> right() {
        return right;
    }

    @Override
    public BooleanTransform value() {
        return value;
    }

    @Override
    public void observe(ValueObserver<BooleanTransform> observer) {
        observable.add(observer);
    }

    @Override
    public synchronized void onValueChanged(
            ObservableValue<BooleanTransform> source,
            BooleanTransform previous,
            BooleanTransform current
    ) {
        boolean related = false;
        if (source == left) {
            leftValue = current;
            related = true;
        }
        if (source == right) {
            rightValue = current;
            related = true;
        }
        if (!related) {
            throw new IllegalArgumentException("change came from an unrelated node");
        }

        BooleanTransform next = compose();
        BooleanTransform old = value;
        value = next;
        observable.publish(this, old, next);
    }

    private BooleanTransform compose() {
        return leftValue.then(rightValue);
    }
}
