package xyz.jasenon.lab.engine.eval.v2;

import xyz.jasenon.lab.engine.event.DeviceEventKey;

import java.util.Map;
import java.util.Objects;

/** 一次设备观测中受影响的根，以及其中真正发生布尔跳变的根。 */
public record EvalUpdate(
        DeviceEventKey eventKey,
        Map<EvalRootKey, Boolean> affectedResults,
        Map<EvalRootKey, Boolean> changedResults
) {

    public EvalUpdate {
        Objects.requireNonNull(eventKey, "eventKey");
        affectedResults = Map.copyOf(Objects.requireNonNull(affectedResults, "affectedResults"));
        changedResults = Map.copyOf(Objects.requireNonNull(changedResults, "changedResults"));
    }

    public EvalUpdate(DeviceEventKey eventKey, Map<EvalRootKey, Boolean> changedResults) {
        this(eventKey, changedResults, changedResults);
    }

    public boolean changed() {
        return !changedResults.isEmpty();
    }

    public boolean affected() {
        return !affectedResults.isEmpty();
    }
}
