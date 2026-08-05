package xyz.jasenon.lab.auth.command;

import xyz.jasenon.lab.auth.SourceType;
import xyz.jasenon.lab.auth.permission.Action;

import java.util.Objects;

public record ActionCommand(
        SourceType entityType,
        String entityId,
        Action action,
        SourceType subjectType,
        String subjectId
) {

    public ActionCommand {
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(subjectType, "subjectType");
        entityId = requireText(entityId, "entityId");
        subjectId = requireText(subjectId, "subjectId");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value.trim();
    }

}
