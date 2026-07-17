package xyz.jasenon.lab.base.config;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.context.UserContextHolder;

@Activate(
        group = CommonConstants.PROVIDER,
        order = -100
)
public class ProviderContextFilter implements Filter {
    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        UserContextHolder.clear();
        try {
            Object context = RpcContext.getServerAttachment()
                    .getObjectAttachment(UserContextHolder.DUBBO_ATTACHMENT_KEY);
            if (context == null) {
                context = RpcContext.getServerAttachment()
                        .getObjectAttachment(UserContextHolder.LEGACY_DUBBO_ATTACHMENT_KEY);
            }
            if (context instanceof UserContext userContext) {
                UserContextHolder.set(userContext);
            }
            return invoker.invoke(invocation);
        }finally {
            UserContextHolder.clear();
        }
    }
}
