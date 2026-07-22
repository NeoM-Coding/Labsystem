package xyz.jasenon.lab.web.response;

import org.apache.dubbo.rpc.RpcException;
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

    @ExceptionHandler(RpcException.class)
    public DiyResponseEntity<R<Object>> handleRpcException(RpcException exception) {
        log.error("下游 RPC 服务调用失败", exception);
        return DiyResponseEntity.of(R.fail(503, 503, "下游服务暂不可用"));
    }

    @ExceptionHandler(Exception.class)
    public DiyResponseEntity<R<Object>> handleUnexpectedException(Exception exception) {
        log.error("请求处理发生未声明异常", exception);
        return DiyResponseEntity.of(R.serverError("服务器内部错误"));
    }
}
