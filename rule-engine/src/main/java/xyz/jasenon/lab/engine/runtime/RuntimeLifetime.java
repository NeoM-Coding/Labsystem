package xyz.jasenon.lab.engine.runtime;

import java.time.Instant;

/**
 * Runtime 有效期，采用开始时间包含、结束时间不包含的区间语义。
 */
public record RuntimeLifetime(
        Instant activeFrom,
        Instant activeUntil
) {

    public RuntimeLifetime {
        if (activeFrom != null && activeUntil != null && !activeFrom.isBefore(activeUntil)) {
            throw new IllegalArgumentException("activeFrom must be before activeUntil");
        }
    }

    public static RuntimeLifetime always() {
        return new RuntimeLifetime(null, null);
    }

    public boolean isPendingAt(Instant instant) {
        return activeFrom != null && instant.isBefore(activeFrom);
    }

    public boolean isExpiredAt(Instant instant) {
        return activeUntil != null && !instant.isBefore(activeUntil);
    }

    public boolean contains(Instant instant) {
        return !isPendingAt(instant) && !isExpiredAt(instant);
    }
}
