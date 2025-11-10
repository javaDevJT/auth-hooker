package com.jtdev.authhooker.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jtdev.authhooker.domain.Tenant;
import com.jtdev.authhooker.dto.TenantUpdateRequest;
import com.jtdev.authhooker.repository.TenantRepository;
import com.jtdev.authhooker.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for TenantController
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TenantControllerIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private JwtService jwtService;
    
    @Autowired
    private TenantRepository tenantRepository;
    
    private Tenant testTenant;
    private String jwtToken;
    
    @BeforeEach
    void setUp() {
        // Create test tenant
        testTenant = Tenant.builder()
                .name("Test Tenant")
                .ownerEmail("test@example.com")
                .planTier("professional")
                .maxVerifiedUsers(5000)
                .status("active")
                .settings(Map.of("feature_enabled", true))
                .build();
        testTenant = tenantRepository.save(testTenant);
        
        // Generate JWT token
        jwtToken = jwtService.generateToken(testTenant.getId(), testTenant.getOwnerEmail(), null);
    }
    
    @Test
    void shouldGetCurrentTenant() throws Exception {
        mockMvc.perform(get("/api/v1/tenant")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testTenant.getId().toString()))
                .andExpect(jsonPath("$.name").value("Test Tenant"))
                .andExpect(jsonPath("$.ownerEmail").value("test@example.com"))
                .andExpect(jsonPath("$.planTier").value("professional"))
                .andExpect(jsonPath("$.maxVerifiedUsers").value(5000))
                .andExpect(jsonPath("$.status").value("active"));
    }
    
    @Test
    void shouldUpdateTenantSettings() throws Exception {
        // Given
        TenantUpdateRequest request = new TenantUpdateRequest();
        request.setSettings(Map.of(
                "feature_enabled", false,
                "new_setting", "value"
        ));
        
        // When/Then
        mockMvc.perform(put("/api/v1/tenant")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.feature_enabled").value(false))
                .andExpect(jsonPath("$.settings.new_setting").value("value"));
    }
    
    @Test
    void shouldGetTenantStats() throws Exception {
        mockMvc.perform(get("/api/v1/tenant/stats")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxVerifiedUsers").value(5000))
                .andExpect(jsonPath("$.totalUsers").isNumber())
                .andExpect(jsonPath("$.activeUsers").isNumber());
    }
    
    @Test
    void shouldRotateApiKey() throws Exception {
        mockMvc.perform(post("/api/v1/tenant/api-key/rotate")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKey").isString())
                .andExpect(jsonPath("$.apiKey").isNotEmpty())
                .andExpect(jsonPath("$.message").value(containsString("rotated successfully")));
    }
    
    @Test
    void shouldRequireAdminRoleForSensitiveOperations() throws Exception {
        // API key authentication should not have admin role for sensitive operations
        String apiKey = "test-api-key";
        testTenant.setSettings(Map.of("api_key", apiKey));
        tenantRepository.save(testTenant);
        
        mockMvc.perform(post("/api/v1/tenant/api-key/rotate")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isForbidden());
    }
}
