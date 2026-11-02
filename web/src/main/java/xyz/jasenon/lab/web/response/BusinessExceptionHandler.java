package xyz.jasenon.lab.web.response;

import org.apache.dubbo.rpc.RpcException;
import xyz.jasenon.lab.common.error.ErrorCode;
import xyz.jasenon.lab.observability.context.TraceContext;
import xyz.jasenon.lab.observability.rpc.RpcErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import xyz.jasenon.lab.auth.exception.AuthenticationRequiredException;
import xyz.jasenon.lab.auth.exception.AuthorizationConfigurationException;
import xyz.jasenon.lab.auth.exception.PermissionDeniedException;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.common.util.R;

@RestControllerAdvice
public class BusinessExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(BusinessExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public DiyResponseEntity<R<Object>> handle(BusinessException exception) {
        if (exception.getType() == xyz.jasenon.lab.common.rpc.RpcErrorType.INTERNAL) log.error("业务执行失败", exception);
        return DiyResponseEntity.of(R.fail(exception.getHttpStatus(), exception.getCode(),
                exception.getMessage(), exception.getData()).withTraceId(
                exception.getTraceId() == null ? TraceContext.traceId() : exception.getTraceId()));
    }

    @ExceptionHandler(AuthenticationRequiredException.class)
    public DiyResponseEntity<R<Object>> handleAuthentication(AuthenticationRequiredException exception) {
        return DiyResponseEntity.of(R.unauthorized(exception.getMessage()));
    }

    @ExceptionHandler(PermissionDeniedException.class)
    public DiyResponseEntity<R<Object>> handlePermissionDenied(PermissionDeniedException exception) {
        return DiyResponseEntity.of(R.forbidden(exception.getMessage()));
    }

    @ExceptionHandler(AuthorizationConfigurationException.class)
    public DiyResponseEntity<R<Object>> handleAuthorizationConfiguration(
            AuthorizationConfigurationException exception) {
        log.error("权限配置异常", exception);
        return handle(new BusinessException(ErrorCode.AUTH_CONFIGURATION_ERROR));
    }

    @ExceptionHandler(RpcException.class)
    public DiyResponseEntity<R<Object>> handleRpcException(RpcException exception) {
        var error = RpcErrors.from(exception);
        return handle(new BusinessException(error.code(), error.status(), error.type(), error.message(), error.data(), error.traceId()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ServletRequestBindingException.class,
            HttpMessageNotReadableException.class, HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class, ConstraintViolationException.class, IllegalArgumentException.class})
    public DiyResponseEntity<R<Object>> handleValidation(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException validation) {
            var fields = validation.getBindingResult().getFieldErrors().stream()
                    .map(field -> java.util.Map.of("field", field.getField(), "message",
                            field.getDefaultMessage() == null ? "字段不合法" : field.getDefaultMessage()))
                    .toList();
            return handle(new BusinessException(ErrorCode.VALIDATION_FAILED,
                    ErrorCode.VALIDATION_FAILED.message(), java.util.Map.of("fieldErrors", fields)));
        }
        return handle(new BusinessException(ErrorCode.VALIDATION_FAILED));
    }

    @ExceptionHandler(Exception.class)
    public DiyResponseEntity<R<Object>> handleUnexpectedException(Exception exception) {
        log.error("请求处理发生未声明异常", exception);
        return DiyResponseEntity.of(R.serverError("服务器内部错误"));
    }
}
