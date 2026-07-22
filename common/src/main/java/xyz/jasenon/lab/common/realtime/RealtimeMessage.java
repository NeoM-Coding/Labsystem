package xyz.jasenon.lab.common.realtime;

import java.io.Serializable;
import java.util.List;

public record RealtimeMessage(
        RealtimeAudienceType audienceType,
        List<String> audienceIds,
        RealtimeEvent event
) implements Serializable {

    public RealtimeMessage {
        audienceIds = audienceIds == null ? List.of() : List.copyOf(audienceIds);
    }
}
