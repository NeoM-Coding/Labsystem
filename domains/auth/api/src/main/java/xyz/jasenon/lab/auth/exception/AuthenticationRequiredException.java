package xyz.jasenon.lab.auth.exception;

import xyz.jasenon.lab.common.exception.BusinessException;

public class AuthenticationRequiredException extends BusinessException {

    public AuthenticationRequiredException(String message) {
        super(401, message);
    }
}
