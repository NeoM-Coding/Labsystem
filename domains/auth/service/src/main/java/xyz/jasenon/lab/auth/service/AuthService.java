package xyz.jasenon.lab.auth.service;

import xyz.jasenon.lab.auth.SourceType;
import xyz.jasenon.lab.auth.client.AuthorizationOperations;
import xyz.jasenon.lab.auth.command.ActionCommand;
import xyz.jasenon.lab.auth.command.GrantCommand;
import xyz.jasenon.lab.auth.command.RevokeCommand;
import xyz.jasenon.lab.auth.command.UserAuthorizationCommand;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.context.UserContextHolder;
import xyz.jasenon.lab.auth.exception.AuthenticationRequiredException;
import xyz.jasenon.lab.auth.exception.AuthorizationConfigurationException;
import xyz.jasenon.lab.auth.exception.PermissionDeniedException;
import xyz.jasenon.lab.auth.permission.RelationShip;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public class AuthService implements Auth {

    public static final String GLOBAL_APP_ID = "global";

    private static final Set<RelationShip.App> PROTECTED_APP_RELATIONS =
            Set.of(RelationShip.App.super_admin);

    private final AuthorizationOperations operations;

    public AuthService(AuthorizationOperations operations) {
        this.operations = operations;
    }

    @Override
    public void grant(GrantCommand command) {
        UserContext operator = requireUserContext();
        validateGrantScope(command.entityType(), command.entityId(), command.relation(), operator);
        operations.grant(
                command.entityType(), command.entityId(), command.relation(),
                command.subjectType(), command.subjectId()
        );
    }

    @Override
    public void revoke(RevokeCommand command) {
        UserContext operator = requireUserContext();
        validateGrantScope(command.entityType(), command.entityId(), command.relation(), operator);
        operations.revoke(
                command.entityType(), command.entityId(), command.relation(),
                command.subjectType(), command.subjectId()
        );
    }

    @Override
    public boolean check(ActionCommand command) {
        return operations.check(
                command.entityType(), command.entityId(), command.action(),
                command.subjectType(), command.subjectId()
        );
    }

    @Override
    public void synchronize(UserAuthorizationCommand command) {
        UserContext operator = requireUserContext();
        Set<RelationShip.App> desiredAppRelations = command.appRelations();
        Set<RelationShip.App> currentAppRelations = knownMutableAppRelations(
                operations.relationsOf(SourceType.app, GLOBAL_APP_ID, SourceType.user, command.userId())
        );
        Set<String> currentLaboratoryIds = operations.entityIdsOf(
                SourceType.laboratory, RelationShip.Laboratory.viewer,
                SourceType.user, command.userId()
        );

        Set<RelationShip.App> appToRevoke = difference(currentAppRelations, desiredAppRelations);
        Set<RelationShip.App> appToGrant = difference(desiredAppRelations, currentAppRelations);
        Set<String> laboratoriesToRevoke = difference(currentLaboratoryIds, command.laboratoryIds());
        Set<String> laboratoriesToGrant = difference(command.laboratoryIds(), currentLaboratoryIds);

        // 先验证完整变更集，避免越权项出现在中途时留下部分授权写入。
        appToRevoke.forEach(relation -> validateAppRelation(GLOBAL_APP_ID, relation, operator.getUserId()));
        appToGrant.forEach(relation -> validateAppRelation(GLOBAL_APP_ID, relation, operator.getUserId()));
        laboratoriesToRevoke.forEach(id -> validateLaboratoryRelation(id, RelationShip.Laboratory.viewer, operator));
        laboratoriesToGrant.forEach(id -> validateLaboratoryRelation(id, RelationShip.Laboratory.viewer, operator));

        appToRevoke.forEach(relation -> operations.revoke(
                SourceType.app, GLOBAL_APP_ID, relation, SourceType.user, command.userId()
        ));
        laboratoriesToRevoke.forEach(id -> operations.revoke(
                SourceType.laboratory, id, RelationShip.Laboratory.viewer, SourceType.user, command.userId()
        ));
        appToGrant.forEach(relation -> operations.grant(
                SourceType.app, GLOBAL_APP_ID, relation, SourceType.user, command.userId()
        ));
        laboratoriesToGrant.forEach(id -> operations.grant(
                SourceType.laboratory, id, RelationShip.Laboratory.viewer, SourceType.user, command.userId()
        ));
    }

    @Override
    public void removeUser(String userId) {
        String normalizedUserId = requireText(userId, "userId");
        Set<String> currentRelations = operations.relationsOf(
                SourceType.app, GLOBAL_APP_ID, SourceType.user, normalizedUserId
        );
        for (RelationShip.App relation : RelationShip.App.values()) {
            if (currentRelations.contains(relation.str())) {
                operations.revoke(
                        SourceType.app, GLOBAL_APP_ID, relation,
                        SourceType.user, normalizedUserId
                );
            }
        }
        Set<String> laboratoryIds = operations.entityIdsOf(
                SourceType.laboratory, RelationShip.Laboratory.viewer,
                SourceType.user, normalizedUserId
        );
        laboratoryIds.forEach(laboratoryId -> operations.revoke(
                SourceType.laboratory, laboratoryId, RelationShip.Laboratory.viewer,
                SourceType.user, normalizedUserId
        ));
    }

    private static Set<RelationShip.App> knownMutableAppRelations(Set<String> relations) {
        EnumSet<RelationShip.App> result = EnumSet.noneOf(RelationShip.App.class);
        for (RelationShip.App relation : RelationShip.App.values()) {
            if (!PROTECTED_APP_RELATIONS.contains(relation) && relations.contains(relation.str())) {
                result.add(relation);
            }
        }
        return Set.copyOf(result);
    }

    private static <T> Set<T> difference(Set<T> left, Set<T> right) {
        HashSet<T> result = new HashSet<>(left);
        result.removeAll(right);
        return Set.copyOf(result);
    }

    private void validateGrantScope(SourceType entityType,
                                    String entityId,
                                    RelationShip relation,
                                    UserContext operator) {
        if (entityType == SourceType.app) {
            validateAppRelation(entityId, relation, operator.getUserId());
            return;
        }
        if (entityType == SourceType.laboratory) {
            validateLaboratoryRelation(entityId, relation, operator);
            return;
        }
        throw new AuthorizationConfigurationException("不支持向 " + entityType + " 资源写入 Relation");
    }

    private void validateAppRelation(String entityId, RelationShip relation, String operatorId) {
        if (!GLOBAL_APP_ID.equals(entityId)) {
            throw new AuthorizationConfigurationException("App Relation 只能写入 app:" + GLOBAL_APP_ID);
        }
        if (!(relation instanceof RelationShip.App appRelation)) {
            throw new AuthorizationConfigurationException("app 资源只能写入 RelationShip.App");
        }
        if (PROTECTED_APP_RELATIONS.contains(appRelation)) {
            throw denied(SourceType.app, GLOBAL_APP_ID, "grant:" + appRelation.str());
        }

        Set<String> ownedRelations = operations.relationsOf(
                SourceType.app, GLOBAL_APP_ID, SourceType.user, operatorId
        );
        boolean superAdmin = ownedRelations.contains(RelationShip.App.super_admin.str());
        if (!superAdmin && !ownedRelations.contains(appRelation.str())) {
            throw denied(SourceType.app, GLOBAL_APP_ID, "grant:" + appRelation.str());
        }
    }

    private static void validateLaboratoryRelation(String laboratoryId,
                                                   RelationShip relation,
                                                   UserContext operator) {
        if (relation != RelationShip.Laboratory.viewer) {
            throw new AuthorizationConfigurationException("laboratory 资源只能写入 viewer Relation");
        }
        if (!operator.canViewLaboratory(laboratoryId)) {
            throw denied(SourceType.laboratory, laboratoryId, "grant:viewer");
        }
    }

    private static UserContext requireUserContext() {
        UserContext context = UserContextHolder.get();
        if (context == null || context.getUserId() == null || context.getUserId().isBlank()) {
            throw new AuthenticationRequiredException("当前请求缺少有效的 UserContext");
        }
        return context;
    }

    private static PermissionDeniedException denied(SourceType type, String id, String permission) {
        return new PermissionDeniedException(type, id, permission);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value.trim();
    }
}
