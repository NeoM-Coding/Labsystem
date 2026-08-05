package xyz.jasenon.lab.observability.dubbo;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.context.UserContextHolder;
import xyz.jasenon.lab.auth.context.UserContextStore;

import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserContextDubboFilterTests {

    private final UserContextDubboFilter filter = new UserContextDubboFilter();

    @AfterEach
    void cleanUp() {
        UserContextHolder.clear();
    }

    @Test
    void consumerOnlySendsUserId() {
        UserContextHolder.set(UserContext.builder().userId("user-1").username("admin").build());
        Invoker<Object> invoker = invoker("consumer");

        RpcInvocation invocation = new RpcInvocation();
        filter.invoke(invoker, invocation);

        assertThat(invocation.getAttachment("user-id")).isEqualTo("user-1");
    }

    @Test
    void providerRestoresContextFromStoreAndClearsItAfterInvocation() {
        UserContext stored = UserContext.builder().userId("user-1").username("admin").build();
        filter.setUserContextStore(store(stored));
        RpcInvocation invocation = new RpcInvocation();
        invocation.setAttachment("user-id", "user-1");
        Invoker<Object> invoker = invoker("provider", () -> {
            assertThat(UserContextHolder.get()).isEqualTo(stored);
            return new AppResponse();
        });

        filter.invoke(invoker, invocation);

        assertThat(UserContextHolder.get()).isNull();
    }

    @Test
    void providerRejectsRevokedContext() {
        filter.setUserContextStore(store(null));
        RpcInvocation invocation = new RpcInvocation();
        invocation.setAttachment("user-id", "revoked-user");

        assertThatThrownBy(() -> filter.invoke(invoker("provider"), invocation))
                .isInstanceOf(RpcException.class)
                .hasMessage("User context does not exist or has been revoked");
        assertThat(UserContextHolder.get()).isNull();
    }

    private static Invoker<Object> invoker(String side) {
        return invoker(side, AppResponse::new);
    }

    private static Invoker<Object> invoker(String side, Supplier<Result> invocation) {
        return new Invoker<>() {
            @Override
            public Class<Object> getInterface() {
                return Object.class;
            }

            @Override
            public Result invoke(Invocation ignored) {
                return invocation.get();
            }

            @Override
            public URL getUrl() {
                return URL.valueOf("tri://localhost/service?side=" + side);
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public void destroy() {
            }
        };
    }

    private static UserContextStore store(UserContext context) {
        return new UserContextStore() {
            @Override
            public void save(UserContext ignored) {
            }

            @Override
            public Optional<UserContext> find(String userId) {
                return Optional.ofNullable(context);
            }

            @Override
            public void delete(String userId) {
            }
        };
    }
}
