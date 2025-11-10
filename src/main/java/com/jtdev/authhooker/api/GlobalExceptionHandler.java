package com.jtdev.authhooker.api;

import com.jtdev.authhooker.dto.response.ErrorResponse;
import com.jtdev.authhooker.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Global exception handler for REST API
 * Converts exceptions to standardized error responses
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * Handle ResourceNotFoundException (404)
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request) {
        
        String correlationId = UUID.randomUUID().toString();
        log.debug("Resource not found: {} [correlationId={}]", ex.getMessage(), correlationId);
        
        ErrorResponse error = ErrorResponse.of(
                "ResourceNotFound",
                ex.getMessage(),
                request.getRequestURI(),
                correlationId
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    /**
     * Handle ValidationException (400)
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            ValidationException ex,
            HttpServletRequest request) {
        
        String correlationId = UUID.randomUUID().toString();
        log.debug("Validation error: {} [correlationId={}]", ex.getMessage(), correlationId);
        
        ErrorResponse error = ErrorResponse.of(
                "ValidationError",
                ex.getMessage(),
                request.getRequestURI(),
                correlationId
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    /**
     * Handle ConflictException (409)
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(
            ConflictException ex,
            HttpServletRequest request) {
        
        String correlationId = UUID.randomUUID().toString();
        log.debug("Conflict error: {} [correlationId={}]", ex.getMessage(), correlationId);
        
        ErrorResponse error = ErrorResponse.of(
                "Conflict",
                ex.getMessage(),
                request.getRequestURI(),
                correlationId
        );
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }
    
    /**
     * Handle PlanLimitExceededException (403)
     */
    @ExceptionHandler(PlanLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handlePlanLimitExceeded(
            PlanLimitExceededException ex,
            HttpServletRequest request) {
        
        String correlationId = UUID.randomUUID().toString();
        log.warn("Plan limit exceeded: {} [correlationId={}]", ex.getMessage(), correlationId);
        
        ErrorResponse error = ErrorResponse.of(
                "PlanLimitExceeded",
                ex.getMessage(),
                request.getRequestURI(),
                correlationId
        );
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }
    
    /**
     * Handle EncryptionException (500)
     */
    @ExceptionHandler(EncryptionException.class)
    public ResponseEntity<ErrorResponse> handleEncryption(
            EncryptionException ex,
            HttpServletRequest request) {
        
        String correlationId = UUID.randomUUID().toString();
        log.error("Encryption error: {} [correlationId={}]", ex.getMessage(), correlationId, ex);
        
        ErrorResponse error = ErrorResponse.of(
                "EncryptionError",
                "An encryption error occurred. Please contact support.",
                request.getRequestURI(),
                correlationId
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
    
    /**
     * Handle MethodArgumentNotValidException (400)
     * Thrown by @Valid annotation on request bodies
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        
        String correlationId = UUID.randomUUID().toString();
        
        // Collect all validation errors
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        
        log.debug("Validation error: {} [correlationId={}]", message, correlationId);
        
        ErrorResponse error = ErrorResponse.of(
                "ValidationError",
                message,
                request.getRequestURI(),
                correlationId
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    /**
     * Handle AccessDeniedException (403)
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {
        
        String correlationId = UUID.randomUUID().toString();
        log.warn("Access denied: {} [correlationId={}]", ex.getMessage(), correlationId);
        
        ErrorResponse error = ErrorResponse.of(
                "AccessDenied",
                "You do not have permission to access this resource",
                request.getRequestURI(),
                correlationId
        );
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }
    
    /**
     * Handle AuthenticationException (401)
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(
            AuthenticationException ex,
            HttpServletRequest request) {
        
        String correlationId = UUID.randomUUID().toString();
        log.debug("Authentication error: {} [correlationId={}]", ex.getMessage(), correlationId);
        
        ErrorResponse error = ErrorResponse.of(
                "Unauthorized",
                "Authentication required. Please provide valid credentials.",
                request.getRequestURI(),
                correlationId
        );
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }
    
    /**
     * Handle OAuthCallbackException (400)
     */
    @ExceptionHandler(OAuthCallbackException.class)
    public ResponseEntity<ErrorResponse> handleOAuthCallback(
            OAuthCallbackException ex,
            HttpServletRequest request) {
        
        String correlationId = UUID.randomUUID().toString();
        log.error("OAuth callback error: {} [correlationId={}]", ex.getMessage(), correlationId);
        
        ErrorResponse error = ErrorResponse.of(
                "OAuthCallbackError",
                ex.getMessage(),
                request.getRequestURI(),
                correlationId
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    /**
     * Handle TokenExchangeException (502)
     */
    @ExceptionHandler(TokenExchangeException.class)
    public ResponseEntity<ErrorResponse> handleTokenExchange(
            TokenExchangeException ex,
            HttpServletRequest request) {
        
        String correlationId = UUID.randomUUID().toString();
        log.error("Token exchange error: {} [correlationId={}]", ex.getMessage(), correlationId, ex);
        
        ErrorResponse error = ErrorResponse.of(
                "TokenExchangeError",
                "Failed to exchange authorization code for tokens. Please try again.",
                request.getRequestURI(),
                correlationId
        );
        
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
    }
    
    /**
     * Handle InvalidIdTokenException (502)
     */
    @ExceptionHandler(InvalidIdTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidIdToken(
            InvalidIdTokenException ex,
            HttpServletRequest request) {
        
        String correlationId = UUID.randomUUID().toString();
        log.error("Invalid ID token: {} [correlationId={}]", ex.getMessage(), correlationId, ex);
        
        ErrorResponse error = ErrorResponse.of(
                "InvalidIdToken",
                "ID token validation failed. Please try again.",
                request.getRequestURI(),
                correlationId
        );
        
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
    }
    
    /**
     * Handle SessionExpiredException (410)
     */
    @ExceptionHandler(SessionExpiredException.class)
    public ResponseEntity<ErrorResponse> handleSessionExpired(
            SessionExpiredException ex,
            HttpServletRequest request) {
        
        String correlationId = UUID.randomUUID().toString();
        log.warn("Session expired: {} [correlationId={}]", ex.getMessage(), correlationId);
        
        ErrorResponse error = ErrorResponse.of(
                "SessionExpired",
                "Verification session has expired. Please start a new verification.",
                request.getRequestURI(),
                correlationId
        );
        
        return ResponseEntity.status(HttpStatus.GONE).body(error);
    }
    
    /**
     * Handle generic OAuthException (502)
     */
    @ExceptionHandler(OAuthException.class)
    public ResponseEntity<ErrorResponse> handleOAuth(
            OAuthException ex,
            HttpServletRequest request) {
        
        String correlationId = UUID.randomUUID().toString();
        log.error("OAuth error: {} [correlationId={}]", ex.getMessage(), correlationId, ex);
        
        ErrorResponse error = ErrorResponse.of(
                "OAuthError",
                "OAuth authentication failed: " + ex.getMessage(),
                request.getRequestURI(),
                correlationId
        );
        
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
    }
    
    /**
     * Handle generic Exception (500)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {
        
        String correlationId = UUID.randomUUID().toString();
        log.error("Unexpected error: {} [correlationId={}]", ex.getMessage(), correlationId, ex);
        
        ErrorResponse error = ErrorResponse.of(
                "InternalServerError",
                "An unexpected error occurred. Please contact support with correlation ID: " + correlationId,
                request.getRequestURI(),
                correlationId
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
