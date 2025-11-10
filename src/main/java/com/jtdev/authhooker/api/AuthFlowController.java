package com.jtdev.authhooker.api;

import com.jtdev.authhooker.domain.Provider;
import com.jtdev.authhooker.domain.User;
import com.jtdev.authhooker.domain.UserPlatformMapping;
import com.jtdev.authhooker.domain.VerificationSession;
import com.jtdev.authhooker.dto.*;
import com.jtdev.authhooker.exception.*;
import com.jtdev.authhooker.service.*;
import com.jtdev.authhooker.util.PkceUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Controller for OAuth authorization flow
 */
@Slf4j
@RestController
@Validated
@RequiredArgsConstructor
public class AuthFlowController {
    
    private final VerificationSessionService sessionService;
    private final ProviderService providerService;
    private final OidcClient oidcClient;
    private final ClaimsNormalizer claimsNormalizer;
    private final UserService userService;
    private final AuditService auditService;
    
    @Value("${app.oauth.success-redirect-url:https://auth.javadevjt.tech/success}")
    private String successRedirectUrl;
    
    @Value("${app.oauth.error-redirect-url:https://auth.javadevjt.tech/error}")
    private String errorRedirectUrl;
    
    /**
     * POST /api/v1/auth/initiate
     * Start the OAuth verification flow
     */
    @PostMapping("/api/v1/auth/initiate")
    public ResponseEntity<InitiateVerificationResponse> initiateVerification(
            @Valid @RequestBody InitiateVerificationRequest request) {
        
        log.info("Initiating verification: tenant={}, provider={}, platform={}, platformUserId={}", 
                request.getTenantId(), request.getProviderId(), 
                request.getPlatform(), request.getPlatformUserId());
        
        try {
            // 1. Validate tenant and provider exist
            Provider provider = providerService.getProviderById(request.getProviderId());
            
            // Verify provider belongs to the requested tenant
            if (!provider.getTenant().getId().equals(request.getTenantId())) {
                throw new ValidationException(
                    "Provider does not belong to the specified tenant");
            }
            
            // Verify provider is active
            if (!Boolean.TRUE.equals(provider.getIsActive())) {
                throw new ValidationException("Provider is not active: " + request.getProviderId());
            }
            
            // 2. Create verification session
            VerificationSession session = sessionService.createSession(
                    request.getTenantId(),
                    request.getProviderId(),
                    request.getPlatform(),
                    request.getPlatformUserId()
            );
            
            // 3. Generate PKCE code challenge
            String codeVerifier = session.getCodeVerifier();
            String codeChallenge = PkceUtil.generateCodeChallenge(codeVerifier);
            
            // 4. Build authorization URL
            String authUrl = oidcClient.buildAuthorizationUrl(
                    provider,
                    session.getStateToken(),
                    codeChallenge
            );
            
            // 5. Build response
            InitiateVerificationResponse response = InitiateVerificationResponse.builder()
                    .verificationUrl(authUrl)
                    .state(session.getStateToken())
                    .expiresAt(session.getExpiresAt())
                    .sessionId(session.getId())
                    .build();
            
            log.info("Verification initiated successfully: sessionId={}, state={}", 
                    session.getId(), session.getStateToken());
            
            // Audit log
            auditService.logAction(request.getTenantId(), null, "verification.initiated",
                    Map.of(
                        "sessionId", session.getId().toString(),
                        "providerId", request.getProviderId().toString(),
                        "platform", request.getPlatform(),
                        "platformUserId", request.getPlatformUserId()
                    ));
            
            return ResponseEntity.ok(response);
            
        } catch (ResourceNotFoundException | ValidationException e) {
            log.error("Verification initiation failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during verification initiation: {}", e.getMessage(), e);
            throw new OAuthException("Failed to initiate verification: " + e.getMessage(), e);
        }
    }
    
