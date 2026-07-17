package xyz.jasenon.lab.base.context;

import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.base.api.model.Laboratory;
import xyz.jasenon.lab.base.api.model.User;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class UserContextFactory {

    private UserContextFactory() {
    }

    public static UserContext from(User user, Collection<Laboratory> visibleLaboratories) {
        List<UserContext.LaboratoryScope> scopes = toScopes(visibleLaboratories);
        return UserContext.of(
                user == null ? null : user.getId(),
                user == null ? null : user.getUsername(),
                user == null ? null : user.getName(),
                scopes
        );
    }

    public static UserContext from(User user,
                                   Collection<String> laboratoryIds,
                                   Collection<UserContext.LaboratoryScope> laboratoryScopes) {
        return UserContext.of(
                user == null ? null : user.getId(),
                user == null ? null : user.getUsername(),
                user == null ? null : user.getName(),
                laboratoryIds,
                laboratoryScopes
        );
    }

    private static List<UserContext.LaboratoryScope> toScopes(Collection<Laboratory> laboratories) {
        if (laboratories == null || laboratories.isEmpty()) {
            return List.of();
        }
        return laboratories.stream()
                .filter(Objects::nonNull)
                .map(UserContextFactory::toScope)
                .filter(scope -> scope.getLaboratoryId() != null && !scope.getLaboratoryId().isBlank())
                .distinct()
                .toList();
    }

    private static UserContext.LaboratoryScope toScope(Laboratory laboratory) {
        return UserContext.LaboratoryScope.builder()
                .laboratoryId(laboratory.getId())
                .laboratoryName(laboratory.getLaboratoryName())
                .buildingName(laboratory.getBuildingName())
                .orgName(laboratory.getOrgName())
                .build();
    }
}
