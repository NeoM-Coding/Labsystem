package xyz.jasenon.lab.auth.command;

import xyz.jasenon.lab.auth.SourceType;
import xyz.jasenon.lab.auth.permission.RelationShip;

import java.util.Objects;

public record RevokeCommand(
        SourceType entityType,
        String entityId,
        RelationShip relation,
        SourceType subjectType,
        String subjectId
) {

    public RevokeCommand {
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(relation, "relation");
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
