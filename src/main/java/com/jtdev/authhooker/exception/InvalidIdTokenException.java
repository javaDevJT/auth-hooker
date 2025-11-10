package com.jtdev.authhooker.exception;

/**
 * Exception thrown when ID token validation fails
 */
public class InvalidIdTokenException extends OAuthException {
    
    public InvalidIdTokenException(String message) {
        super(message);
    }
    
    public InvalidIdTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
