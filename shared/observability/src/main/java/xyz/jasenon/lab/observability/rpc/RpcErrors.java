package xyz.jasenon.lab.observability.rpc;

import org.apache.dubbo.rpc.RpcException;
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
            return error("AUTHENTICATION_REQUIRED", RpcErrorType.AUTHENTICATION, 401,
                    exception.getMessage(), traceId);
        }
        if (cause instanceof PermissionDeniedException exception) {
            return error("PERMISSION_DENIED", RpcErrorType.AUTHORIZATION, 403,
                    exception.getMessage(), traceId);
        }
        if (cause instanceof BusinessException exception) {
            int status = exception.getCode() == null ? 500 : exception.getCode();
            return error("BUSINESS_" + status, typeOf(status), status,
                    safeMessage(exception.getMessage(), "业务处理失败"), traceId);
        }
        if (cause instanceof IllegalArgumentException exception) {
            return error("VALIDATION_FAILED", RpcErrorType.VALIDATION, 400,
                    safeMessage(exception.getMessage(), "请求参数不合法"), traceId);
        }

        log.error("RPC provider execution failed, traceId={}", traceId, cause);
        return error("INTERNAL_ERROR", RpcErrorType.INTERNAL, 500,
                "下游服务处理失败", traceId);
    }

    private static RpcError error(String code, RpcErrorType type, int status,
                                  String message, String traceId) {
        return new RpcError(code, type, status, message, traceId);
    }

    private static RpcErrorType typeOf(int status) {
        return switch (status) {
            case 400, 422 -> RpcErrorType.VALIDATION;
            case 401 -> RpcErrorType.AUTHENTICATION;
            case 403 -> RpcErrorType.AUTHORIZATION;
            case 404 -> RpcErrorType.NOT_FOUND;
            case 409 -> RpcErrorType.CONFLICT;
            default -> status >= 500 ? RpcErrorType.INTERNAL : RpcErrorType.BUSINESS;
        };
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException
                || current instanceof ExecutionException
                || current instanceof RpcException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String safeMessage(String message, String fallback) {
        return message == null || message.isBlank() ? fallback : message;
    }
}
