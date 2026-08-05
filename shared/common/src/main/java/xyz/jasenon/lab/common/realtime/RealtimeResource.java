package xyz.jasenon.lab.common.realtime;

import java.io.Serializable;

public record RealtimeResource(
        String type,
        String id,
        String laboratoryId
) implements Serializable {
}
