package xyz.jasenon.lab.engine.time;

import xyz.jasenon.lab.engine.event.TimeSignal;

import java.time.Instant;
import java.util.Objects;

/**
 * 时间条件计算出的下一次边界。
 */
public record TimeTransition(
        TimeSignal signal,
        Instant scheduledAt
) {

    public TimeTransition {
        Objects.requireNonNull(signal, "signal");
        Objects.requireNonNull(scheduledAt, "scheduledAt");
    }
}
