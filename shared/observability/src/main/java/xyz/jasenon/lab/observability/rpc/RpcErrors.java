package xyz.jasenon.lab.observability.rpc;

import org.apache.dubbo.rpc.RpcException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.transaction.CannotCreateTransactionException;
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
        return from(failure, ErrorCode.INTERNAL_ERROR, "下游服务处理失败");
    }

    public static RpcError from(Throwable failure, ErrorCode fallback, String fallbackMessage) {
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
            if (exception.getHttpStatus() >= 500) {
                log.error("BusinessException: code={}, traceId={}", exception.getCode(), traceId, exception);
            } else if (exception.getCause() != null) {
                log.warn("BusinessException: code={}, traceId={}, cause={}",
                        exception.getCode(), traceId, exception.getCause().getClass().getSimpleName());
            } else {
                log.warn("BusinessException: code={}, traceId={}", exception.getCode(), traceId);
            }
            return new RpcError(exception.getCode(), exception.getType(), exception.getHttpStatus(),
                    safeMessage(exception.getMessage(), "业务处理失败"),
                    exception.getTraceId() == null ? traceId : exception.getTraceId(), exception.getData());
        }
        if (cause instanceof IllegalArgumentException exception) {
            return error(ErrorCode.VALIDATION_FAILED.code(), RpcErrorType.VALIDATION, 400,
                    safeMessage(exception.getMessage(), "请求参数不合法"), traceId);
        }
        if (cause instanceof DataAccessException || cause instanceof CannotCreateTransactionException) {
            ErrorCode code;
            if (cause instanceof DuplicateKeyException) {
                code = ErrorCode.DUPLICATE_KEY;
            } else if (cause instanceof DataIntegrityViolationException) {
                code = ErrorCode.DATA_CONSTRAINT_VIOLATION;
            } else if (cause instanceof ConcurrencyFailureException) {
                code = ErrorCode.DATA_CONCURRENT_UPDATE;
            } else if (cause instanceof DataAccessResourceFailureException
                    || cause instanceof TransientDataAccessResourceException
                    || cause instanceof QueryTimeoutException
                    || cause instanceof CannotCreateTransactionException) {
                code = ErrorCode.DATA_STORE_UNAVAILABLE;
            } else {
                code = ErrorCode.DATA_ACCESS_FAILED;
            }
            if (code.httpStatus() >= 500) {
                log.error("Data access failed, code={}, traceId={}", code.code(), traceId, cause);
            } else {
                log.warn("{}: code={}, traceId={}", cause.getClass().getSimpleName(), code.code(), traceId);
            }
            return error(code.code(), code.type(), code.httpStatus(), code.message(), traceId);
        }

        if (cause instanceof AuthorizationConfigurationException) {
            log.error("Authorization configuration failed, traceId={}", traceId, cause);
            return error(ErrorCode.AUTH_CONFIGURATION_ERROR.code(), RpcErrorType.INTERNAL, 500, "权限配置异常", traceId);
        }
        if (cause instanceof RpcException rpc) {
            ErrorCode code = rpc.isTimeout() ? ErrorCode.RPC_TIMEOUT : ErrorCode.DEPENDENCY_UNAVAILABLE;
            return error(code.code(), code.type(), code.httpStatus(), code.message(), traceId);
        }
        log.error("Service exception, code={}, traceId={}", fallback.code(), traceId, cause);
        return error(fallback.code(), fallback.type(), fallback.httpStatus(), fallbackMessage, traceId);
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
