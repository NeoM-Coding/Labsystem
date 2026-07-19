package xyz.jasenon.lab.web.response;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import xyz.jasenon.lab.auth.exception.AuthenticationRequiredException;
import xyz.jasenon.lab.auth.exception.AuthorizationConfigurationException;
import xyz.jasenon.lab.auth.exception.PermissionDeniedException;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.common.util.R;

@RestControllerAdvice
public class BusinessExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public DiyResponseEntity<R<Object>> handle(BusinessException exception) {
        R<Object> response = exception.getR() == null
                ? R.fail(exception.getCode(), exception.getMessage(), exception.getData())
                : R.fail(exception.getR().getCode(), exception.getR().getMsg(), exception.getR().getData());
        return DiyResponseEntity.of(response);
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
        return DiyResponseEntity.of(R.serverError(exception.getMessage()));
    }
}
