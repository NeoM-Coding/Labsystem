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
                    SourceType.laboratory, normalizedLaboratoryId, RelationShip.Laboratory.owner,
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

    @Override
    public LaboratoryMembers members(String laboratoryId) {
        String id = requireText(laboratoryId, "laboratoryId");
        return new LaboratoryMembers(
                operations.subjectIdsOf(SourceType.laboratory, id,
                        RelationShip.Laboratory.owner, SourceType.user),
                operations.subjectIdsOf(SourceType.laboratory, id,
                        RelationShip.Laboratory.viewer, SourceType.user)
        );
    }

    @Override
    public void replaceViewers(String laboratoryId, Set<String> viewerUserIds) {
        String id = requireText(laboratoryId, "laboratoryId");
        Set<String> requestedInput = viewerUserIds == null ? Set.of() : viewerUserIds.stream()
                .map(userId -> requireText(userId, "viewerUserId"))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        LaboratoryMembers members = members(id);
        java.util.HashSet<String> requested = new java.util.HashSet<>(requestedInput);
        // owner 已天然具备 can_view，避免再写一条冗余 viewer 关系。
        requested.removeAll(members.ownerIds());
        Set<String> current = members.viewerIds();
        current.stream().filter(userId -> !requested.contains(userId)).sorted().forEach(userId ->
                operations.revoke(SourceType.laboratory, id, RelationShip.Laboratory.viewer,
                        SourceType.user, userId));
        requested.stream().filter(userId -> !current.contains(userId)).sorted().forEach(userId ->
                operations.grant(SourceType.laboratory, id, RelationShip.Laboratory.viewer,
                        SourceType.user, userId));
    }

    @Override
    public void reconcile(String laboratoryId, String ownerUserId, Set<String> managerUserIds) {
        String id = requireText(laboratoryId, "laboratoryId");
        String ownerId = requireText(ownerUserId, "ownerUserId");
        operations.grant(SourceType.laboratory, id, RelationShip.Laboratory.app,
                SourceType.app, GLOBAL_APP_ID);
        replaceRelation(id, RelationShip.Laboratory.owner, Set.of(ownerId));
        replaceRelation(id, RelationShip.Laboratory.manager,
                managerUserIds == null ? Set.of() : managerUserIds);
    }

    private void replaceRelation(String laboratoryId, RelationShip.Laboratory relation,
                                 Set<String> requestedIds) {
        Set<String> requested = requestedIds.stream()
                .map(id -> requireText(id, relation.str() + "UserId"))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> current = operations.subjectIdsOf(
                SourceType.laboratory, laboratoryId, relation, SourceType.user);
        current.stream().filter(id -> !requested.contains(id)).sorted().forEach(id ->
                operations.revoke(SourceType.laboratory, laboratoryId, relation, SourceType.user, id));
        requested.stream().filter(id -> !current.contains(id)).sorted().forEach(id ->
                operations.grant(SourceType.laboratory, laboratoryId, relation, SourceType.user, id));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value.trim();
    }
}
