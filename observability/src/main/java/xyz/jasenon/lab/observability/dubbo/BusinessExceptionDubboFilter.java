package xyz.jasenon.lab.observability.dubbo;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import xyz.jasenon.lab.common.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Activate(group = {CommonConstants.CONSUMER, CommonConstants.PROVIDER}, order = -190)
public class BusinessExceptionDubboFilter implements Filter {

    static final String TYPE_ATTACHMENT = "lab-business-exception";
    static final String CODE_ATTACHMENT = "lab-business-code";
    static final String MESSAGE_ATTACHMENT = "lab-business-message";
    static final String WIRE_PREFIX = "LAB_BUSINESS_EXCEPTION:";

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        Result result = invoker.invoke(invocation);
        String side = invoker.getUrl().getParameter(CommonConstants.SIDE_KEY);
        if (CommonConstants.PROVIDER_SIDE.equals(side)) {
            return result.whenCompleteWithContext((response, failure) -> encode(response));
        }
        return result.whenCompleteWithContext((response, failure) -> decode(response));
    }

    static void encode(Result result) {
        if (result == null) {
            return;
        }
        BusinessException exception;
        if (result.getException() instanceof BusinessException businessException) {
            exception = businessException;
        } else if (result.getException() instanceof IllegalArgumentException argumentException) {
            exception = new BusinessException(400, argumentException.getMessage());
        } else {
            return;
        }
        result.setAttachment(TYPE_ATTACHMENT, Boolean.TRUE.toString());
        result.setAttachment(CODE_ATTACHMENT, exception.getCode().toString());
        result.setAttachment(MESSAGE_ATTACHMENT, exception.getMessage());
        result.setException(new RuntimeException(wireMessage(exception)));
    }

    static void decode(Result result) {
        if (result == null) {
            return;
        }
        String code = result.getAttachment(CODE_ATTACHMENT);
        String message = result.getAttachment(MESSAGE_ATTACHMENT);
        if (!Boolean.parseBoolean(result.getAttachment(TYPE_ATTACHMENT))) {
            String remoteMessage = result.getException() == null ? null : result.getException().getMessage();
            int marker = remoteMessage == null ? -1 : remoteMessage.indexOf(WIRE_PREFIX);
            if (marker < 0) {
                return;
            }
            String[] fields = remoteMessage.substring(marker + WIRE_PREFIX.length()).split(":", 2);
            if (fields.length != 2) {
                result.setException(new BusinessException(500, "下游服务返回了无效的业务异常状态"));
                return;
            }
            code = fields[0];
            try {
                message = new String(Base64.getUrlDecoder().decode(fields[1]), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException exception) {
                result.setException(new BusinessException(500, "下游服务返回了无效的业务异常状态"));
                return;
            }
        }
        try {
            result.setException(new BusinessException(Integer.parseInt(code), message));
        } catch (NumberFormatException exception) {
            result.setException(new BusinessException(500, "下游服务返回了无效的业务异常状态"));
        }
    }

    private static String wireMessage(BusinessException exception) {
        String message = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(exception.getMessage().getBytes(StandardCharsets.UTF_8));
        return WIRE_PREFIX + exception.getCode() + ":" + message;
    }
}
