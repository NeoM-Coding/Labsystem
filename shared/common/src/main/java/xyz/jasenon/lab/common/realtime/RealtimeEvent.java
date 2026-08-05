package xyz.jasenon.lab.common.realtime;

import java.io.Serializable;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record RealtimeEvent(
        String version,
        String eventId,
        String eventType,
        Instant occurredAt,
        String source,
        String traceId,
        RealtimeResource resource,
        Map<String, Object> data
) implements Serializable {

    public static final String CURRENT_VERSION = "1.0";

    public RealtimeEvent {
        data = data == null ? Map.of() : new LinkedHashMap<>(data);
    }
}
