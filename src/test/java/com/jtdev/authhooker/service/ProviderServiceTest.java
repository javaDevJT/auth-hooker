package com.jtdev.authhooker.service;

import com.jtdev.authhooker.domain.Provider;
import com.jtdev.authhooker.domain.Tenant;
import com.jtdev.authhooker.dto.ProviderCreateRequest;
import com.jtdev.authhooker.dto.ProviderTemplate;
import com.jtdev.authhooker.exception.ValidationException;
import com.jtdev.authhooker.repository.ProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProviderService
 */
@ExtendWith(MockitoExtension.class)
class ProviderServiceTest {
    
    @Mock
    private ProviderRepository providerRepository;
    
    @Mock
    private TenantService tenantService;
    
    @Mock
    private EncryptionService encryptionService;
    
    @Mock
    private WebClient.Builder webClientBuilder;
    
    @InjectMocks
    private ProviderService providerService;
    
    private Tenant testTenant;
    private Provider testProvider;
    private ProviderCreateRequest createRequest;
    
    @BeforeEach
    void setUp() {
        testTenant = Tenant.builder()
                .id(UUID.randomUUID())
                .name("Test Tenant")
                .planTier("free")
                .build();
        
        testProvider = Provider.builder()
                .id(UUID.randomUUID())
                .tenant(testTenant)
                .providerType("google")
                .name("Google")
                .clientId("test-client-id")
                .clientSecretEncrypted("encrypted-secret")
                .config(Map.of("issuer", "https://accounts.google.com"))
                .isActive(true)
                .isPrimary(false)
                .build();
        
        createRequest = ProviderCreateRequest.builder()
                .providerType("google")
                .name("Google")
                .clientId("test-client-id")
                .clientSecret("test-client-secret")
                .config(Map.of("issuer", "https://accounts.google.com"))
                .isActive(true)
                .isPrimary(false)
                .build();
    }
    
    @Test
    void createProvider_shouldCreateSuccessfully() {
        // Given
        UUID tenantId = testTenant.getId();
        
        when(tenantService.getTenantById(tenantId)).thenReturn(testTenant);
        when(tenantService.validatePlanLimits(tenantId, "providers")).thenReturn(true);
        when(encryptionService.encrypt(anyString())).thenReturn("encrypted-secret");
        when(providerRepository.save(any(Provider.class))).thenAnswer(i -> {
            Provider p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });
        
        // When
        Provider created = providerService.createProvider(tenantId, createRequest);
        
        // Then
        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo("Google");
        assertThat(created.getProviderType()).isEqualTo("google");
        assertThat(created.getClientSecretEncrypted()).isEqualTo("encrypted-secret");
        
        verify(encryptionService).encrypt("test-client-secret");
        verify(providerRepository).save(any(Provider.class));
    }
    
    @Test
    void createProvider_shouldUnsetOtherPrimaryProviders() {
        // Given
        UUID tenantId = testTenant.getId();
        createRequest.setIsPrimary(true);
        
        Provider existingPrimary = Provider.builder()
                .id(UUID.randomUUID())
                .tenant(testTenant)
                .isPrimary(true)
                .build();
        
        when(tenantService.getTenantById(tenantId)).thenReturn(testTenant);
        when(tenantService.validatePlanLimits(tenantId, "providers")).thenReturn(true);
        when(encryptionService.encrypt(anyString())).thenReturn("encrypted-secret");
        when(providerRepository.findByTenantId(tenantId)).thenReturn(List.of(existingPrimary));
        when(providerRepository.save(any(Provider.class))).thenAnswer(i -> i.getArgument(0));
        
        // When
        providerService.createProvider(tenantId, createRequest);
        
        // Then
        assertThat(existingPrimary.getIsPrimary()).isFalse();
        verify(providerRepository, atLeastOnce()).save(existingPrimary);
    }
    
