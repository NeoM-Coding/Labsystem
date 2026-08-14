package xyz.jasenon.lab.engine.eval.v2;

import xyz.jasenon.lab.engine.event.DeviceEventKey;

import java.util.Objects;

/** 同一事件键下由所有谓词共享的唯一字段索引入口。 */
public final class EventSourceNode implements ObservableValue<String> {

    private final DeviceEventKey eventKey;
    private final ObservableSupport<String> observable = new ObservableSupport<>();
    private volatile String value;

    EventSourceNode(DeviceEventKey eventKey) {
        this.eventKey = Objects.requireNonNull(eventKey, "eventKey");
    }

    public DeviceEventKey eventKey() {
        return eventKey;
    }

    @Override
    public String value() {
        return value;
    }

    @Override
    public void observe(ValueObserver<String> observer) {
        observable.add(observer);
    }

    synchronized void accept(String eventValue) {
        String previous = value;
        value = eventValue;
        observable.publish(this, previous, eventValue);
    }

    public int predicateObserverCount() {
        return observable.size();
    }
}
