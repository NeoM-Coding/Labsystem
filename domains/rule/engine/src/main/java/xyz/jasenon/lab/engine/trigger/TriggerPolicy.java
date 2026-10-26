package xyz.jasenon.lab.engine.trigger;

import xyz.jasenon.lab.engine.definition.RuntimeRevision.TriggerMode;

/**
 * Called only after the entire event batch has been applied.
 */
public final class TriggerPolicy {
    private TriggerPolicy() {
    }

    public static boolean matches(TriggerMode mode, boolean before, boolean after, boolean activation) {
        return after && (activation || mode == TriggerMode.RECONCILE || !before);
    }
}
