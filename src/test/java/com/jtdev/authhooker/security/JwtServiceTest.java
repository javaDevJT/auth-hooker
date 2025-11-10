package com.jtdev.authhooker.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for JwtService
 */
class JwtServiceTest {
    
    private JwtService jwtService;
    private UUID testTenantId;
    private String testEmail;
    
    @BeforeEach
    void setUp() {
        jwtService = new JwtService("test-secret-key-minimum-32-characters-long", 24);
        testTenantId = UUID.randomUUID();
        testEmail = "test@example.com";
    }
    
    @Test
    void shouldGenerateToken() {
        // When
        String token = jwtService.generateToken(testTenantId, testEmail, null);
        
        // Then
        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
    }
    
    @Test
    void shouldValidateToken() {
        // Given
        String token = jwtService.generateToken(testTenantId, testEmail, null);
        
        // When
        boolean isValid = jwtService.validateToken(token);
        
        // Then
        assertThat(isValid).isTrue();
    }
    
    @Test
    void shouldRejectInvalidToken() {
        // Given
        String invalidToken = "invalid.token.here";
        
        // When
        boolean isValid = jwtService.validateToken(invalidToken);
        
        // Then
        assertThat(isValid).isFalse();
    }
    
    @Test
    void shouldExtractTenantId() {
        // Given
        String token = jwtService.generateToken(testTenantId, testEmail, null);
        
        // When
        UUID extractedTenantId = jwtService.extractTenantId(token);
        
        // Then
        assertThat(extractedTenantId).isEqualTo(testTenantId);
    }
    
    @Test
    void shouldExtractEmail() {
        // Given
        String token = jwtService.generateToken(testTenantId, testEmail, null);
        
        // When
        String extractedEmail = jwtService.extractEmail(token);
        
        // Then
        assertThat(extractedEmail).isEqualTo(testEmail);
    }
    
    @Test
    void shouldIncludeAdditionalClaims() {
        // Given
        Map<String, Object> additionalClaims = new HashMap<>();
        additionalClaims.put("custom_claim", "custom_value");
        additionalClaims.put("user_role", "admin");
        
        // When
        String token = jwtService.generateToken(testTenantId, testEmail, additionalClaims);
        Claims claims = jwtService.extractAllClaims(token);
        
        // Then
        assertThat(claims.get("custom_claim")).isEqualTo("custom_value");
        assertThat(claims.get("user_role")).isEqualTo("admin");
    }
    
    @Test
    void shouldNotBeExpired() {
        // Given
        String token = jwtService.generateToken(testTenantId, testEmail, null);
        
        // When
        boolean isExpired = jwtService.isTokenExpired(token);
        
        // Then
        assertThat(isExpired).isFalse();
    }
}
