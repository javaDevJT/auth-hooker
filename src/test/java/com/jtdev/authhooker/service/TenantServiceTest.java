package com.jtdev.authhooker.service;

import com.jtdev.authhooker.domain.Tenant;
import com.jtdev.authhooker.dto.TenantCreateRequest;
import com.jtdev.authhooker.dto.UsageStats;
import com.jtdev.authhooker.exception.ConflictException;
import com.jtdev.authhooker.exception.PlanLimitExceededException;
import com.jtdev.authhooker.exception.ResourceNotFoundException;
import com.jtdev.authhooker.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TenantService
 */
@ExtendWith(MockitoExtension.class)
class TenantServiceTest {
    
    @Mock
    private TenantRepository tenantRepository;
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private ProviderRepository providerRepository;
    
    @Mock
    private PlatformIntegrationRepository platformIntegrationRepository;
    
    @InjectMocks
    private TenantService tenantService;
    
    private Tenant testTenant;
    private TenantCreateRequest createRequest;
    
    @BeforeEach
    void setUp() {
        testTenant = Tenant.builder()
                .id(UUID.randomUUID())
                .name("Test Tenant")
                .subdomain("test-tenant")
                .planTier("free")
                .maxVerifiedUsers(50)
                .ownerEmail("owner@example.com")
                .ownerName("Test Owner")
                .status("active")
                .settings(Map.of())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        createRequest = TenantCreateRequest.builder()
                .name("New Tenant")
                .subdomain("new-tenant")
                .ownerEmail("new@example.com")
                .ownerName("New Owner")
                .planTier("free")
                .settings(Map.of())
                .build();
    }
    
