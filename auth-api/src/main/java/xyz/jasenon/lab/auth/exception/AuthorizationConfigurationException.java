package xyz.jasenon.lab.auth.exception;

public class AuthorizationConfigurationException extends RuntimeException {

    public AuthorizationConfigurationException(String message) {
        super(message);
    }

    public AuthorizationConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
