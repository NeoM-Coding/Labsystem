package xyz.jasenon.lab.web.response;

import xyz.jasenon.lab.common.error.ErrorCode;
import xyz.jasenon.lab.observability.rpc.RpcErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import xyz.jasenon.lab.auth.exception.AuthenticationRequiredException;
import xyz.jasenon.lab.auth.exception.AuthorizationConfigurationException;
import xyz.jasenon.lab.auth.exception.PermissionDeniedException;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.common.rpc.RpcError;
import xyz.jasenon.lab.common.util.R;

@RestControllerAdvice
public class BusinessExceptionHandler {

    @ExceptionHandler({BusinessException.class, AuthenticationRequiredException.class,
            PermissionDeniedException.class, AuthorizationConfigurationException.class})
    public DiyResponseEntity<R<Object>> handle(RuntimeException exception) {
        return response(RpcErrors.from(exception));
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
        var fallback = ErrorCode.INTERNAL_ERROR;
        return response(RpcErrors.from(exception, fallback, fallback.message()));
    }

    private static DiyResponseEntity<R<Object>> response(RpcError error) {
        return DiyResponseEntity.of(R.fail(error.status(), error.code(), error.message(), error.data()).withTraceId(error.traceId()));
    }
}