    @Test
    void createTenant_shouldCreateSuccessfully() {
        // Given
        when(tenantRepository.findBySubdomain(anyString())).thenReturn(Optional.empty());
        when(tenantRepository.findByOwnerEmail(anyString())).thenReturn(Optional.empty());
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(i -> {
            Tenant t = i.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
        
        // When
        Tenant created = tenantService.createTenant(createRequest);
        
        // Then
        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo("New Tenant");
        assertThat(created.getSubdomain()).isEqualTo("new-tenant");
        assertThat(created.getMaxVerifiedUsers()).isEqualTo(50); // free plan
        
        verify(tenantRepository).save(any(Tenant.class));
    }
    
    @Test
    void createTenant_shouldThrowExceptionForDuplicateSubdomain() {
        // Given
        when(tenantRepository.findBySubdomain("new-tenant"))
                .thenReturn(Optional.of(testTenant));
        
        // When/Then
        assertThatThrownBy(() -> tenantService.createTenant(createRequest))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Subdomain already exists");
        
        verify(tenantRepository, never()).save(any());
    }
    
    @Test
    void createTenant_shouldThrowExceptionForDuplicateOwnerEmail() {
        // Given
        when(tenantRepository.findBySubdomain(anyString())).thenReturn(Optional.empty());
        when(tenantRepository.findByOwnerEmail("new@example.com"))
                .thenReturn(Optional.of(testTenant));
        
        // When/Then
        assertThatThrownBy(() -> tenantService.createTenant(createRequest))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Tenant already exists for email");
        
        verify(tenantRepository, never()).save(any());
    }
    
    @Test
    void getTenantById_shouldReturnTenant() {
        // Given
        UUID id = testTenant.getId();
        when(tenantRepository.findActiveById(id)).thenReturn(Optional.of(testTenant));
        
        // When
        Tenant found = tenantService.getTenantById(id);
        
        // Then
        assertThat(found).isEqualTo(testTenant);
    }
    
    @Test
    void getTenantById_shouldThrowExceptionWhenNotFound() {
        // Given
        UUID id = UUID.randomUUID();
        when(tenantRepository.findActiveById(id)).thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> tenantService.getTenantById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
    
    @Test
    void updateTenantSettings_shouldMergeSettings() {
        // Given
        UUID id = testTenant.getId();
        testTenant.setSettings(new HashMap<>(Map.of("key1", "value1")));
        
        when(tenantRepository.findActiveById(id)).thenReturn(Optional.of(testTenant));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(i -> i.getArgument(0));
        
        Map<String, Object> newSettings = Map.of("key2", "value2", "key3", "value3");
        
        // When
        Tenant updated = tenantService.updateTenantSettings(id, newSettings);
        
        // Then
        assertThat(updated.getSettings()).containsKeys("key1", "key2", "key3");
        assertThat(updated.getSettings().get("key1")).isEqualTo("value1");
        assertThat(updated.getSettings().get("key2")).isEqualTo("value2");
        
        verify(tenantRepository).save(testTenant);
    }
    
    @Test
    void updateTenantPlan_shouldUpdatePlanAndLimits() {
        // Given
        UUID id = testTenant.getId();
        when(tenantRepository.findActiveById(id)).thenReturn(Optional.of(testTenant));
        when(userRepository.countActiveByTenantId(id)).thenReturn(10L);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(i -> i.getArgument(0));
        
        // When
        Tenant updated = tenantService.updateTenantPlan(id, "starter", 500);
        
        // Then
        assertThat(updated.getPlanTier()).isEqualTo("starter");
        assertThat(updated.getMaxVerifiedUsers()).isEqualTo(500);
        
        verify(tenantRepository).save(testTenant);
    }
    
    @Test
    void updateTenantPlan_shouldThrowExceptionWhenDowngradeViolatesLimits() {
        // Given
        UUID id = testTenant.getId();
        testTenant.setMaxVerifiedUsers(100);
        
        when(tenantRepository.findActiveById(id)).thenReturn(Optional.of(testTenant));
        when(userRepository.countActiveByTenantId(id)).thenReturn(75L); // Current users
        
        // When/Then - trying to downgrade to 50 max users
        assertThatThrownBy(() -> tenantService.updateTenantPlan(id, "free", 50))
                .isInstanceOf(PlanLimitExceededException.class);
        
        verify(tenantRepository, never()).save(any());
    }
    
    @Test
    void validatePlanLimits_users_shouldReturnTrueWhenUnderLimit() {
        // Given
        UUID id = testTenant.getId();
        when(tenantRepository.findActiveById(id)).thenReturn(Optional.of(testTenant));
        when(userRepository.countActiveByTenantId(id)).thenReturn(25L);
        
        // When
        boolean result = tenantService.validatePlanLimits(id, "users");
        
        // Then
        assertThat(result).isTrue();
    }
    
    @Test
    void validatePlanLimits_users_shouldReturnFalseWhenAtLimit() {
        // Given
        UUID id = testTenant.getId();
        when(tenantRepository.findActiveById(id)).thenReturn(Optional.of(testTenant));
        when(userRepository.countActiveByTenantId(id)).thenReturn(50L);
        
        // When
        boolean result = tenantService.validatePlanLimits(id, "users");
        
        // Then
        assertThat(result).isFalse();
    }
    
    @Test
    void getTenantUsageStats_shouldReturnCompleteStats() {
        // Given
        UUID id = testTenant.getId();
        when(tenantRepository.findActiveById(id)).thenReturn(Optional.of(testTenant));
        when(userRepository.findByTenantId(id)).thenReturn(List.of());
        when(userRepository.countActiveByTenantId(id)).thenReturn(30L);
        when(providerRepository.findByTenantId(id)).thenReturn(List.of());
        when(providerRepository.findActiveByTenantId(id)).thenReturn(List.of());
        when(platformIntegrationRepository.findByTenantId(id)).thenReturn(List.of());
        when(userRepository.findByTenantIdAndLastVerifiedAtBetween(any(), any(), any()))
                .thenReturn(List.of());
        
        // When
        UsageStats stats = tenantService.getTenantUsageStats(id);
        
        // Then
        assertThat(stats).isNotNull();
        assertThat(stats.getMaxVerifiedUsers()).isEqualTo(50);
        assertThat(stats.getActiveUsers()).isEqualTo(30L);
    }
    
    @Test
    void suspendTenant_shouldSetStatusToSuspended() {
        // Given
        UUID id = testTenant.getId();
        when(tenantRepository.findActiveById(id)).thenReturn(Optional.of(testTenant));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(i -> i.getArgument(0));
        
        // When
        Tenant suspended = tenantService.suspendTenant(id);
        
        // Then
        assertThat(suspended.getStatus()).isEqualTo("suspended");
        verify(tenantRepository).save(testTenant);
    }
    
    @Test
    void deleteTenant_shouldSoftDelete() {
        // Given
        UUID id = testTenant.getId();
        when(tenantRepository.findActiveById(id)).thenReturn(Optional.of(testTenant));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(i -> i.getArgument(0));
        
        // When
        tenantService.deleteTenant(id);
        
        // Then
        assertThat(testTenant.getDeletedAt()).isNotNull();
        assertThat(testTenant.getStatus()).isEqualTo("deleted");
        verify(tenantRepository).save(testTenant);
    }
}
