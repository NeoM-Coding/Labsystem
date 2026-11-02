package xyz.jasenon.lab.observability.rpc;

import org.apache.dubbo.rpc.RpcException;
import xyz.jasenon.lab.common.error.ErrorCode;
import xyz.jasenon.lab.auth.exception.AuthorizationConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.jasenon.lab.auth.exception.AuthenticationRequiredException;
import xyz.jasenon.lab.auth.exception.PermissionDeniedException;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.common.rpc.RpcError;
import xyz.jasenon.lab.common.rpc.RpcErrorType;
import xyz.jasenon.lab.observability.context.TraceContext;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public final class RpcErrors {

    private static final Logger log = LoggerFactory.getLogger(RpcErrors.class);

    private RpcErrors() {
    }

    public static RpcError from(Throwable failure) {
        Throwable cause = unwrap(failure);
        String traceId = TraceContext.traceId();
        if (cause instanceof AuthenticationRequiredException exception) {
            return error(ErrorCode.AUTHENTICATION_REQUIRED.code(), RpcErrorType.AUTHENTICATION, 401,
                    exception.getMessage(), traceId);
        }
        if (cause instanceof PermissionDeniedException exception) {
            return error(ErrorCode.PERMISSION_DENIED.code(), RpcErrorType.AUTHORIZATION, 403,
                    exception.getMessage(), traceId);
        }
        if (cause instanceof BusinessException exception) {
            return new RpcError(exception.getCode(), exception.getType(), exception.getHttpStatus(),
                    safeMessage(exception.getMessage(), "业务处理失败"),
                    exception.getTraceId() == null ? traceId : exception.getTraceId(), exception.getData());
        }
        if (cause instanceof IllegalArgumentException exception) {
            return error(ErrorCode.VALIDATION_FAILED.code(), RpcErrorType.VALIDATION, 400,
                    safeMessage(exception.getMessage(), "请求参数不合法"), traceId);
        }

        if (cause instanceof AuthorizationConfigurationException) {
            return error(ErrorCode.AUTH_CONFIGURATION_ERROR.code(), RpcErrorType.INTERNAL, 500, "权限配置异常", traceId);
        }
        if (cause instanceof RpcException rpc) {
            ErrorCode code = rpc.isTimeout() ? ErrorCode.RPC_TIMEOUT : ErrorCode.DEPENDENCY_UNAVAILABLE;
            return error(code.code(), code.type(), code.httpStatus(), code.message(), traceId);
        }
        log.error("RPC provider execution failed, traceId={}", traceId, cause);
        return error(ErrorCode.INTERNAL_ERROR.code(), RpcErrorType.INTERNAL, 500,
                "下游服务处理失败", traceId);
    }

    private static RpcError error(int code, RpcErrorType type, int status,
                                  String message, String traceId) {
        return new RpcError(code, type, status, message, traceId);
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException
                || current instanceof ExecutionException
                || (current instanceof RpcException && !((RpcException) current).isTimeout()))
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String safeMessage(String message, String fallback) {
        return message == null || message.isBlank() ? fallback : message;
    }
}