    @Test
    void createProvider_shouldThrowExceptionWhenPlanLimitExceeded() {
        // Given
        UUID tenantId = testTenant.getId();
        
        when(tenantService.getTenantById(tenantId)).thenReturn(testTenant);
        when(tenantService.validatePlanLimits(tenantId, "providers")).thenReturn(false);
        
        // When/Then
        assertThatThrownBy(() -> providerService.createProvider(tenantId, createRequest))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Provider limit exceeded");
        
        verify(providerRepository, never()).save(any());
    }
    
    @Test
    void setPrimaryProvider_shouldUnsetOthersAndSetCurrent() {
        // Given
        UUID providerId = testProvider.getId();
        UUID tenantId = testTenant.getId();
        
        Provider otherProvider = Provider.builder()
                .id(UUID.randomUUID())
                .tenant(testTenant)
                .isPrimary(true)
                .build();
        
        when(providerRepository.findActiveById(providerId)).thenReturn(Optional.of(testProvider));
        when(providerRepository.findByTenantId(tenantId)).thenReturn(List.of(otherProvider));
        when(providerRepository.save(any(Provider.class))).thenAnswer(i -> i.getArgument(0));
        
        // When
        Provider updated = providerService.setPrimaryProvider(providerId);
        
        // Then
        assertThat(updated.getIsPrimary()).isTrue();
        assertThat(otherProvider.getIsPrimary()).isFalse();
        
        verify(providerRepository, times(2)).save(any());
    }
    
    @Test
    void rotateClientSecret_shouldEncryptAndUpdateSecret() {
        // Given
        UUID providerId = testProvider.getId();
        String newSecret = "new-client-secret";
        
        when(providerRepository.findActiveById(providerId)).thenReturn(Optional.of(testProvider));
        when(encryptionService.encrypt(newSecret)).thenReturn("new-encrypted-secret");
        when(providerRepository.save(any(Provider.class))).thenAnswer(i -> i.getArgument(0));
        
        // When
        Provider updated = providerService.rotateClientSecret(providerId, newSecret);
        
        // Then
        assertThat(updated.getClientSecretEncrypted()).isEqualTo("new-encrypted-secret");
        
        verify(encryptionService).encrypt(newSecret);
        verify(providerRepository).save(testProvider);
    }
    
    @Test
    void getProviderTemplates_shouldReturnPredefinedTemplates() {
        // When
        List<ProviderTemplate> templates = providerService.getProviderTemplates();
        
        // Then
        assertThat(templates).isNotEmpty();
        assertThat(templates).anyMatch(t -> t.getProviderType().equals("google"));
        assertThat(templates).anyMatch(t -> t.getProviderType().equals("github"));
        assertThat(templates).anyMatch(t -> t.getProviderType().equals("microsoft"));
        assertThat(templates).anyMatch(t -> t.getProviderType().equals("custom-oidc"));
    }
    
    @Test
    void deleteProvider_shouldSoftDelete() {
        // Given
        UUID providerId = testProvider.getId();
        testProvider.setIsPrimary(false); // Not primary
        
        when(providerRepository.findActiveById(providerId)).thenReturn(Optional.of(testProvider));
        when(providerRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        
        // When
        providerService.deleteProvider(providerId);
        
        // Then
        assertThat(testProvider.getDeletedAt()).isNotNull();
        assertThat(testProvider.getIsActive()).isFalse();
        
        verify(providerRepository).save(testProvider);
    }
    
    @Test
    void deleteProvider_shouldThrowExceptionForPrimaryWithOthers() {
        // Given
        UUID providerId = testProvider.getId();
        testProvider.setIsPrimary(true);
        
        Provider otherProvider = Provider.builder()
                .id(UUID.randomUUID())
                .build();
        
        when(providerRepository.findActiveById(providerId)).thenReturn(Optional.of(testProvider));
        when(providerRepository.findActiveByTenantId(any()))
                .thenReturn(List.of(testProvider, otherProvider));
        
        // When/Then
        assertThatThrownBy(() -> providerService.deleteProvider(providerId))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Cannot delete primary provider");
        
        verify(providerRepository, never()).save(any());
    }
}