    /**
     * GET /oauth/callback/{tenantId}/{providerId}
     * OAuth callback endpoint (called by identity provider)
     */
    @GetMapping("/oauth/callback/{tenantId}/{providerId}")
    public RedirectView handleCallback(
            @PathVariable UUID tenantId,
            @PathVariable UUID providerId,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String error_description) {
        
        log.info("OAuth callback received: tenant={}, provider={}, state={}, error={}", 
                tenantId, providerId, state, error);
        
        try {
            // 1. Check for OAuth errors
            if (error != null && !error.isBlank()) {
                String errorMsg = error_description != null ? error_description : error;
                log.error("OAuth error in callback: {} - {}", error, errorMsg);
                
                // Audit log
                auditService.logAction(tenantId, null, "verification.error",
                        Map.of("error", error, "description", errorMsg));
                
                return buildErrorRedirect("OAuth error: " + errorMsg);
            }
            
            // 2. Validate required parameters
            if (code == null || code.isBlank()) {
                throw new OAuthCallbackException("Authorization code is missing");
            }
            
            if (state == null || state.isBlank()) {
                throw new OAuthCallbackException("State token is missing");
            }
            
            // 3. Validate state token and get session
            VerificationSession session = sessionService.getSessionByState(state)
                    .orElseThrow(() -> new SessionExpiredException(
                        "Verification session not found or expired"));
            
            // Verify session belongs to the correct tenant and provider
            if (!session.getTenant().getId().equals(tenantId) ||
                !session.getProvider().getId().equals(providerId)) {
                throw new OAuthCallbackException(
                    "Session mismatch: tenant or provider doesn't match");
            }
            
            // Verify session is still pending
            if (!session.isPending()) {
                throw new OAuthCallbackException(
                    "Verification session is not pending: " + session.getStatus());
            }
            
            // 4. Get provider
            Provider provider = session.getProvider();
            
            // 5. Exchange authorization code for tokens using PKCE
            log.info("Exchanging authorization code for tokens");
            TokenResponse tokenResponse = oidcClient.exchangeCodeForTokens(
                    provider,
                    code,
                    session.getCodeVerifier()
            );
            
            // 6. Validate ID token
            log.info("Validating ID token");
            boolean isValid = oidcClient.validateIdToken(provider, tokenResponse.getIdToken());
            
            if (!isValid) {
                throw new InvalidIdTokenException("ID token validation failed");
            }
            
            // 7. Extract and normalize claims
            log.info("Extracting claims from ID token");
            Map<String, Object> rawClaims = oidcClient.extractClaims(tokenResponse.getIdToken());
            
            NormalizedClaims normalizedClaims = claimsNormalizer.normalize(rawClaims, provider);
            
            log.info("Claims normalized: subject={}, email={}", 
                    normalizedClaims.getSubject(), normalizedClaims.getEmail());
            
            // 8. Create or update user record
            log.info("Creating/updating user record");
            User user = userService.createVerifiedUser(
                    tenantId,
                    providerId,
                    normalizedClaims.getSubject(),
                    convertNormalizedClaimsToMap(normalizedClaims)
            );
            
            log.info("User verified: userId={}, subject={}", 
                    user.getId(), normalizedClaims.getSubject());
            
            // 9. Create UserPlatformMapping record
            log.info("Creating platform mapping: platform={}, platformUserId={}", 
                    session.getPlatformType(), session.getPlatformUserId());
            
            Map<String, Object> platformMetadata = new HashMap<>();
            if (normalizedClaims.getName() != null) {
                platformMetadata.put("username", normalizedClaims.getName());
            }
            
            UserPlatformMapping mapping = userService.linkPlatformAccount(
                    user.getId(),
                    session.getPlatformType(),
                    session.getPlatformUserId(),
                    platformMetadata
            );
            
            log.info("Platform mapping created: mappingId={}", mapping.getId());
            
            // 10. Complete session
            sessionService.completeSession(state, Map.of(
                    "userId", user.getId().toString(),
                    "subject", normalizedClaims.getSubject(),
                    "email", normalizedClaims.getEmail() != null ? normalizedClaims.getEmail() : ""
            ));
            
            log.info("Verification session completed successfully: sessionId={}", session.getId());
            
            // Audit log
            auditService.logAction(tenantId, user.getId(), "verification.completed",
                    Map.of(
                        "sessionId", session.getId().toString(),
                        "subject", normalizedClaims.getSubject(),
                        "platform", session.getPlatformType(),
                        "platformUserId", session.getPlatformUserId()
                    ));
            
            // 11. Redirect to success page
            return buildSuccessRedirect(user.getId());
            
        } catch (SessionExpiredException e) {
            log.error("Session expired: {}", e.getMessage());
            auditService.logAction(tenantId, null, "verification.session_expired",
                    Map.of("state", state != null ? state : ""));
            return buildErrorRedirect("Verification session expired. Please try again.");
            
        } catch (TokenExchangeException e) {
            log.error("Token exchange failed: {}", e.getMessage(), e);
            auditService.logAction(tenantId, null, "verification.token_exchange_failed",
                    Map.of("error", e.getMessage()));
            return buildErrorRedirect("Authentication failed. Please try again.");
            
        } catch (InvalidIdTokenException e) {
            log.error("ID token validation failed: {}", e.getMessage(), e);
            auditService.logAction(tenantId, null, "verification.invalid_token",
                    Map.of("error", e.getMessage()));
            return buildErrorRedirect("Authentication token is invalid. Please try again.");
            
        } catch (OAuthCallbackException e) {
            log.error("OAuth callback error: {}", e.getMessage());
            auditService.logAction(tenantId, null, "verification.callback_error",
                    Map.of("error", e.getMessage()));
            return buildErrorRedirect("Verification failed: " + e.getMessage());
            
        } catch (Exception e) {
            log.error("Unexpected error during OAuth callback: {}", e.getMessage(), e);
            auditService.logAction(tenantId, null, "verification.unexpected_error",
                    Map.of("error", e.getClass().getSimpleName(), "message", e.getMessage()));
            return buildErrorRedirect("An unexpected error occurred. Please try again.");
        }
    }
    
