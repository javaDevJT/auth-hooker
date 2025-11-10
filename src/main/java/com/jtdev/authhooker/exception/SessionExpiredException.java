package com.jtdev.authhooker.exception;

/**
 * Exception thrown when a verification session has expired
 */
public class SessionExpiredException extends OAuthException {
    
    public SessionExpiredException(String message) {
        super(message);
    }
    
    public SessionExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
