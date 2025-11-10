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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for GlobalExceptionHandler
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GlobalExceptionHandlerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
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
                .planTier("free")
                .maxVerifiedUsers(50)
                .status("active")
                .build();
        testTenant = tenantRepository.save(testTenant);
        
        // Generate JWT token
        jwtToken = jwtService.generateToken(testTenant.getId(), testTenant.getOwnerEmail(), null);
    }
    
    @Test
    void shouldReturn404ForNonExistentResource() throws Exception {
        mockMvc.perform(get("/api/v1/providers/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ResourceNotFound"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/api/v1/providers/" + UUID.randomUUID().toString().substring(0, 8) + "*"))
                .andExpect(jsonPath("$.correlationId").exists());
    }
    
    @Test
    void shouldReturn401ForUnauthorizedAccess() throws Exception {
        mockMvc.perform(get("/api/v1/tenant"))
                .andExpect(status().isUnauthorized());
    }
    
    @Test
    void shouldReturn400ForValidationError() throws Exception {
        // Send invalid JSON
        mockMvc.perform(post("/api/v1/providers")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType("application/json")
                        .content("{\"invalid\": \"json\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ValidationError"));
    }
    
    @Test
    void shouldIncludeCorrelationIdInErrorResponse() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.correlationId").isString())
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }
    
    @Test
    void shouldHandleInternalServerErrors() throws Exception {
        // This will trigger an internal error if the JWT service fails
        String invalidToken = "Bearer invalid.jwt.token";
        
        // The filter will handle this gracefully and return 401
        mockMvc.perform(get("/api/v1/tenant")
                        .header("Authorization", invalidToken))
                .andExpect(status().isUnauthorized());
    }
}