    // Helper methods
    
    private RedirectView buildSuccessRedirect(UUID userId) {
        String url = successRedirectUrl + "?user=" + userId.toString();
        log.debug("Redirecting to success page: {}", url);
        return new RedirectView(url);
    }
    
    private RedirectView buildErrorRedirect(String errorMessage) {
        String url = errorRedirectUrl + "?error=" + encodeUrl(errorMessage);
        log.debug("Redirecting to error page: {}", url);
        return new RedirectView(url);
    }
    
    private String encodeUrl(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }
    
    private Map<String, Object> convertNormalizedClaimsToMap(NormalizedClaims claims) {
        Map<String, Object> map = new HashMap<>();
        
        map.put("subject", claims.getSubject());
        
        if (claims.getEmail() != null) {
            map.put("email", claims.getEmail());
        }
        
        if (claims.getEmailDomain() != null) {
            map.put("email_domain", claims.getEmailDomain());
        }
        
        if (claims.getName() != null) {
            map.put("name", claims.getName());
        }
        
        if (claims.getGivenName() != null) {
            map.put("given_name", claims.getGivenName());
        }
        
        if (claims.getFamilyName() != null) {
            map.put("family_name", claims.getFamilyName());
        }
        
        if (claims.getAvatarUrl() != null) {
            map.put("picture", claims.getAvatarUrl());
        }
        
        if (claims.getVerifiedEmail() != null) {
            map.put("email_verified", claims.getVerifiedEmail());
        }
        
        if (claims.getGroups() != null && !claims.getGroups().isEmpty()) {
            map.put("groups", claims.getGroups());
        }
        
        if (claims.getLocale() != null) {
            map.put("locale", claims.getLocale());
        }
        
        // Include raw claims for completeness
        if (claims.getRawClaims() != null) {
            map.putAll(claims.getRawClaims());
        }
        
        return map;
    }
}
