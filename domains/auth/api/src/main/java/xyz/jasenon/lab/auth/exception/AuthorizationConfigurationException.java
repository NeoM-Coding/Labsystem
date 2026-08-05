package xyz.jasenon.lab.auth.exception;

import xyz.jasenon.lab.common.exception.BusinessException;

public class AuthorizationConfigurationException extends BusinessException {

    public AuthorizationConfigurationException(String message) {
        super(500, message);
    }

    public AuthorizationConfigurationException(String message, Throwable cause) {
        super(500, message);
        initCause(cause);
    }
}
