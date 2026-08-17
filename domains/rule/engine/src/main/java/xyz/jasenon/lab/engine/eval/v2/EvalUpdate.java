package xyz.jasenon.lab.engine.eval.v2;

import xyz.jasenon.lab.engine.event.DeviceEventKey;

import java.util.Map;
import java.util.Objects;

/** 一次事件传播中归并得到的根节点变化。 */
public record EvalUpdate(DeviceEventKey eventKey, Map<EvalRootKey, Boolean> changedResults) {

    public EvalUpdate {
        Objects.requireNonNull(eventKey, "eventKey");
        changedResults = Map.copyOf(Objects.requireNonNull(changedResults, "changedResults"));
    }

    public boolean changed() {
        return !changedResults.isEmpty();
    }
}
