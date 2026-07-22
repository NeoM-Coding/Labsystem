package xyz.jasenon.lab.web.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.context.UserContextHolder;
import xyz.jasenon.lab.auth.context.UserContextStore;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserContextRequestFilterTests {

    @AfterEach
    void clearContext() {
        UserContextHolder.clear();
    }

    @Test
    void restoresContextForAuthenticatedRequestAndClearsItAfterwards() throws Exception {
        UserContext context = UserContext.of("user-1", "alice", "Alice", List.of("lab-1"), List.of());
        UserContextRequestFilter filter = new UserContextRequestFilter(
                () -> Optional.of("user-1"), new FakeStore(context)
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, (request, chainResponse) ->
                assertEquals("user-1", UserContextHolder.get().getUserId()));

        assertNull(UserContextHolder.get());
        assertEquals(200, response.getStatus());
    }

    @Test
    void rejectsAuthenticatedRequestWhenCachedContextIsMissing() throws Exception {
        UserContextRequestFilter filter = new UserContextRequestFilter(
                () -> Optional.of("user-1"), new FakeStore(null)
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response,
                (request, chainResponse) -> { throw new AssertionError("filter chain should not run"); });

        assertEquals(401, response.getStatus());
        assertNull(UserContextHolder.get());
    }

    @Test
    void allowsAnonymousRequestWithoutCreatingContext() throws Exception {
        UserContextRequestFilter filter = new UserContextRequestFilter(
                Optional::empty, new FakeStore(null)
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response,
                (request, chainResponse) -> assertNull(UserContextHolder.get()));

        assertEquals(200, response.getStatus());
    }

    @Test
    void skipsContextRestorationForLoginRequest() throws Exception {
        UserContextRequestFilter filter = new UserContextRequestFilter(
                () -> { throw new AssertionError("login must not resolve the previous session"); },
                new FakeStore(null)
        );
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/sessions");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response,
                (chainRequest, chainResponse) -> assertNull(UserContextHolder.get()));

        assertEquals(200, response.getStatus());
    }

    @Test
    void skipsContextRestorationForOpenApiDocumentation() throws Exception {
        UserContextRequestFilter filter = new UserContextRequestFilter(
                () -> { throw new AssertionError("documentation must be publicly accessible"); },
                new FakeStore(null)
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v3/api-docs.yaml");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response,
                (chainRequest, chainResponse) -> assertNull(UserContextHolder.get()));

        assertEquals(200, response.getStatus());
    }

    private record FakeStore(UserContext context) implements UserContextStore {
        @Override
        public void save(UserContext context) {
        }

        @Override
        public Optional<UserContext> find(String userId) {
            return Optional.ofNullable(context);
        }

        @Override
        public void delete(String userId) {
        }
    }
}
