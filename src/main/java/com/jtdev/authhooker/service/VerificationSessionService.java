package com.jtdev.authhooker.service;

import com.jtdev.authhooker.domain.Provider;
import com.jtdev.authhooker.domain.Tenant;
import com.jtdev.authhooker.domain.VerificationSession;
import com.jtdev.authhooker.exception.ResourceNotFoundException;
import com.jtdev.authhooker.exception.ValidationException;
import com.jtdev.authhooker.repository.VerificationSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing OAuth verification sessions
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class VerificationSessionService {
    
    private final VerificationSessionRepository sessionRepository;
    private final TenantService tenantService;
    private final ProviderService providerService;
    
    private final SecureRandom secureRandom = new SecureRandom();
    
    private static final int SESSION_EXPIRATION_MINUTES = 10;
    
    /**
     * Create a new verification session
     */
    public VerificationSession createSession(UUID tenantId, UUID providerId, 
                                             String platform, String platformUserId) {
        log.info("Creating verification session for tenant={}, provider={}, platform={}", 
                tenantId, providerId, platform);
        
        // Validate tenant and provider
        Tenant tenant = tenantService.getTenantById(tenantId);
        Provider provider = providerService.getProviderById(providerId);
        
        // Generate secure random state token
        String stateToken = generateSecureToken();
        
        // Generate code verifier for PKCE
        String codeVerifier = generateCodeVerifier();
        
        // Generate nonce for OIDC
        String nonce = generateSecureToken();
        
        // Calculate expiration
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(SESSION_EXPIRATION_MINUTES);
        
        VerificationSession session = VerificationSession.builder()
                .tenant(tenant)
                .provider(provider)
                .stateToken(stateToken)
                .codeVerifier(codeVerifier)
                .nonce(nonce)
                .platformType(platform)
                .platformUserId(platformUserId)
                .sessionData(Map.of())
                .status("pending")
                .expiresAt(expiresAt)
                .build();
        
        session = sessionRepository.save(session);
        log.info("Verification session created: {} (state={})", session.getId(), stateToken);
        
        return session;
    }
    
    /**
     * Get session by state token
     */
    @Transactional(readOnly = true)
    public Optional<VerificationSession> getSessionByState(String stateToken) {
        if (stateToken == null || stateToken.isBlank()) {
            return Optional.empty();
        }
        
        Optional<VerificationSession> session = sessionRepository.findByStateToken(stateToken);
        
        // Check if session is expired
        if (session.isPresent() && session.get().isExpired()) {
            log.warn("Verification session expired: {}", session.get().getId());
            return Optional.empty();
        }
        
        return session;
    }
    
    /**
     * Complete a verification session
     */
    public VerificationSession completeSession(String stateToken, Map<String, Object> claims) {
        log.info("Completing verification session: {}", stateToken);
        
        VerificationSession session = getSessionByState(stateToken)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Verification session not found or expired: " + stateToken));
        
        if (!session.isPending()) {
            throw new ValidationException(
                "Verification session is not pending: " + session.getStatus());
        }
        
        // Update session
        session.complete();
        
        // Store claims in session data if needed
        if (claims != null && !claims.isEmpty()) {
            session.setSessionData(claims);
        }
        
        session = sessionRepository.save(session);
        log.info("Verification session completed: {}", session.getId());
        
        return session;
    }
    
    /**
     * Expire a session manually
     */
    public void expireSession(String stateToken) {
        log.info("Expiring verification session: {}", stateToken);
        
        VerificationSession session = sessionRepository.findByStateToken(stateToken)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Verification session not found: " + stateToken));
        
        session.expire();
        sessionRepository.save(session);
        
        log.info("Verification session expired: {}", session.getId());
    }
    
    /**
     * Clean up expired sessions
     * 
     * @return number of sessions cleaned up
     */
    public int cleanupExpiredSessions() {
        log.info("Cleaning up expired verification sessions");
        
        LocalDateTime now = LocalDateTime.now();
        
        // Update status of expired sessions
        int expiredCount = sessionRepository.expireOldSessions(now);
        
        // Delete sessions older than 24 hours
        LocalDateTime oneDayAgo = now.minusHours(24);
        int deletedCount = sessionRepository.deleteOldSessions(oneDayAgo);
        
        log.info("Expired {} sessions, deleted {} old sessions", expiredCount, deletedCount);
        
        return expiredCount + deletedCount;
    }
    
    /**
     * Generate a secure random token (32 bytes, base64-encoded)
     */
    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    
    /**
     * Generate a code verifier for PKCE (43-128 characters)
     */
    private String generateCodeVerifier() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
