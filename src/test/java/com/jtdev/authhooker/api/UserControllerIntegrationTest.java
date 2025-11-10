package com.jtdev.authhooker.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jtdev.authhooker.domain.Provider;
import com.jtdev.authhooker.domain.Tenant;
import com.jtdev.authhooker.domain.User;
import com.jtdev.authhooker.repository.ProviderRepository;
import com.jtdev.authhooker.repository.TenantRepository;
import com.jtdev.authhooker.repository.UserRepository;
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
 * Integration tests for UserController
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserControllerIntegrationTest {
    
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
    
    @Autowired
    private UserRepository userRepository;
    
    private Tenant testTenant;
    private Provider testProvider;
    private User testUser;
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
        
        // Create test user
        testUser = User.builder()
                .tenant(testTenant)
                .provider(testProvider)
                .subject("google|12345")
                .email("user@example.com")
                .emailVerified(true)
                .rawClaims(Map.of("sub", "google|12345", "email", "user@example.com"))
                .claims(Map.of("sub", "google|12345", "email", "user@example.com"))
                .isActive(true)
                .verificationCount(1)
                .build();
        testUser = userRepository.save(testUser);
        
        // Generate JWT token
        jwtToken = jwtService.generateToken(testTenant.getId(), testTenant.getOwnerEmail(), null);
    }
    
    @Test
    void shouldGetAllUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(testUser.getId().toString()))
                .andExpect(jsonPath("$[0].subject").value("google|12345"))
                .andExpect(jsonPath("$[0].email").value("user@example.com"));
    }
    
    @Test
    void shouldGetUserById() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + testUser.getId())
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testUser.getId().toString()))
                .andExpect(jsonPath("$.subject").value("google|12345"))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.verificationCount").value(1));
    }
    
    @Test
    void shouldDeleteUser() throws Exception {
        mockMvc.perform(delete("/api/v1/users/" + testUser.getId())
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("deleted successfully")));
    }
    
    @Test
    void shouldGetUserPlatformMappings() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + testUser.getId() + "/platform-mappings")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", isA(java.util.List.class)));
    }
    
    @Test
    void shouldEnforceMultiTenantIsolation() throws Exception {
        // Create another tenant and user
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
        
        User anotherUser = User.builder()
                .tenant(anotherTenant)
                .provider(anotherProvider)
                .subject("google|67890")
                .email("another@example.com")
                .emailVerified(true)
                .rawClaims(Map.of("sub", "google|67890"))
                .claims(Map.of("sub", "google|67890"))
                .isActive(true)
                .build();
        anotherUser = userRepository.save(anotherUser);
        
        // Try to access another tenant's user
        mockMvc.perform(get("/api/v1/users/" + anotherUser.getId())
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isForbidden());
    }
    
    @Test
    void shouldReturn404ForNonExistentUser() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + java.util.UUID.randomUUID())
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ResourceNotFound"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }
}
