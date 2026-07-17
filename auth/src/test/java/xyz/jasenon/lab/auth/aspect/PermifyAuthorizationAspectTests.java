package xyz.jasenon.lab.auth.aspect;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import xyz.jasenon.lab.auth.SourceType;
import xyz.jasenon.lab.auth.annotation.Mode;
import xyz.jasenon.lab.auth.annotation.PostAuth;
import xyz.jasenon.lab.auth.annotation.PreAuth;
import xyz.jasenon.lab.auth.client.AuthorizationOperations;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.context.UserContextHolder;
import xyz.jasenon.lab.auth.exception.AuthenticationRequiredException;
import xyz.jasenon.lab.auth.exception.PermissionDeniedException;
import xyz.jasenon.lab.auth.permission.Permission;
import xyz.jasenon.lab.auth.permission.RelationShip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PermifyAuthorizationAspectTests {

    private FakeAuthorizationOperations authorizationOperations;
    private SecuredService target;
    private SecuredService proxy;

    @BeforeEach
    void setUp() {
        authorizationOperations = new FakeAuthorizationOperations();
        target = new SecuredService();
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new PermifyAuthorizationAspect(authorizationOperations));
        proxy = factory.getProxy();
        UserContextHolder.set(UserContext.builder().userId("user-7").build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void constantEntityIdUsesCurrentUserAsSubject() {
        assertEquals("ok", proxy.constant());

        CheckCall call = authorizationOperations.lastCheck;
        assertEquals(SourceType.laboratory, call.source());
        assertEquals("lab-fixed", call.sourceId());
        assertEquals("view", call.permission());
        assertEquals(SourceType.user, call.target());
        assertEquals("user-7", call.targetId());
    }

    @Test
    void spelReadsNamedMethodArgumentProperties() {
        assertEquals("lab-16", proxy.byCommand(new Command("lab-16")));
        assertEquals("lab-16", authorizationOperations.lastCheck.sourceId());
    }

    @Test
    void spelSupportsPositionalArguments() {
        assertEquals("lab-p0", proxy.byPosition("lab-p0"));
        assertEquals("lab-p0", authorizationOperations.lastCheck.sourceId());
    }

    @Test
    void deniedPreAuthorizationDoesNotInvokeMethod() {
        authorizationOperations.allowed = false;

        assertThrows(PermissionDeniedException.class, proxy::constant);
        assertEquals(0, target.invocations);
    }

    @Test
    void postAuthorizationRunsAfterSuccessfulMethodExecution() {
        authorizationOperations.allowed = false;

        assertThrows(PermissionDeniedException.class, () -> proxy.post("lab-post"));
        assertEquals(1, target.invocations);
    }

    @Test
    void missingUserContextFailsBeforeCallingPermify() {
        UserContextHolder.clear();

        assertThrows(AuthenticationRequiredException.class, proxy::constant);
        assertEquals(0, authorizationOperations.checkCount);
    }

    record Command(String laboratoryId) {
    }

    record CheckCall(
            SourceType source,
            String sourceId,
            String permission,
            SourceType target,
            String targetId
    ) {
    }

    static class FakeAuthorizationOperations implements AuthorizationOperations {

        private boolean allowed = true;
        private int checkCount;
        private CheckCall lastCheck;

        @Override
        public boolean grant(SourceType source, String sourceId, RelationShip relationShip,
                             SourceType target, String targetId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean revoke(SourceType source, String sourceId, RelationShip relationShip,
                              SourceType target, String targetId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean check(SourceType source, String sourceId, Permission permission,
                             SourceType target, String targetId) {
            checkCount++;
            lastCheck = new CheckCall(source, sourceId, permission.str(), target, targetId);
            return allowed;
        }
    }

    static class SecuredService {

        private int invocations;

        @PreAuth(
                entityType = SourceType.laboratory,
                entityId = "lab-fixed",
                permission = "view"
        )
        public String constant() {
            invocations++;
            return "ok";
        }

        @PreAuth(
                entityType = SourceType.laboratory,
                entityId = "#command.laboratoryId",
                idMode = Mode.Sqel,
                permission = "update"
        )
        public String byCommand(Command command) {
            return command.laboratoryId();
        }

        @PreAuth(
                entityType = SourceType.laboratory,
                entityId = "#p0",
                idMode = Mode.Sqel,
                permission = "delete"
        )
        public String byPosition(String laboratoryId) {
            return laboratoryId;
        }

        @PostAuth(
                entityType = SourceType.laboratory,
                entityId = "#a0",
                idMode = Mode.Sqel,
                permission = "view"
        )
        public String post(String laboratoryId) {
            invocations++;
            return laboratoryId;
        }
    }
}
