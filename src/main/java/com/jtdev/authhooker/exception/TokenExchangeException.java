package com.jtdev.authhooker.exception;

/**
 * Exception thrown when token exchange fails
 */
public class TokenExchangeException extends OAuthException {
    
    public TokenExchangeException(String message) {
        super(message);
    }
    
    public TokenExchangeException(String message, Throwable cause) {
        super(message, cause);
    }
}
