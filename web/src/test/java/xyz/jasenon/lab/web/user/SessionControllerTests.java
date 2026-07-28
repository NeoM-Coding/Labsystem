package xyz.jasenon.lab.web.user;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.jasenon.lab.base.api.dto.UserSession;
import xyz.jasenon.lab.base.api.model.User;
import xyz.jasenon.lab.base.api.service.UserService;
import xyz.jasenon.lab.common.rpc.RpcResult;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionControllerTests {

    @Test
    void loginWritesAnHttpOnlySameSiteCookieAvailableToTheWebSocketPath() {
        UserService userService = mock(UserService.class);
        User user = new User();
        user.setId("user-1");
        when(userService.authenticate("tester", "secret")).thenReturn(RpcResult.success(user));
        SaTokenSessionManager sessionManager = mock(SaTokenSessionManager.class);
        when(sessionManager.login(user))
                .thenReturn(new UserSession(user, "satoken", "token-value"));
        SessionController controller = new SessionController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "sessionManager", sessionManager);
        ReflectionTestUtils.setField(controller, "secureCookie", false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.login(new UserLoginRequest("tester", "secret"), response);

        verify(userService).authenticate("tester", "secret");
        String cookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(cookie);
        assertTrue(cookie.startsWith("satoken=token-value"));
        assertTrue(cookie.contains("Path=/"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Lax"));
        assertFalse(cookie.contains("Secure"));
    }

    @Test
    void currentReturnsTheUserAndTokenForTheRequestCookie() {
        UserService userService = mock(UserService.class);
        User user = new User();
        user.setId("user-1");
        UserSession session = new UserSession(user, "satoken", "token-value");
        when(userService.current()).thenReturn(RpcResult.success(user));
        SaTokenSessionManager sessionManager = mock(SaTokenSessionManager.class);
        when(sessionManager.current(user)).thenReturn(session);
        SessionController controller = new SessionController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "sessionManager", sessionManager);

        controller.current();

        verify(userService).current();
        verify(sessionManager).current(user);
    }

    @Test
    void logoutClearsTheDownstreamContextAndCurrentSaTokenSession() {
        UserService userService = mock(UserService.class);
        when(userService.logout()).thenReturn(RpcResult.success());
        SaTokenSessionManager sessionManager = mock(SaTokenSessionManager.class);
        SessionController controller = new SessionController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "sessionManager", sessionManager);
        ReflectionTestUtils.setField(controller, "secureCookie", false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.logout(response);

        verify(userService).logout();
        verify(sessionManager).logout();
    }
}
