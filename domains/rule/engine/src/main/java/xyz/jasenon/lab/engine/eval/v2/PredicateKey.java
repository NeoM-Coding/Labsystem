package xyz.jasenon.lab.engine.eval.v2;

import xyz.jasenon.lab.engine.eval.Operator;
import xyz.jasenon.lab.engine.event.DeviceEventKey;

/** Forest 内完全相同谓词的共享身份。 */
record PredicateKey(
        DeviceEventKey eventKey,
        Operator operator,
        String targetValue
) {
}
