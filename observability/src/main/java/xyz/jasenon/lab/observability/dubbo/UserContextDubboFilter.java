package xyz.jasenon.lab.observability.dubbo;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.context.UserContextHolder;
import xyz.jasenon.lab.auth.context.UserContextStore;

@Activate(group = {CommonConstants.CONSUMER, CommonConstants.PROVIDER}, order = -190)
public class UserContextDubboFilter implements Filter {

    private static final String USER_ID_ATTACHMENT = "user-id";

    private UserContextStore userContextStore;

    /**
     * Dubbo SPI uses its Spring extension factory to inject the context store bean.
     */
    public void setUserContextStore(UserContextStore userContextStore) {
        this.userContextStore = userContextStore;
    }

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String side = invoker.getUrl().getParameter(CommonConstants.SIDE_KEY);
        if (CommonConstants.CONSUMER_SIDE.equals(side)) {
            return invokeConsumer(invoker, invocation);
        }
        return invokeProvider(invoker, invocation);
    }

    private Result invokeConsumer(Invoker<?> invoker, Invocation invocation) {
        UserContext context = UserContextHolder.get();
        if (context != null && hasText(context.getUserId())) {
            invocation.setAttachment(USER_ID_ATTACHMENT, context.getUserId().trim());
        }
        return invoker.invoke(invocation);
    }

    private Result invokeProvider(Invoker<?> invoker, Invocation invocation) {
        String userId = invocation.getAttachment(USER_ID_ATTACHMENT);
        UserContextHolder.clear();
        if (hasText(userId)) {
            UserContextHolder.set(loadContext(userId.trim()));
        }
        try {
            return invoker.invoke(invocation);
        } finally {
            // Dubbo provider threads are reused across users.
            UserContextHolder.clear();
        }
    }

    private UserContext loadContext(String userId) {
        if (userContextStore == null) {
            throw new RpcException("UserContextStore is unavailable on the Dubbo provider");
        }
        return userContextStore.find(userId)
                .orElseThrow(() -> new RpcException("User context does not exist or has been revoked"));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
