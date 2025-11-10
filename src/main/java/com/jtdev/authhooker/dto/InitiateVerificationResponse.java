package com.jtdev.authhooker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response containing OAuth authorization URL
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiateVerificationResponse {
    
    /**
     * OAuth authorization URL to redirect user to
     * User should be redirected to this URL to complete verification
     */
    private String verificationUrl;
    
    /**
     * OAuth state token for CSRF protection
     * Will be validated in callback
     */
    private String state;
    
    /**
     * Verification session expiration timestamp
     */
    private LocalDateTime expiresAt;
    
    /**
     * Verification session ID for tracking
     */
    private UUID sessionId;
}
