package com.jtdev.authhooker.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jtdev.authhooker.domain.*;
import com.jtdev.authhooker.dto.InitiateVerificationRequest;
import com.jtdev.authhooker.dto.InitiateVerificationResponse;
import com.jtdev.authhooker.repository.*;
import com.jtdev.authhooker.service.EncryptionService;
import com.jtdev.authhooker.util.PkceUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AuthFlowController
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthFlowControllerIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private TenantRepository tenantRepository;
    
    @Autowired
    private ProviderRepository providerRepository;
    
    @Autowired
    private VerificationSessionRepository sessionRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PlatformIntegrationRepository platformIntegrationRepository;
    
    @Autowired
    private EncryptionService encryptionService;
    
    private Tenant testTenant;
    private Provider testProvider;
    private PlatformIntegration testPlatformIntegration;
    
    @BeforeEach
    void setUp() {
        // Create test tenant
        testTenant = Tenant.builder()
                .name("Test Tenant")
                .ownerEmail("owner@test.com")
                .planTier("free")
                .build();
        testTenant = tenantRepository.save(testTenant);
        
        // Create test provider
        testProvider = Provider.builder()
                .tenant(testTenant)
                .providerType("google")
                .name("Google Test")
                .clientId("test-client-id")
                .clientSecretEncrypted(encryptionService.encrypt("test-secret"))
                .config(Map.of(
                        "issuer", "https://accounts.google.com",
                        "authorization_endpoint", "https://accounts.google.com/o/oauth2/v2/auth",
                        "token_endpoint", "https://oauth2.googleapis.com/token",
                        "jwks_uri", "https://www.googleapis.com/oauth2/v3/certs",
                        "scopes", "openid profile email"
                ))
                .isActive(true)
                .isPrimary(true)
                .build();
        testProvider = providerRepository.save(testProvider);
        
        // Create test platform integration
        testPlatformIntegration = PlatformIntegration.builder()
                .tenant(testTenant)
                .platformType("discord")
                .platformId("test-discord-server")
                .config(Map.of("serverId", "123456789"))
                .isActive(true)
                .build();
        testPlatformIntegration = platformIntegrationRepository.save(testPlatformIntegration);
    }
    
    @Test
    void initiateVerification_shouldCreateSessionAndReturnAuthUrl() throws Exception {
        // Given
        InitiateVerificationRequest request = InitiateVerificationRequest.builder()
                .tenantId(testTenant.getId())
                .providerId(testProvider.getId())
                .platform("discord")
                .platformUserId("discord-user-123")
                .build();
        
        // When
        MvcResult result = mockMvc.perform(post("/api/v1/auth/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationUrl").exists())
                .andExpect(jsonPath("$.state").exists())
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.sessionId").exists())
                .andReturn();
        
        // Then
        String responseBody = result.getResponse().getContentAsString();
        InitiateVerificationResponse response = objectMapper.readValue(
                responseBody, InitiateVerificationResponse.class);
        
        assertNotNull(response.getVerificationUrl());
        assertTrue(response.getVerificationUrl().contains("accounts.google.com"));
        assertTrue(response.getVerificationUrl().contains("client_id=test-client-id"));
        assertTrue(response.getVerificationUrl().contains("state=" + response.getState()));
        assertTrue(response.getVerificationUrl().contains("code_challenge="));
        assertTrue(response.getVerificationUrl().contains("code_challenge_method=S256"));
        
        // Verify session was created
        VerificationSession session = sessionRepository.findByStateToken(response.getState())
                .orElseThrow();
        
        assertEquals(testTenant.getId(), session.getTenant().getId());
        assertEquals(testProvider.getId(), session.getProvider().getId());
        assertEquals("discord", session.getPlatformType());
        assertEquals("discord-user-123", session.getPlatformUserId());
        assertEquals("pending", session.getStatus());
        assertNotNull(session.getCodeVerifier());
        assertTrue(session.getExpiresAt().isAfter(LocalDateTime.now()));
        
        // Verify PKCE challenge matches verifier
        String codeChallenge = extractCodeChallengeFromUrl(response.getVerificationUrl());
        assertTrue(PkceUtil.verifyCodeChallenge(session.getCodeVerifier(), codeChallenge));
    }
    
    @Test
    void initiateVerification_shouldFailForInvalidProvider() throws Exception {
        // Given
        InitiateVerificationRequest request = InitiateVerificationRequest.builder()
                .tenantId(testTenant.getId())
                .providerId(UUID.randomUUID()) // Non-existent provider
                .platform("discord")
                .platformUserId("discord-user-123")
                .build();
        
        // When/Then
        mockMvc.perform(post("/api/v1/auth/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }
    
    @Test
    void initiateVerification_shouldFailForMismatchedTenant() throws Exception {
        // Given - Create provider for different tenant
        Tenant otherTenant = Tenant.builder()
                .name("Other Tenant")
                .ownerEmail("other@test.com")
                .planTier("free")
                .build();
        otherTenant = tenantRepository.save(otherTenant);
        
        Provider otherProvider = Provider.builder()
                .tenant(otherTenant)
                .providerType("google")
                .name("Other Google")
                .clientId("other-client-id")
                .clientSecretEncrypted(encryptionService.encrypt("other-secret"))
                .config(Map.of(
                        "authorization_endpoint", "https://accounts.google.com/o/oauth2/v2/auth",
                        "token_endpoint", "https://oauth2.googleapis.com/token"
                ))
                .isActive(true)
                .build();
        otherProvider = providerRepository.save(otherProvider);
        
        InitiateVerificationRequest request = InitiateVerificationRequest.builder()
                .tenantId(testTenant.getId()) // Different tenant
                .providerId(otherProvider.getId())
                .platform("discord")
                .platformUserId("discord-user-123")
                .build();
        
        // When/Then
        mockMvc.perform(post("/api/v1/auth/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ValidationError"));
    }
    
    @Test
    void initiateVerification_shouldFailForInactiveProvider() throws Exception {
        // Given - Make provider inactive
        testProvider.setIsActive(false);
        providerRepository.save(testProvider);
        
        InitiateVerificationRequest request = InitiateVerificationRequest.builder()
                .tenantId(testTenant.getId())
                .providerId(testProvider.getId())
                .platform("discord")
                .platformUserId("discord-user-123")
                .build();
        
        // When/Then
        mockMvc.perform(post("/api/v1/auth/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ValidationError"));
    }
    
    @Test
    void initiateVerification_shouldFailForMissingRequiredFields() throws Exception {
        // Given - Missing tenantId
        String invalidRequest = "{\"providerId\":\"" + testProvider.getId() + "\","
                + "\"platform\":\"discord\",\"platformUserId\":\"user123\"}";
        
        // When/Then
        mockMvc.perform(post("/api/v1/auth/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void handleCallback_shouldFailForMissingCode() throws Exception {
        // Given
        VerificationSession session = createTestSession();
        
        // When/Then
        mockMvc.perform(get("/oauth/callback/{tenantId}/{providerId}", 
                        testTenant.getId(), testProvider.getId())
                        .param("state", session.getStateToken()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/error?error=*"));
    }
    
    @Test
    void handleCallback_shouldFailForInvalidState() throws Exception {
        // When/Then
        mockMvc.perform(get("/oauth/callback/{tenantId}/{providerId}", 
                        testTenant.getId(), testProvider.getId())
                        .param("code", "test-auth-code")
                        .param("state", "invalid-state-token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/error?error=*"));
    }
    
    @Test
    void handleCallback_shouldFailForExpiredSession() throws Exception {
        // Given - Create expired session
        VerificationSession session = VerificationSession.builder()
                .tenant(testTenant)
                .provider(testProvider)
                .stateToken("expired-state")
                .codeVerifier(PkceUtil.generateCodeVerifier())
                .platformType("discord")
                .platformUserId("user-123")
                .status("pending")
                .expiresAt(LocalDateTime.now().minusMinutes(5)) // Expired
                .build();
        sessionRepository.save(session);
        
        // When/Then
        mockMvc.perform(get("/oauth/callback/{tenantId}/{providerId}", 
                        testTenant.getId(), testProvider.getId())
                        .param("code", "test-auth-code")
                        .param("state", "expired-state"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/error?error=*"));
    }
    
    @Test
    void handleCallback_shouldHandleOAuthError() throws Exception {
        // When/Then
        mockMvc.perform(get("/oauth/callback/{tenantId}/{providerId}", 
                        testTenant.getId(), testProvider.getId())
                        .param("error", "access_denied")
                        .param("error_description", "User cancelled the request")
                        .param("state", "some-state"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/error?error=*User*cancelled*"));
    }
    
    // Helper methods
    
    private VerificationSession createTestSession() {
        VerificationSession session = VerificationSession.builder()
                .tenant(testTenant)
                .provider(testProvider)
                .stateToken(UUID.randomUUID().toString())
                .codeVerifier(PkceUtil.generateCodeVerifier())
                .platformType("discord")
                .platformUserId("discord-user-123")
                .status("pending")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        
        return sessionRepository.save(session);
    }
    
    private String extractCodeChallengeFromUrl(String url) {
        // Extract code_challenge parameter from URL
        String[] parts = url.split("code_challenge=");
        if (parts.length < 2) {
            return null;
        }
        
        String challengePart = parts[1];
        int ampersandIndex = challengePart.indexOf('&');
        
        if (ampersandIndex > 0) {
            return challengePart.substring(0, ampersandIndex);
        }
        
        return challengePart;
    }
}
