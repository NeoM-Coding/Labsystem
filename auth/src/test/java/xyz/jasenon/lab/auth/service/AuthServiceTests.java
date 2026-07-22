package xyz.jasenon.lab.auth.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.auth.SourceType;
import xyz.jasenon.lab.auth.client.AuthorizationOperations;
import xyz.jasenon.lab.auth.command.GrantCommand;
import xyz.jasenon.lab.auth.command.RevokeCommand;
import xyz.jasenon.lab.auth.command.UserAuthorizationCommand;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.context.UserContextHolder;
import xyz.jasenon.lab.auth.exception.PermissionDeniedException;
import xyz.jasenon.lab.auth.permission.Permission;
import xyz.jasenon.lab.auth.permission.RelationShip;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTests {

    private FakeAuthorizationOperations operations;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        operations = new FakeAuthorizationOperations();
        auth = new AuthService(operations);
        UserContextHolder.set(UserContext.of(
                "operator", "operator", "Operator", List.of("lab-1"), List.of()
        ));
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void userCanGrantAnAppRelationTheyOwn() {
        operations.ownedRelations.add(RelationShip.App.user_manager.str());

        auth.grant(appGrant(RelationShip.App.user_manager));

        assertEquals("grant:app:global#user_manager@user:target", operations.lastMutation);
    }

    @Test
    void userCannotGrantAnAppRelationTheyDoNotOwn() {
        operations.ownedRelations.add(RelationShip.App.user_viewer.str());

        assertThrows(PermissionDeniedException.class,
                () -> auth.grant(appGrant(RelationShip.App.user_manager)));
    }

    @Test
    void superAdminCanGrantAnyNonProtectedAppRelation() {
        operations.ownedRelations.add(RelationShip.App.super_admin.str());

        auth.grant(appGrant(RelationShip.App.laboratory_manager));

        assertEquals("grant:app:global#laboratory_manager@user:target", operations.lastMutation);
    }

    @Test
    void superAdminRelationCannotBeGrantedOrRevoked() {
        operations.ownedRelations.add(RelationShip.App.super_admin.str());

        assertThrows(PermissionDeniedException.class,
                () -> auth.grant(appGrant(RelationShip.App.super_admin)));
        assertThrows(PermissionDeniedException.class,
                () -> auth.revoke(new RevokeCommand(
                        SourceType.app, AuthService.GLOBAL_APP_ID, RelationShip.App.super_admin,
                        SourceType.user, "target"
                )));
    }

    @Test
    void laboratoryViewerCanOnlyBeGrantedInsideUserContextScope() {
        auth.grant(new GrantCommand(
                SourceType.laboratory, "lab-1", RelationShip.Laboratory.viewer,
                SourceType.user, "target"
        ));

        assertEquals("grant:laboratory:lab-1#viewer@user:target", operations.lastMutation);
        assertThrows(PermissionDeniedException.class, () -> auth.grant(new GrantCommand(
                SourceType.laboratory, "lab-2", RelationShip.Laboratory.viewer,
                SourceType.user, "target"
        )));
    }

    @Test
    void synchronizeReplacesAppRelationsAndLaboratoryViewers() {
        operations.ownedRelations.add(RelationShip.App.super_admin.str());
        operations.targetRelations.add(RelationShip.App.user_viewer.str());
        operations.targetLaboratoryIds.add("lab-1");

        auth.synchronize(new UserAuthorizationCommand(
                "target",
                Set.of(RelationShip.App.user_manager),
                Set.of()
        ));

        assertTrue(operations.mutations.contains("revoke:app:global#user_viewer@user:target"));
        assertTrue(operations.mutations.contains("grant:app:global#user_manager@user:target"));
        assertTrue(operations.mutations.contains("revoke:laboratory:lab-1#viewer@user:target"));
    }

    @Test
    void synchronizeValidatesEveryChangeBeforeWritingAnything() {
        operations.ownedRelations.add(RelationShip.App.user_manager.str());

        assertThrows(PermissionDeniedException.class, () -> auth.synchronize(
                new UserAuthorizationCommand(
                        "target", Set.of(RelationShip.App.user_manager), Set.of("lab-2")
                )
        ));
        assertTrue(operations.mutations.isEmpty());
    }

    private static GrantCommand appGrant(RelationShip.App relation) {
        return new GrantCommand(
                SourceType.app, AuthService.GLOBAL_APP_ID, relation,
                SourceType.user, "target"
        );
    }

    private static final class FakeAuthorizationOperations implements AuthorizationOperations {

        private final Set<String> ownedRelations = new HashSet<>();
        private final Set<String> targetRelations = new HashSet<>();
        private final Set<String> targetLaboratoryIds = new HashSet<>();
        private final Set<String> mutations = new HashSet<>();
        private String lastMutation;

        @Override
        public boolean grant(SourceType source, String sourceId, RelationShip relation,
                             SourceType target, String targetId) {
            lastMutation = "grant:" + source + ":" + sourceId + "#" + relation.str()
                    + "@" + target + ":" + targetId;
            mutations.add(lastMutation);
            return true;
        }

        @Override
        public boolean revoke(SourceType source, String sourceId, RelationShip relation,
                              SourceType target, String targetId) {
            lastMutation = "revoke:" + source + ":" + sourceId + "#" + relation.str()
                    + "@" + target + ":" + targetId;
            mutations.add(lastMutation);
            return true;
        }

        @Override
        public boolean deleteEntityData(SourceType source, String sourceId) {
            lastMutation = "delete:" + source + ":" + sourceId;
            mutations.add(lastMutation);
            return true;
        }

        @Override
        public boolean check(SourceType source, String sourceId, Permission permission,
                             SourceType target, String targetId) {
            return true;
        }

        @Override
        public Set<String> relationsOf(SourceType source, String sourceId,
                                       SourceType target, String targetId) {
            return "operator".equals(targetId)
                    ? Set.copyOf(ownedRelations)
                    : Set.copyOf(targetRelations);
        }

        @Override
        public Set<String> entityIdsOf(SourceType source, RelationShip relation,
                                       SourceType target, String targetId) {
            return Set.copyOf(targetLaboratoryIds);
        }

        @Override
        public Set<String> lookupEntityIds(SourceType entityType, Permission permission,
                                           SourceType subjectType, String subjectId) {
            return Set.of();
        }

        @Override
        public Set<String> lookupSubjectIds(SourceType entityType, String entityId,
                                            Permission permission, SourceType subjectType) {
            return Set.of();
        }
    }
}
