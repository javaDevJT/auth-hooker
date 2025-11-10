package com.jtdev.authhooker.api;

import com.jtdev.authhooker.domain.Tenant;
import com.jtdev.authhooker.repository.TenantRepository;
import com.jtdev.authhooker.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for authentication and authorization
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private JwtService jwtService;
    
    @Autowired
    private TenantRepository tenantRepository;
    
    private Tenant testTenant;
    private String jwtToken;
    private String apiKey;
    
    @BeforeEach
    void setUp() {
        // Create test tenant
        testTenant = Tenant.builder()
                .name("Test Tenant")
                .ownerEmail("test@example.com")
                .planTier("free")
                .maxVerifiedUsers(50)
                .status("active")
                .build();
        testTenant = tenantRepository.save(testTenant);
        
        // Generate JWT token
        jwtToken = jwtService.generateToken(testTenant.getId(), testTenant.getOwnerEmail(), null);
        
        // Generate API key and save in tenant settings
        apiKey = "test-api-key-" + UUID.randomUUID();
        testTenant.setSettings(Map.of("api_key", apiKey));
        testTenant = tenantRepository.save(testTenant);
    }
    
    @Test
    void shouldAllowAccessToPublicHealthEndpoint() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
    
    @Test
    void shouldRequireAuthenticationForProtectedEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/tenant"))
                .andExpect(status().isUnauthorized());
    }
    
    @Test
    void shouldAuthenticateWithValidJWT() throws Exception {
        mockMvc.perform(get("/api/v1/tenant")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testTenant.getId().toString()))
                .andExpect(jsonPath("$.name").value("Test Tenant"));
    }
    
    @Test
    void shouldRejectInvalidJWT() throws Exception {
        mockMvc.perform(get("/api/v1/tenant")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized());
    }
    
    @Test
    void shouldAuthenticateWithValidApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/tenant")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testTenant.getId().toString()));
    }
    
    @Test
    void shouldRejectInvalidApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/tenant")
                        .header("X-API-Key", "invalid-api-key"))
                .andExpect(status().isUnauthorized());
    }
    
    @Test
    void shouldHandleCorsPreflightRequest() throws Exception {
        mockMvc.perform(options("/api/v1/tenant")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Origin"));
    }
}
