package com.jtdev.authhooker.exception;

/**
 * Base exception for OAuth/OIDC related errors
 */
public class OAuthException extends RuntimeException {
    
    public OAuthException(String message) {
        super(message);
    }
    
    public OAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
