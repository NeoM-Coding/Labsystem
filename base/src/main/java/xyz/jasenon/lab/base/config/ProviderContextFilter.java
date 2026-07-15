package xyz.jasenon.lab.base.config;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;
import xyz.jasenon.lab.base.context.Holder;
import xyz.jasenon.lab.base.context.UserContext;

@Activate(
        group = CommonConstants.PROVIDER,
        order = -100
)
public class ProviderContextFilter implements Filter {
    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        Holder.clear();
        try {
            UserContext ctx = (UserContext) RpcContext.getServerAttachment().getObjectAttachment("user-conext");
            Holder.set(ctx);
            return invoker.invoke(invocation);
        }finally {
            Holder.clear();
        }
    }
}
