package xyz.jasenon.lab.engine.runtime;

import xyz.jasenon.lab.engine.action.Action;

import java.time.Instant;

/**
 * ActionExecutionTracker 保留的精简失败快照。
 */
public record ActionFailure(
        String runtimeId,
        String actionGroupId,
        Action.ActionType actionType,
        String targetId,
        String errorType,
        String message,
        Instant occurredAt
) {
}
