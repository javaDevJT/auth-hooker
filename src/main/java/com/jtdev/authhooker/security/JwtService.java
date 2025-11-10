package com.jtdev.authhooker.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for JWT token generation and validation
 */
@Slf4j
@Service
public class JwtService {
    
    private final SecretKey secretKey;
    private final long expirationHours;
    
    public JwtService(
            @Value("${app.security.jwt-secret}") String secret,
            @Value("${app.security.jwt-expiration-hours:24}") long expirationHours) {
        
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret cannot be null or empty. Set app.security.jwt-secret property.");
        }
        
        // Ensure secret is at least 256 bits (32 bytes)
        if (secret.length() < 32) {
            log.warn("JWT secret is too short. Padding to minimum length.");
            secret = String.format("%-32s", secret);
        }
        
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationHours = expirationHours;
        
        log.info("JwtService initialized with expiration: {} hours", expirationHours);
    }
    
    /**
     * Generate a JWT token for a tenant
     */
    public String generateToken(UUID tenantId, String ownerEmail, Map<String, Object> additionalClaims) {
        log.debug("Generating JWT token for tenant: {}", tenantId);
        
        Map<String, Object> claims = new HashMap<>();
        if (additionalClaims != null) {
            claims.putAll(additionalClaims);
        }
        
        // Add standard claims
        claims.put("tenantId", tenantId.toString());
        claims.put("email", ownerEmail);
        
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(expirationHours * 3600);
        
        String token = Jwts.builder()
                .claims(claims)
                .subject(ownerEmail)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(secretKey)
                .compact();
        
        log.debug("JWT token generated successfully for tenant: {}", tenantId);
        return token;
    }
    
    /**
     * Validate a JWT token
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            log.debug("JWT token validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Extract tenant ID from token
     */
    public UUID extractTenantId(String token) {
        String tenantIdStr = extractClaim(token, "tenantId");
        return tenantIdStr != null ? UUID.fromString(tenantIdStr) : null;
    }
    
    /**
     * Extract email from token
     */
    public String extractEmail(String token) {
        return extractClaim(token, "email");
    }
    
    /**
     * Extract all claims from token
     */
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    
    /**
     * Extract a specific claim from token
     */
    public String extractClaim(String token, String claimName) {
        Claims claims = extractAllClaims(token);
        Object claim = claims.get(claimName);
        return claim != null ? claim.toString() : null;
    }
    
    /**
     * Check if token is expired
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
}
