package xyz.jasenon.lab.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.auth.SourceType;
import xyz.jasenon.lab.auth.client.AuthorizationOperations;
import xyz.jasenon.lab.auth.permission.Action;
import xyz.jasenon.lab.auth.permission.Permission;
import xyz.jasenon.lab.auth.permission.RelationShip;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LaboratoryAuthorizationServiceTests {

    private FakeAuthorizationOperations operations;
    private LaboratoryAuthorization service;

    @BeforeEach
    void setUp() {
        operations = new FakeAuthorizationOperations();
        service = new LaboratoryAuthorizationService(operations);
    }

    @Test
    void initializeConnectsGlobalAppBeforeGrantingCreatorOwner() {
        service.initialize("lab-1", "creator");

        assertEquals(List.of(
                "grant:laboratory:lab-1#app@app:global",
                "grant:laboratory:lab-1#owner@user:creator"
        ), operations.mutations);
    }

    @Test
    void readsOwnersAndDirectViewersSeparately() {
        operations.ownerIds = Set.of("owner-1");
        operations.viewerIds = Set.of("viewer-1", "viewer-2");

        assertEquals(new LaboratoryMembers(Set.of("owner-1"), Set.of("viewer-1", "viewer-2")),
                service.members("lab-1"));
    }

    @Test
    void replacesOnlyViewerRelations() {
        operations.viewerIds = Set.of("keep", "remove");

        service.replaceViewers("lab-1", Set.of("keep", "add"));

        assertEquals(List.of(
                "revoke:laboratory:lab-1#viewer@user:remove",
                "grant:laboratory:lab-1#viewer@user:add"
        ), operations.mutations);
    }

    @Test
    void reconciliationAlignsOwnerAndManagersWithMysqlFacts() {
        operations.ownerIds = Set.of("old-owner");
        operations.managerIds = Set.of("keep-manager", "old-manager");

        service.reconcile("lab-1", "new-owner", Set.of("keep-manager", "new-manager"));

        assertEquals(List.of(
                "grant:laboratory:lab-1#app@app:global",
                "revoke:laboratory:lab-1#owner@user:old-owner",
                "grant:laboratory:lab-1#owner@user:new-owner",
                "revoke:laboratory:lab-1#manager@user:old-manager",
                "grant:laboratory:lab-1#manager@user:new-manager"
        ), operations.mutations);
    }

    @Test
    void removeDeletesAllEntityRelationships() {
        service.remove("lab-1");

        assertEquals(List.of("delete:laboratory:lab-1"), operations.mutations);
    }

    @Test
    void visibleLaboratoryIdsUsesCanViewPermissionLookup() {
        operations.visibleLaboratoryIds = Set.of("lab-1", "lab-2");

        assertEquals(Set.of("lab-1", "lab-2"), service.visibleLaboratoryIds("target"));
        assertEquals("laboratory#can_view@user:target", operations.lastLookup);
    }

    @Test
    void usersWhoCanViewUsesSubjectPermissionLookup() {
        operations.visibleUserIds = Set.of("user-1", "user-2");

        assertEquals(Set.of("user-1", "user-2"), service.usersWhoCanView("lab-1"));
        assertEquals("laboratory:lab-1#can_view@user", operations.lastLookup);
    }

    @Test
    void rejectsBlankIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> service.initialize(" ", "creator"));
        assertThrows(IllegalArgumentException.class, () -> service.visibleLaboratoryIds(" "));
        assertThrows(IllegalArgumentException.class, () -> service.usersWhoCanView(" "));
    }

    private static final class FakeAuthorizationOperations implements AuthorizationOperations {

        private final List<String> mutations = new ArrayList<>();
        private Set<String> visibleLaboratoryIds = Set.of();
        private Set<String> visibleUserIds = Set.of();
        private Set<String> ownerIds = Set.of();
        private Set<String> viewerIds = Set.of();
        private Set<String> managerIds = Set.of();
        private String lastLookup;

        @Override
        public boolean grant(SourceType source, String sourceId, RelationShip relation,
                             SourceType target, String targetId) {
            mutations.add("grant:" + source + ":" + sourceId + "#" + relation.str()
                    + "@" + target + ":" + targetId);
            return true;
        }

        @Override
        public boolean revoke(SourceType source, String sourceId, RelationShip relation,
                              SourceType target, String targetId) {
            mutations.add("revoke:" + source + ":" + sourceId + "#" + relation.str()
                    + "@" + target + ":" + targetId);
            return true;
        }

        @Override
        public Set<String> subjectIdsOf(SourceType source, String sourceId,
                                        RelationShip relation, SourceType target) {
            if (relation == RelationShip.Laboratory.owner) return ownerIds;
            if (relation == RelationShip.Laboratory.manager) return managerIds;
            return viewerIds;
        }

        @Override
        public boolean deleteEntityData(SourceType source, String sourceId) {
            mutations.add("delete:" + source + ":" + sourceId);
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
            return Set.of();
        }

        @Override
        public Set<String> entityIdsOf(SourceType source, RelationShip relation,
                                       SourceType target, String targetId) {
            return Set.of();
        }

        @Override
        public Set<String> lookupEntityIds(SourceType entityType, Permission permission,
                                           SourceType subjectType, String subjectId) {
            lastLookup = entityType + "#" + permission.str() + "@" + subjectType + ":" + subjectId;
            return visibleLaboratoryIds;
        }

        @Override
        public Set<String> lookupSubjectIds(SourceType entityType, String entityId,
                                            Permission permission, SourceType subjectType) {
            lastLookup = entityType + ":" + entityId + "#" + permission.str()
                    + "@" + subjectType;
            return visibleUserIds;
        }
    }
}
