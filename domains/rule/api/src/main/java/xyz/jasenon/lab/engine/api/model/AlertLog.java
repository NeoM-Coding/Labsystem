package xyz.jasenon.lab.engine.api.model;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record AlertLog(
        String id,
        String eventId,
        String runtimeId,
        String actionGroupId,
        String deviceConditionGroupId,
        String timeConditionGroupId,
        Instant matchedAt,
        Instant completedAt,
        String status,
        String content,
        List<String> userIds,
        List<ActionResult> actions,
        LocalDateTime createAt
) implements Serializable {

    public record ActionResult(
            int index,
            String type,
            String targetId,
            List<String> userIds,
            Set<String> reportTypes,
            String content,
            String status,
            String message,
            Instant completedAt
    ) implements Serializable {
    }
}
