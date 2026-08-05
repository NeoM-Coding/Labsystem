package xyz.jasenon.lab.auth.service;

import xyz.jasenon.lab.auth.SourceType;
import xyz.jasenon.lab.auth.client.AuthorizationOperations;
import xyz.jasenon.lab.auth.permission.Action;
import xyz.jasenon.lab.auth.permission.RelationShip;

import java.util.Set;

public class LaboratoryAuthorizationService implements LaboratoryAuthorization {

    private static final String GLOBAL_APP_ID = "global";

    private final AuthorizationOperations operations;

    public LaboratoryAuthorizationService(AuthorizationOperations operations) {
        this.operations = operations;
    }

    @Override
    public void initialize(String laboratoryId, String creatorUserId) {
        String normalizedLaboratoryId = requireText(laboratoryId, "laboratoryId");
        String normalizedCreatorUserId = requireText(creatorUserId, "creatorUserId");

        // 接通 app:global 后，DSL 才能将 app.super_admin 展开为 laboratory.can_view。
        operations.grant(
                SourceType.laboratory, normalizedLaboratoryId, RelationShip.Laboratory.app,
                SourceType.app, GLOBAL_APP_ID
        );
        try {
            operations.grant(
                    SourceType.laboratory, normalizedLaboratoryId, RelationShip.Laboratory.viewer,
                    SourceType.user, normalizedCreatorUserId
            );
        } catch (RuntimeException e) {
            try {
                operations.deleteEntityData(SourceType.laboratory, normalizedLaboratoryId);
            } catch (RuntimeException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
    }

    @Override
    public void remove(String laboratoryId) {
        operations.deleteEntityData(
                SourceType.laboratory,
                requireText(laboratoryId, "laboratoryId")
        );
    }

    @Override
    public Set<String> visibleLaboratoryIds(String userId) {
        return operations.lookupEntityIds(
                SourceType.laboratory,
                Action.Laboratory.can_view,
                SourceType.user,
                requireText(userId, "userId")
        );
    }

    @Override
    public Set<String> usersWhoCanView(String laboratoryId) {
        return operations.lookupSubjectIds(
                SourceType.laboratory,
                requireText(laboratoryId, "laboratoryId"),
                Action.Laboratory.can_view,
                SourceType.user
        );
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value.trim();
    }
}
