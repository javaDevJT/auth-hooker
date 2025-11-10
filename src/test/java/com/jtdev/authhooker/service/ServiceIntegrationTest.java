package com.jtdev.authhooker.service;

import com.jtdev.authhooker.domain.*;
import com.jtdev.authhooker.dto.ProviderCreateRequest;
import com.jtdev.authhooker.dto.TenantCreateRequest;
import com.jtdev.authhooker.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for services
 * 
 * Note: These tests require a running PostgreSQL database
 * Configure test database in application-test.properties
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ServiceIntegrationTest {
    
    @Autowired
    private TenantService tenantService;
    
    @Autowired
    private ProviderService providerService;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private VerificationSessionService sessionService;
    
    @Autowired
    private AuditService auditService;
    
    @Autowired
    private TenantRepository tenantRepository;
    
    @Autowired
    private ProviderRepository providerRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @BeforeEach
    void setUp() {
        // Clean up before each test
        userRepository.deleteAll();
        providerRepository.deleteAll();
        tenantRepository.deleteAll();
    }
    
    @Test
    void fullWorkflow_shouldCreateTenantProviderAndUser() {
        // 1. Create tenant
        TenantCreateRequest tenantRequest = TenantCreateRequest.builder()
                .name("Integration Test Tenant")
                .subdomain("int-test")
                .ownerEmail("test@example.com")
                .ownerName("Test Owner")
                .planTier("free")
                .build();
        
        Tenant tenant = tenantService.createTenant(tenantRequest);
        assertThat(tenant).isNotNull();
        assertThat(tenant.getId()).isNotNull();
        
        // 2. Create provider
        ProviderCreateRequest providerRequest = ProviderCreateRequest.builder()
                .providerType("google")
                .name("Google")
                .clientId("test-client-id")
                .clientSecret("test-client-secret")
                .config(Map.of("issuer", "https://accounts.google.com"))
                .isPrimary(true)
                .build();
        
        Provider provider = providerService.createProvider(tenant.getId(), providerRequest);
        assertThat(provider).isNotNull();
        assertThat(provider.getId()).isNotNull();
        assertThat(provider.getIsPrimary()).isTrue();
        
        // 3. Create verification session
        VerificationSession session = sessionService.createSession(
                tenant.getId(),
                provider.getId(),
                "discord",
                "discord-user-123"
        );
        
        assertThat(session).isNotNull();
        assertThat(session.getStateToken()).isNotBlank();
        assertThat(session.getCodeVerifier()).isNotBlank();
        
        // 4. Create verified user
        Map<String, Object> claims = Map.of(
                "sub", "google-user-123",
                "email", "user@example.com",
                "email_verified", true,
                "name", "Test User"
        );
        
        User user = userService.createVerifiedUser(
                tenant.getId(),
                provider.getId(),
                "google-user-123",
                claims
        );
        
        assertThat(user).isNotNull();
        assertThat(user.getId()).isNotNull();
        assertThat(user.getEmail()).isEqualTo("user@example.com");
        assertThat(user.getClaims()).containsKey("email_domain");
        assertThat(user.getClaims().get("email_domain")).isEqualTo("example.com");
        
        // 5. Verify session can be retrieved
        Optional<VerificationSession> foundSession = sessionService.getSessionByState(session.getStateToken());
        assertThat(foundSession).isPresent();
        assertThat(foundSession.get().getId()).isEqualTo(session.getId());
        
        // 6. Verify audit logs were created
        List<AuditLog> auditLogs = auditService.getAuditLogsByUser(user.getId());
        assertThat(auditLogs).isNotEmpty();
        assertThat(auditLogs).anyMatch(log -> log.getAction().equals("user.created"));
        
        // 7. Complete session
        sessionService.completeSession(session.getStateToken(), claims);
        
        // 8. Re-verify user (should update, not create new)
        User updatedUser = userService.createVerifiedUser(
                tenant.getId(),
                provider.getId(),
                "google-user-123",
                claims
        );
        
        assertThat(updatedUser.getId()).isEqualTo(user.getId());
        assertThat(updatedUser.getVerificationCount()).isEqualTo(2);
        
        // 9. Verify tenant stats
        var stats = tenantService.getTenantUsageStats(tenant.getId());
        assertThat(stats.getTotalUsers()).isEqualTo(1L);
        assertThat(stats.getActiveUsers()).isEqualTo(1L);
        assertThat(stats.getTotalProviders()).isEqualTo(1L);
    }
    
    @Test
    void multiTenantIsolation_shouldIsolateTenantData() {
        // Create two tenants
        Tenant tenant1 = tenantService.createTenant(TenantCreateRequest.builder()
                .name("Tenant 1")
                .subdomain("tenant1")
                .ownerEmail("owner1@example.com")
                .planTier("free")
                .build());
        
        Tenant tenant2 = tenantService.createTenant(TenantCreateRequest.builder()
                .name("Tenant 2")
                .subdomain("tenant2")
                .ownerEmail("owner2@example.com")
                .planTier("free")
                .build());
        
        // Create providers for each
        Provider provider1 = providerService.createProvider(tenant1.getId(), 
                ProviderCreateRequest.builder()
                        .providerType("google")
                        .name("Google T1")
                        .clientId("client1")
                        .clientSecret("secret1")
                        .config(Map.of())
                        .build());
        
        Provider provider2 = providerService.createProvider(tenant2.getId(),
                ProviderCreateRequest.builder()
                        .providerType("google")
                        .name("Google T2")
                        .clientId("client2")
                        .clientSecret("secret2")
                        .config(Map.of())
                        .build());
        
        // Create users for each
        userService.createVerifiedUser(tenant1.getId(), provider1.getId(), "user1-sub",
                Map.of("email", "user1@example.com"));
        
        userService.createVerifiedUser(tenant2.getId(), provider2.getId(), "user2-sub",
                Map.of("email", "user2@example.com"));
        
        // Verify isolation
        List<User> tenant1Users = userService.getUsersByTenant(tenant1.getId());
        List<User> tenant2Users = userService.getUsersByTenant(tenant2.getId());
        
        assertThat(tenant1Users).hasSize(1);
        assertThat(tenant2Users).hasSize(1);
        assertThat(tenant1Users.get(0).getEmail()).isEqualTo("user1@example.com");
        assertThat(tenant2Users.get(0).getEmail()).isEqualTo("user2@example.com");
        
        // Verify provider isolation
        List<Provider> tenant1Providers = providerService.getProvidersByTenant(tenant1.getId());
        List<Provider> tenant2Providers = providerService.getProvidersByTenant(tenant2.getId());
        
        assertThat(tenant1Providers).hasSize(1);
        assertThat(tenant2Providers).hasSize(1);
        assertThat(tenant1Providers.get(0).getClientId()).isEqualTo("client1");
        assertThat(tenant2Providers.get(0).getClientId()).isEqualTo("client2");
    }
}
