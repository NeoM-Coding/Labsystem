package xyz.jasenon.lab.engine.runtime;

import xyz.jasenon.lab.engine.action.Action;

import java.time.Instant;

/**
 * Structured terminal result for one Action invocation.
 */
public record ActionExecutionResult(
        Status status,
        String runtimeId,
        String actionGroupId,
        Action.ActionType actionType,
        String message,
        Instant completedAt
) {

    public enum Status {
        SUCCESS,
        FAILED,
        NOT_IMPLEMENTED
    }

    public static ActionExecutionResult success(
            String runtimeId,
            String actionGroupId,
            Action.ActionType actionType,
            String message
    ) {
        return new ActionExecutionResult(
                Status.SUCCESS,
                runtimeId,
                actionGroupId,
                actionType,
                message,
                Instant.now()
        );
    }

    public static ActionExecutionResult failed(
            String runtimeId,
            String actionGroupId,
            Action.ActionType actionType,
            String message
    ) {
        return new ActionExecutionResult(
                Status.FAILED,
                runtimeId,
                actionGroupId,
                actionType,
                message,
                Instant.now()
        );
    }

    public static ActionExecutionResult notImplemented(
            String runtimeId,
            String actionGroupId,
            Action.ActionType actionType,
            String message
    ) {
        return new ActionExecutionResult(
                Status.NOT_IMPLEMENTED,
                runtimeId,
                actionGroupId,
                actionType,
                message,
                Instant.now()
        );
    }
}
