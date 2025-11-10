package com.jtdev.authhooker.exception;

/**
 * Exception thrown during OAuth callback processing
 */
public class OAuthCallbackException extends OAuthException {
    
    public OAuthCallbackException(String message) {
        super(message);
    }
    
    public OAuthCallbackException(String message, Throwable cause) {
        super(message, cause);
    }
}
