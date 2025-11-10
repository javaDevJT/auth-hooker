package com.jtdev.authhooker.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jtdev.authhooker.domain.Provider;
import com.jtdev.authhooker.domain.Tenant;
import com.jtdev.authhooker.dto.ProviderCreateRequest;
import com.jtdev.authhooker.dto.ProviderUpdateRequest;
import com.jtdev.authhooker.repository.ProviderRepository;
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
 * Integration tests for ProviderController
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProviderControllerIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private JwtService jwtService;
    
    @Autowired
    private TenantRepository tenantRepository;
    
    @Autowired
    private ProviderRepository providerRepository;
    
    private Tenant testTenant;
    private Provider testProvider;
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
                .build();
        testTenant = tenantRepository.save(testTenant);
        
        // Create test provider
        testProvider = Provider.builder()
                .tenant(testTenant)
                .providerType("google")
                .name("Google OAuth")
                .clientId("test-client-id")
                .clientSecretEncrypted("encrypted-secret")
                .config(Map.of("issuer", "https://accounts.google.com"))
                .isActive(true)
                .isPrimary(true)
                .build();
        testProvider = providerRepository.save(testProvider);
        
        // Generate JWT token
        jwtToken = jwtService.generateToken(testTenant.getId(), testTenant.getOwnerEmail(), null);
    }
    
    @Test
    void shouldGetAllProviders() throws Exception {
        mockMvc.perform(get("/api/v1/providers")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(testProvider.getId().toString()))
                .andExpect(jsonPath("$[0].name").value("Google OAuth"))
                .andExpect(jsonPath("$[0].providerType").value("google"))
                .andExpect(jsonPath("$[0].clientSecretEncrypted").doesNotExist());
    }
    
    @Test
    void shouldCreateProvider() throws Exception {
        // Given
        ProviderCreateRequest request = new ProviderCreateRequest();
        request.setProviderType("github");
        request.setName("GitHub OAuth");
        request.setClientId("github-client-id");
        request.setClientSecret("github-client-secret");
        request.setConfig(Map.of("issuer", "https://github.com"));
        request.setIsActive(true);
        request.setIsPrimary(false);
        
        // When/Then
        mockMvc.perform(post("/api/v1/providers")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("GitHub OAuth"))
                .andExpect(jsonPath("$.providerType").value("github"))
                .andExpect(jsonPath("$.clientId").value("github-client-id"))
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.isPrimary").value(false));
    }
    
    @Test
    void shouldGetProviderById() throws Exception {
        mockMvc.perform(get("/api/v1/providers/" + testProvider.getId())
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testProvider.getId().toString()))
                .andExpect(jsonPath("$.name").value("Google OAuth"));
    }
    
    @Test
    void shouldUpdateProvider() throws Exception {
        // Given
        ProviderUpdateRequest request = new ProviderUpdateRequest();
        request.setName("Updated Google OAuth");
        request.setIsActive(false);
        
        // When/Then
        mockMvc.perform(put("/api/v1/providers/" + testProvider.getId())
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Google OAuth"))
                .andExpect(jsonPath("$.isActive").value(false));
    }
    
    @Test
    void shouldDeleteProvider() throws Exception {
        // Create a non-primary provider to delete
        Provider providerToDelete = Provider.builder()
                .tenant(testTenant)
                .providerType("github")
                .name("GitHub")
                .clientId("test")
                .clientSecretEncrypted("encrypted")
                .config(Map.of())
                .isActive(true)
                .isPrimary(false)
                .build();
        providerToDelete = providerRepository.save(providerToDelete);
        
        mockMvc.perform(delete("/api/v1/providers/" + providerToDelete.getId())
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("deleted successfully")));
    }
    
    @Test
    void shouldSetPrimaryProvider() throws Exception {
        // Create another provider
        Provider secondProvider = Provider.builder()
                .tenant(testTenant)
                .providerType("github")
                .name("GitHub")
                .clientId("test")
                .clientSecretEncrypted("encrypted")
                .config(Map.of())
                .isActive(true)
                .isPrimary(false)
                .build();
        secondProvider = providerRepository.save(secondProvider);
        
        mockMvc.perform(post("/api/v1/providers/" + secondProvider.getId() + "/set-primary")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPrimary").value(true));
    }
    
    @Test
    void shouldGetProviderTemplates() throws Exception {
        mockMvc.perform(get("/api/v1/providers/templates")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$[0].providerType").exists())
                .andExpect(jsonPath("$[0].name").exists());
    }
    
    @Test
    void shouldEnforceMultiTenantIsolation() throws Exception {
        // Create another tenant and provider
        Tenant anotherTenant = Tenant.builder()
                .name("Another Tenant")
                .ownerEmail("another@example.com")
                .planTier("free")
                .maxVerifiedUsers(50)
                .status("active")
                .build();
        anotherTenant = tenantRepository.save(anotherTenant);
        
        Provider anotherProvider = Provider.builder()
                .tenant(anotherTenant)
                .providerType("google")
                .name("Another Provider")
                .clientId("another-client")
                .clientSecretEncrypted("encrypted")
                .config(Map.of())
                .isActive(true)
                .isPrimary(true)
                .build();
        anotherProvider = providerRepository.save(anotherProvider);
        
        // Try to access another tenant's provider
        mockMvc.perform(get("/api/v1/providers/" + anotherProvider.getId())
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isForbidden());
    }
}
