package com.jtdev.authhooker.service;

import com.jtdev.authhooker.domain.Provider;
import com.jtdev.authhooker.domain.Tenant;
import com.jtdev.authhooker.dto.NormalizedClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ClaimsNormalizer
 */
@ExtendWith(MockitoExtension.class)
class ClaimsNormalizerTest {
    
    @Mock
    private ClaimMappingService claimMappingService;
    
    @InjectMocks
    private ClaimsNormalizer claimsNormalizer;
    
    private Provider googleProvider;
    private Provider githubProvider;
    
    @BeforeEach
    void setUp() {
        Tenant tenant = Tenant.builder()
                .id(UUID.randomUUID())
                .name("Test Tenant")
                .build();
        
        googleProvider = Provider.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .providerType("google")
                .name("Google")
                .build();
        
        githubProvider = Provider.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .providerType("github")
                .name("GitHub")
                .build();
    }
    
    @Test
    void normalize_shouldExtractStandardOidcClaims() {
        // Given
        Map<String, Object> rawClaims = Map.of(
                "sub", "123456789",
                "email", "user@example.com",
                "email_verified", true,
                "name", "John Doe",
                "given_name", "John",
                "family_name", "Doe",
                "picture", "https://example.com/avatar.jpg",
                "locale", "en-US"
        );
        
        // When
        NormalizedClaims result = claimsNormalizer.normalize(rawClaims, googleProvider);
        
        // Then
        assertEquals("123456789", result.getSubject());
        assertEquals("user@example.com", result.getEmail());
        assertEquals("example.com", result.getEmailDomain());
        assertEquals("John Doe", result.getName());
        assertEquals("John", result.getGivenName());
        assertEquals("Doe", result.getFamilyName());
        assertEquals("https://example.com/avatar.jpg", result.getAvatarUrl());
        assertTrue(result.getVerifiedEmail());
        assertEquals("en-US", result.getLocale());
    }
    
    @Test
    void normalize_shouldExtractEmailDomain() {
        // Given
        Map<String, Object> rawClaims = Map.of(
                "sub", "123",
                "email", "admin@company.com"
        );
        
        // When
        NormalizedClaims result = claimsNormalizer.normalize(rawClaims, googleProvider);
        
        // Then
        assertEquals("company.com", result.getEmailDomain());
    }
    
    @Test
    void normalize_shouldExtractGroups() {
        // Given
        Map<String, Object> rawClaims = Map.of(
                "sub", "123",
                "email", "user@example.com",
                "groups", List.of("admin", "developers", "managers")
        );
        
        // When
        NormalizedClaims result = claimsNormalizer.normalize(rawClaims, googleProvider);
        
        // Then
        assertNotNull(result.getGroups());
        assertEquals(3, result.getGroups().size());
        assertTrue(result.getGroups().contains("admin"));
        assertTrue(result.getGroups().contains("developers"));
        assertTrue(result.getGroups().contains("managers"));
    }
    
    @Test
    void normalize_shouldHandleGitHubSpecificClaims() {
        // Given
        Map<String, Object> rawClaims = Map.of(
                "id", "987654321",
                "login", "johndoe",
                "email", "john@example.com",
                "avatar_url", "https://github.com/avatar.jpg"
        );
        
        // When
        NormalizedClaims result = claimsNormalizer.normalize(rawClaims, githubProvider);
        
        // Then
        assertEquals("987654321", result.getSubject());
        assertEquals("john@example.com", result.getEmail());
        assertEquals("johndoe", result.getName());
        assertEquals("https://github.com/avatar.jpg", result.getAvatarUrl());
        assertTrue(result.getVerifiedEmail()); // GitHub emails are verified
    }
    
    @Test
    void normalize_shouldHandleMissingOptionalFields() {
        // Given
        Map<String, Object> rawClaims = Map.of(
                "sub", "123456789"
        );
        
        // When
        NormalizedClaims result = claimsNormalizer.normalize(rawClaims, googleProvider);
        
        // Then
        assertEquals("123456789", result.getSubject());
        assertNull(result.getEmail());
        assertNull(result.getEmailDomain());
        assertNull(result.getName());
        assertNull(result.getAvatarUrl());
        assertNotNull(result.getGroups());
        assertTrue(result.getGroups().isEmpty());
    }
    
    @Test
    void normalize_shouldThrowOnNullClaims() {
        // When/Then
        assertThrows(IllegalArgumentException.class,
                () -> claimsNormalizer.normalize(null, googleProvider));
    }
    
    @Test
    void normalize_shouldThrowOnEmptyClaims() {
        // When/Then
        assertThrows(IllegalArgumentException.class,
                () -> claimsNormalizer.normalize(Map.of(), googleProvider));
    }
    
    @Test
    void extractEmailDomain_shouldExtractCorrectDomain() {
        // When/Then
        assertEquals("gmail.com",
                claimsNormalizer.extractEmailDomain("user@gmail.com"));
        assertEquals("company.co.uk",
                claimsNormalizer.extractEmailDomain("admin@company.co.uk"));
        assertEquals("subdomain.example.com",
                claimsNormalizer.extractEmailDomain("test@subdomain.example.com"));
    }
    
    @Test
    void extractEmailDomain_shouldReturnNullForInvalidEmail() {
        // When/Then
        assertNull(claimsNormalizer.extractEmailDomain(null));
        assertNull(claimsNormalizer.extractEmailDomain(""));
        assertNull(claimsNormalizer.extractEmailDomain("   "));
        assertNull(claimsNormalizer.extractEmailDomain("invalidemail"));
        assertNull(claimsNormalizer.extractEmailDomain("@nodomain"));
        assertNull(claimsNormalizer.extractEmailDomain("user@"));
    }
    
    @Test
    void extractGroups_shouldExtractFromStandardGroupsClaim() {
        // Given
        Map<String, Object> claims = Map.of(
                "groups", List.of("group1", "group2", "group3")
        );
        
        // When
        List<String> groups = claimsNormalizer.extractGroups(claims, googleProvider);
        
        // Then
        assertEquals(3, groups.size());
        assertTrue(groups.contains("group1"));
        assertTrue(groups.contains("group2"));
        assertTrue(groups.contains("group3"));
    }
    
    @Test
    void extractGroups_shouldExtractFromRolesClaim() {
        // Given
        Provider azureProvider = Provider.builder()
                .providerType("microsoft")
                .build();
        
        Map<String, Object> claims = Map.of(
                "groups", List.of("group1"),
                "roles", List.of("role1", "role2")
        );
        
        // When
        List<String> groups = claimsNormalizer.extractGroups(claims, azureProvider);
        
        // Then
        assertTrue(groups.size() >= 2);
        assertTrue(groups.contains("group1"));
        assertTrue(groups.contains("role1"));
        assertTrue(groups.contains("role2"));
    }
    
    @Test
    void extractGroups_shouldReturnEmptyListWhenNoGroups() {
        // Given
        Map<String, Object> claims = Map.of("sub", "123");
        
        // When
        List<String> groups = claimsNormalizer.extractGroups(claims, googleProvider);
        
        // Then
        assertNotNull(groups);
        assertTrue(groups.isEmpty());
    }
}
