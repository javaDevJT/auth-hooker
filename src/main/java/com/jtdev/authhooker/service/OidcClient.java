package com.jtdev.authhooker.service;

import com.jtdev.authhooker.domain.Provider;
import com.jtdev.authhooker.dto.OidcConfiguration;
import com.jtdev.authhooker.dto.TokenResponse;
import com.jtdev.authhooker.exception.InvalidIdTokenException;
import com.jtdev.authhooker.exception.TokenExchangeException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.*;

/**
 * Service for OIDC (OpenID Connect) operations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OidcClient {
    
    private final WebClient.Builder webClientBuilder;
    private final EncryptionService encryptionService;
    
    @Value("${app.oauth.callback-base-url:https://auth.javadevjt.tech}")
    private String callbackBaseUrl;
    
    @Value("${app.oauth.timeout-seconds:10}")
    private int timeoutSeconds;
    
    // Cache for JWKS keys (provider ID -> public keys)
    private final Map<UUID, Map<String, PublicKey>> jwksCache = new HashMap<>();
    
    // Cache for discovery documents (issuer -> config)
    private final Map<String, OidcConfiguration> discoveryCache = new HashMap<>();
    
    /**
     * Build OAuth authorization URL with PKCE
     * 
     * @param provider The OIDC provider config
     * @param state Random state token for CSRF protection
     * @param codeChallenge PKCE code challenge (SHA-256 hash of verifier)
     * @return Complete authorization URL
     */
    public String buildAuthorizationUrl(Provider provider, String state, String codeChallenge) {
        log.debug("Building authorization URL for provider: {}", provider.getId());
        
        Map<String, Object> config = provider.getConfig();
        
        // Get authorization endpoint
        String authEndpoint = (String) config.get("authorization_endpoint");
        if (authEndpoint == null || authEndpoint.isBlank()) {
            throw new IllegalArgumentException(
                "Provider configuration missing 'authorization_endpoint': " + provider.getId());
        }
        
        // Build redirect URI
        String redirectUri = String.format("%s/oauth/callback/%s/%s",
                callbackBaseUrl,
                provider.getTenant().getId(),
                provider.getId());
        
        // Get scopes (default to "openid profile email")
        String scopes = (String) config.getOrDefault("scopes", "openid profile email");
        
        // Build authorization URL
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(authEndpoint)
                .queryParam("client_id", provider.getClientId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", scopes)
                .queryParam("state", state)
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256");
        
        // Add nonce if supported (OIDC)
        if (scopes.contains("openid")) {
            // Nonce will be stored in session and validated in ID token
            builder.queryParam("nonce", UUID.randomUUID().toString());
        }
        
        // Add any additional parameters from config
        @SuppressWarnings("unchecked")
        Map<String, String> additionalParams = 
            (Map<String, String>) config.get("additional_auth_params");
        
        if (additionalParams != null) {
            additionalParams.forEach(builder::queryParam);
        }
        
        String authUrl = builder.build().toUriString();
        log.debug("Authorization URL built: {}", authUrl);
        
        return authUrl;
    }
    
    /**
     * Exchange authorization code for tokens
     * 
     * @param provider The OIDC provider
     * @param code Authorization code from callback
     * @param codeVerifier PKCE code verifier
     * @return Token response with id_token, access_token, refresh_token
     */
    public TokenResponse exchangeCodeForTokens(Provider provider, String code, String codeVerifier) {
        log.info("Exchanging authorization code for tokens: provider={}", provider.getId());
        
        Map<String, Object> config = provider.getConfig();
        
        // Get token endpoint
        String tokenEndpoint = (String) config.get("token_endpoint");
        if (tokenEndpoint == null || tokenEndpoint.isBlank()) {
            throw new IllegalArgumentException(
                "Provider configuration missing 'token_endpoint': " + provider.getId());
        }
        
        // Build redirect URI (must match the one in authorization request)
        String redirectUri = String.format("%s/oauth/callback/%s/%s",
                callbackBaseUrl,
                provider.getTenant().getId(),
                provider.getId());
        
        // Decrypt client secret
        String clientSecret = encryptionService.decrypt(provider.getClientSecretEncrypted());
        
        // Build request body
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("code", code);
        formData.add("redirect_uri", redirectUri);
        formData.add("client_id", provider.getClientId());
        formData.add("client_secret", clientSecret);
        formData.add("code_verifier", codeVerifier);
        
        // Make token exchange request
        WebClient webClient = webClientBuilder.build();
        
        try {
            TokenResponse tokenResponse = webClient.post()
                    .uri(tokenEndpoint)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .bodyToMono(TokenResponse.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .onErrorResume(e -> {
                        log.error("Token exchange failed for provider {}: {}", 
                                provider.getId(), e.getMessage());
                        return Mono.error(new TokenExchangeException(
                            "Failed to exchange authorization code for tokens: " + e.getMessage(), e));
                    })
                    .block();
            
            if (tokenResponse == null || tokenResponse.getIdToken() == null) {
                throw new TokenExchangeException("Token response is null or missing ID token");
            }
            
            log.info("Token exchange successful for provider: {}", provider.getId());
            return tokenResponse;
            
        } catch (TokenExchangeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during token exchange for provider {}: {}", 
                    provider.getId(), e.getMessage(), e);
            throw new TokenExchangeException(
                "Unexpected error during token exchange: " + e.getMessage(), e);
        }
    }
    
    /**
     * Validate ID token using JWKS
     * 
     * @param provider The OIDC provider
     * @param idToken JWT ID token from token response
     * @return true if valid, false otherwise
     */
    public boolean validateIdToken(Provider provider, String idToken) {
        log.debug("Validating ID token for provider: {}", provider.getId());
        
        try {
            Map<String, Object> config = provider.getConfig();
            
            // Get JWKS URI
            String jwksUri = (String) config.get("jwks_uri");
            if (jwksUri == null || jwksUri.isBlank()) {
                log.warn("Provider {} missing jwks_uri, skipping signature verification", 
                        provider.getId());
                // For providers without JWKS (like GitHub), skip signature validation
                // but still parse and validate claims
                return validateIdTokenClaims(provider, idToken);
            }
            
            // Fetch JWKS if not cached
            Map<String, PublicKey> publicKeys = getOrFetchJwks(provider.getId(), jwksUri);
            
            // Parse JWT header to get key ID (kid)
            String[] parts = idToken.split("\\.");
            if (parts.length != 3) {
                throw new InvalidIdTokenException("Invalid JWT format");
            }
            
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
            @SuppressWarnings("unchecked")
            Map<String, Object> header = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(headerJson, Map.class);
            
            String kid = (String) header.get("kid");
            
            // Get the public key for this kid
            PublicKey publicKey = publicKeys.get(kid);
            if (publicKey == null) {
                log.warn("Public key not found for kid: {}, refreshing JWKS", kid);
                // Refresh JWKS cache and try again
                publicKeys = fetchJwks(provider.getId(), jwksUri);
                publicKey = publicKeys.get(kid);
                
                if (publicKey == null) {
                    throw new InvalidIdTokenException("Public key not found for kid: " + kid);
                }
            }
            
            // Verify JWT signature and parse claims
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(idToken)
                    .getPayload();
            
            // Validate claims
            return validateClaims(provider, claims);
            
        } catch (SignatureException e) {
            log.error("ID token signature validation failed for provider {}: {}", 
                    provider.getId(), e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("ID token validation failed for provider {}: {}", 
                    provider.getId(), e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Extract claims from ID token
     * 
     * @param idToken JWT ID token
     * @return Map of all claims
     */
    public Map<String, Object> extractClaims(String idToken) {
        try {
            // Parse JWT without verification (already validated)
            String[] parts = idToken.split("\\.");
            if (parts.length != 3) {
                throw new InvalidIdTokenException("Invalid JWT format");
            }
            
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
            
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(payloadJson, Map.class);
            
            log.debug("Extracted {} claims from ID token", claims.size());
            return claims;
            
        } catch (Exception e) {
            log.error("Failed to extract claims from ID token: {}", e.getMessage(), e);
            throw new InvalidIdTokenException("Failed to extract claims: " + e.getMessage(), e);
        }
    }
    
    /**
     * Discover OIDC configuration
     * 
     * @param issuerUri Base issuer URL
     * @return Discovery document with endpoints
     */
    public OidcConfiguration discoverConfiguration(String issuerUri) {
        log.info("Discovering OIDC configuration for issuer: {}", issuerUri);
        
        // Check cache
        if (discoveryCache.containsKey(issuerUri)) {
            log.debug("Returning cached discovery document for: {}", issuerUri);
            return discoveryCache.get(issuerUri);
        }
        
        // Build discovery URL
        String discoveryUrl = issuerUri.endsWith("/")
                ? issuerUri + ".well-known/openid-configuration"
                : issuerUri + "/.well-known/openid-configuration";
        
        // Fetch discovery document
        WebClient webClient = webClientBuilder.build();
        
        try {
            OidcConfiguration config = webClient.get()
                    .uri(discoveryUrl)
                    .retrieve()
                    .bodyToMono(OidcConfiguration.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .onErrorResume(e -> {
                        log.error("Failed to fetch OIDC discovery document from {}: {}", 
                                discoveryUrl, e.getMessage());
                        return Mono.empty();
                    })
                    .block();
            
            if (config == null) {
                throw new IllegalArgumentException(
                    "Failed to retrieve OIDC discovery document from: " + discoveryUrl);
            }
            
            // Validate required fields
            if (config.getAuthorizationEndpoint() == null || 
                config.getTokenEndpoint() == null) {
                throw new IllegalArgumentException(
                    "Invalid OIDC discovery document: missing required endpoints");
            }
            
            // Cache the configuration
            discoveryCache.put(issuerUri, config);
            
            log.info("OIDC configuration discovered successfully for: {}", issuerUri);
            return config;
            
        } catch (Exception e) {
            log.error("Failed to discover OIDC configuration for {}: {}", 
                    issuerUri, e.getMessage(), e);
            throw new IllegalArgumentException(
                "Failed to discover OIDC configuration: " + e.getMessage(), e);
        }
    }
    
    /**
     * Clear JWKS cache for a provider (useful for key rotation)
     */
    public void clearJwksCache(UUID providerId) {
        jwksCache.remove(providerId);
        log.info("JWKS cache cleared for provider: {}", providerId);
    }
    
    /**
     * Clear discovery cache for an issuer
     */
    public void clearDiscoveryCache(String issuer) {
        discoveryCache.remove(issuer);
        log.info("Discovery cache cleared for issuer: {}", issuer);
    }
    
    // Private helper methods
    
    private Map<String, PublicKey> getOrFetchJwks(UUID providerId, String jwksUri) {
        if (jwksCache.containsKey(providerId)) {
            return jwksCache.get(providerId);
        }
        return fetchJwks(providerId, jwksUri);
    }
    
    private Map<String, PublicKey> fetchJwks(UUID providerId, String jwksUri) {
        log.info("Fetching JWKS for provider {} from: {}", providerId, jwksUri);
        
        WebClient webClient = webClientBuilder.build();
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> jwks = webClient.get()
                    .uri(jwksUri)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();
            
            if (jwks == null || !jwks.containsKey("keys")) {
                throw new InvalidIdTokenException("Invalid JWKS response");
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> keys = (List<Map<String, Object>>) jwks.get("keys");
            
            Map<String, PublicKey> publicKeys = new HashMap<>();
            
            for (Map<String, Object> key : keys) {
                String kid = (String) key.get("kid");
                String kty = (String) key.get("kty");
                
                if (!"RSA".equals(kty)) {
                    log.debug("Skipping non-RSA key: {}", kid);
                    continue;
                }
                
                String n = (String) key.get("n");
                String e = (String) key.get("e");
                
                // Convert base64url to BigInteger
                BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(n));
                BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(e));
                
                // Create RSA public key
                RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
                KeyFactory factory = KeyFactory.getInstance("RSA");
                PublicKey publicKey = factory.generatePublic(spec);
                
                publicKeys.put(kid, publicKey);
                log.debug("Loaded public key: {}", kid);
            }
            
            // Cache the keys
            jwksCache.put(providerId, publicKeys);
            
            log.info("Fetched and cached {} JWKS keys for provider: {}", 
                    publicKeys.size(), providerId);
            
            return publicKeys;
            
        } catch (Exception e) {
            log.error("Failed to fetch JWKS for provider {}: {}", providerId, e.getMessage(), e);
            throw new InvalidIdTokenException("Failed to fetch JWKS: " + e.getMessage(), e);
        }
    }
    
    private boolean validateClaims(Provider provider, Claims claims) {
        Map<String, Object> config = provider.getConfig();
        
        // Validate issuer
        String expectedIssuer = (String) config.get("issuer");
        if (expectedIssuer != null && !expectedIssuer.equals(claims.getIssuer())) {
            log.error("Invalid issuer: expected={}, actual={}", 
                    expectedIssuer, claims.getIssuer());
            return false;
        }
        
        // Validate audience
        String expectedAudience = provider.getClientId();
        Object audience = claims.get("aud");
        
        boolean audienceValid = false;
        if (audience instanceof String) {
            audienceValid = expectedAudience.equals(audience);
        } else if (audience instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> audiences = (List<String>) audience;
            audienceValid = audiences.contains(expectedAudience);
        }
        
        if (!audienceValid) {
            log.error("Invalid audience: expected={}, actual={}", expectedAudience, audience);
            return false;
        }
        
        // Validate expiration
        Date expiration = claims.getExpiration();
        if (expiration != null && expiration.before(new Date())) {
            log.error("ID token expired: {}", expiration);
            return false;
        }
        
        // Validate issued time (not in the future)
        Date issuedAt = claims.getIssuedAt();
        if (issuedAt != null && issuedAt.after(new Date())) {
            log.error("ID token issued in the future: {}", issuedAt);
            return false;
        }
        
        log.debug("ID token claims validation successful");
        return true;
    }
    
    private boolean validateIdTokenClaims(Provider provider, String idToken) {
        try {
            Map<String, Object> claims = extractClaims(idToken);
            
            // Basic validation without signature verification
            Map<String, Object> config = provider.getConfig();
            
            // Validate issuer if configured
            String expectedIssuer = (String) config.get("issuer");
            if (expectedIssuer != null && !expectedIssuer.equals(claims.get("iss"))) {
                log.error("Invalid issuer in claims");
                return false;
            }
            
            // Validate expiration
            Object exp = claims.get("exp");
            if (exp instanceof Number) {
                long expirationTimestamp = ((Number) exp).longValue();
                if (expirationTimestamp < System.currentTimeMillis() / 1000) {
                    log.error("ID token expired");
                    return false;
                }
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Failed to validate ID token claims: {}", e.getMessage());
            return false;
        }
    }
}
