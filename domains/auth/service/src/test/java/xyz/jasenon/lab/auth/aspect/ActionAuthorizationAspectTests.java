package xyz.jasenon.lab.auth.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.auth.SourceType;
import xyz.jasenon.lab.auth.command.ActionCommand;
import xyz.jasenon.lab.auth.command.GrantCommand;
import xyz.jasenon.lab.auth.command.RevokeCommand;
import xyz.jasenon.lab.auth.command.UserAuthorizationCommand;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.context.UserContextHolder;
import xyz.jasenon.lab.auth.exception.AuthorizationConfigurationException;
import xyz.jasenon.lab.auth.exception.PermissionDeniedException;
import xyz.jasenon.lab.auth.handler.ActionCommandHandler;
import xyz.jasenon.lab.auth.handler.ActionCommandHandlerRegistry;
import xyz.jasenon.lab.auth.permission.Action;
import xyz.jasenon.lab.auth.service.Auth;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionAuthorizationAspectTests {

    private FakeAuth auth;
    private ActionAuthorizationAspect aspect;

    @BeforeEach
    void setUp() {
        auth = new FakeAuth();
        ActionCommandHandler<TestMutation> handler = new ActionCommandHandler<>(TestMutation.class) {
            @Override
            protected ActionCommand toAction(TestMutation source, UserContext context) {
                return new ActionCommand(
                        SourceType.app, "global", Action.App.edit_user,
                        SourceType.user, context.getUserId()
                );
            }
        };
        aspect = new ActionAuthorizationAspect(new ActionCommandHandlerRegistry(List.of(handler)), auth);
        UserContextHolder.set(UserContext.of("operator", "operator", "Operator", List.of()));
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void proceedsWhenEveryHandledArgumentIsAuthorized() throws Throwable {
        auth.allowed = true;
        JoinPointFixture fixture = joinPoint("ignored", new TestMutation("target"));

        assertEquals("ok", aspect.authorize(fixture.joinPoint()));
        assertTrue(fixture.proceeded()[0]);
        assertEquals(Action.App.edit_user, auth.lastCommand.action());
        assertEquals("operator", auth.lastCommand.subjectId());
    }

    @Test
    void rejectsWhenACommandIsDenied() {
        JoinPointFixture fixture = joinPoint(new TestMutation("target"));

        assertThrows(PermissionDeniedException.class, () -> aspect.authorize(fixture.joinPoint()));
        assertFalse(fixture.proceeded()[0]);
    }

    @Test
    void failsClosedWhenNoArgumentHasAnAuthorizationHandler() {
        JoinPointFixture fixture = joinPoint("unhandled");

        assertThrows(AuthorizationConfigurationException.class,
                () -> aspect.authorize(fixture.joinPoint()));
        assertFalse(fixture.proceeded()[0]);
    }

    private static JoinPointFixture joinPoint(Object... arguments) {
        boolean[] proceeded = {false};
        Signature signature = (Signature) Proxy.newProxyInstance(
                Signature.class.getClassLoader(), new Class<?>[]{Signature.class},
                (proxy, method, args) -> method.getName().equals("toShortString")
                        ? "UserService.mutate(..)" : defaultValue(method.getReturnType())
        );
        ProceedingJoinPoint joinPoint = (ProceedingJoinPoint) Proxy.newProxyInstance(
                ProceedingJoinPoint.class.getClassLoader(), new Class<?>[]{ProceedingJoinPoint.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getArgs" -> arguments;
                    case "getSignature" -> signature;
                    case "proceed" -> {
                        proceeded[0] = true;
                        yield "ok";
                    }
                    default -> defaultValue(method.getReturnType());
                }
        );
        return new JoinPointFixture(joinPoint, proceeded);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        return 0;
    }

    private record JoinPointFixture(ProceedingJoinPoint joinPoint, boolean[] proceeded) {
    }

    private record TestMutation(String userId) {
    }

    private static final class FakeAuth implements Auth {

        private boolean allowed;
        private ActionCommand lastCommand;

        @Override
        public void grant(GrantCommand command) {
        }

        @Override
        public void revoke(RevokeCommand command) {
        }

        @Override
        public boolean check(ActionCommand command) {
            lastCommand = command;
            return allowed;
        }

        @Override
        public void synchronize(UserAuthorizationCommand command) {
        }

        @Override
        public void removeUser(String userId) {
        }

    }
}
