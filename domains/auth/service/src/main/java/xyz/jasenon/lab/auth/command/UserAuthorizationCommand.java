package xyz.jasenon.lab.auth.command;

import xyz.jasenon.lab.auth.permission.RelationShip;

import java.util.Set;
import java.util.stream.Collectors;

public record UserAuthorizationCommand(
        String userId,
        Set<RelationShip.App> appRelations,
        Set<String> laboratoryIds
) {

    public UserAuthorizationCommand {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        userId = userId.trim();
        appRelations = appRelations == null ? Set.of() : Set.copyOf(appRelations);
        laboratoryIds = laboratoryIds == null
                ? Set.of()
                : laboratoryIds.stream()
                        .filter(id -> id != null && !id.isBlank())
                        .map(String::trim)
                        .collect(Collectors.toUnmodifiableSet());
    }
}
