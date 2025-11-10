package com.jtdev.authhooker.service;

import com.jtdev.authhooker.domain.Provider;
import com.jtdev.authhooker.domain.Tenant;
import com.jtdev.authhooker.dto.ConnectionTestResult;
import com.jtdev.authhooker.dto.ProviderCreateRequest;
import com.jtdev.authhooker.dto.ProviderTemplate;
import com.jtdev.authhooker.dto.ProviderUpdateRequest;
import com.jtdev.authhooker.exception.ResourceNotFoundException;
import com.jtdev.authhooker.exception.ValidationException;
import com.jtdev.authhooker.repository.ProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing OAuth/OIDC providers
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ProviderService {
    
    private final ProviderRepository providerRepository;
    private final TenantService tenantService;
    private final EncryptionService encryptionService;
    private final WebClient.Builder webClientBuilder;
    
    /**
     * Create a new provider for a tenant
     */
    public Provider createProvider(UUID tenantId, ProviderCreateRequest request) {
        log.info("Creating provider for tenant {}: {}", tenantId, request.getName());
        
        // Validate tenant exists
        Tenant tenant = tenantService.getTenantById(tenantId);
        
        // Validate plan limits
        if (!tenantService.validatePlanLimits(tenantId, "providers")) {
            throw new ValidationException("Provider limit exceeded for current plan");
        }
        
        // Encrypt client secret
        String encryptedSecret = encryptionService.encrypt(request.getClientSecret());
        
        // If this is set as primary, unset other primary providers
        if (Boolean.TRUE.equals(request.getIsPrimary())) {
            unsetPrimaryProviders(tenantId);
        }
        
        // Create provider
        Provider provider = Provider.builder()
                .tenant(tenant)
                .providerType(request.getProviderType())
                .name(request.getName())
                .clientId(request.getClientId())
                .clientSecretEncrypted(encryptedSecret)
                .config(request.getConfig() != null ? request.getConfig() : Map.of())
                .isActive(request.getIsActive())
                .isPrimary(request.getIsPrimary())
                .build();
        
        provider = providerRepository.save(provider);
        log.info("Provider created successfully: {} (id={})", provider.getName(), provider.getId());
        
        return provider;
    }
    
    /**
     * Get provider by ID
     */
    @Transactional(readOnly = true)
    public Provider getProviderById(UUID id) {
        return providerRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Provider", id));
    }
    
    /**
     * Get all providers for a tenant
     */
    @Transactional(readOnly = true)
    public List<Provider> getProvidersByTenant(UUID tenantId) {
        // Validate tenant exists
        tenantService.getTenantById(tenantId);
        
        return providerRepository.findByTenantId(tenantId);
    }
    
    /**
     * Update provider
     */
    public Provider updateProvider(UUID id, ProviderUpdateRequest request) {
        log.info("Updating provider: {}", id);
        
        Provider provider = getProviderById(id);
        
        // Update fields if provided
        if (request.getName() != null) {
            provider.setName(request.getName());
        }
        
        if (request.getClientId() != null) {
            provider.setClientId(request.getClientId());
        }
        
        if (request.getConfig() != null) {
            // Merge config
            Map<String, Object> currentConfig = new HashMap<>(provider.getConfig());
            currentConfig.putAll(request.getConfig());
            provider.setConfig(currentConfig);
        }
        
        if (request.getIsActive() != null) {
            provider.setIsActive(request.getIsActive());
        }
        
        if (request.getIsPrimary() != null && Boolean.TRUE.equals(request.getIsPrimary())) {
            // Unset other primary providers
            unsetPrimaryProviders(provider.getTenant().getId());
            provider.setIsPrimary(true);
        } else if (request.getIsPrimary() != null) {
            provider.setIsPrimary(false);
        }
        
        provider = providerRepository.save(provider);
        log.info("Provider updated successfully: {}", id);
        
        return provider;
    }
    
    /**
     * Delete provider (soft delete)
     */
    public void deleteProvider(UUID id) {
        log.warn("Deleting provider: {}", id);
        
        Provider provider = getProviderById(id);
        
        // Don't allow deleting primary provider if there are other active providers
        if (Boolean.TRUE.equals(provider.getIsPrimary())) {
            List<Provider> activeProviders = providerRepository
                    .findActiveByTenantId(provider.getTenant().getId());
            
            if (activeProviders.size() > 1) {
                throw new ValidationException(
                    "Cannot delete primary provider. Set another provider as primary first.");
            }
        }
        
        provider.softDelete();
        provider.setIsActive(false);
        
        providerRepository.save(provider);
        log.info("Provider deleted (soft): {}", id);
    }
    
    /**
     * Set a provider as primary (unsets others)
     */
    public Provider setPrimaryProvider(UUID providerId) {
        log.info("Setting primary provider: {}", providerId);
        
        Provider provider = getProviderById(providerId);
        
        // Unset other primary providers for this tenant
        unsetPrimaryProviders(provider.getTenant().getId());
        
        // Set this as primary
        provider.setIsPrimary(true);
        provider = providerRepository.save(provider);
        
        log.info("Provider set as primary: {}", providerId);
        return provider;
    }
    
    /**
     * Test provider connection
     */
    @Transactional(readOnly = true)
    public ConnectionTestResult testProviderConnection(UUID id) {
        log.info("Testing provider connection: {}", id);
        
        Provider provider = getProviderById(id);
        
        try {
            // Get discovery URL from config
            Map<String, Object> config = provider.getConfig();
            String issuer = (String) config.get("issuer");
            
            if (issuer == null || issuer.isBlank()) {
                return ConnectionTestResult.failure(
                    "Provider configuration missing 'issuer' URL",
                    Map.of("providerId", id.toString(), "providerType", provider.getProviderType())
                );
            }
            
            // Build discovery URL
            String discoveryUrl = issuer.endsWith("/") 
                ? issuer + ".well-known/openid-configuration"
                : issuer + "/.well-known/openid-configuration";
            
            // Test connection with timeout
            WebClient webClient = webClientBuilder.build();
            
            Map<String, Object> discoveryDoc = webClient.get()
                    .uri(discoveryUrl)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .onErrorResume(e -> {
                        log.error("Provider connection test failed for {}: {}", id, e.getMessage());
                        return Mono.empty();
                    })
                    .block();
            
            if (discoveryDoc == null || discoveryDoc.isEmpty()) {
                return ConnectionTestResult.failure(
                    "Failed to retrieve OIDC discovery document",
                    Map.of("discoveryUrl", discoveryUrl)
                );
            }
            
            // Validate discovery document has required fields
            if (!discoveryDoc.containsKey("authorization_endpoint") ||
                !discoveryDoc.containsKey("token_endpoint")) {
                return ConnectionTestResult.failure(
                    "Invalid OIDC discovery document: missing required endpoints",
                    Map.of("discoveryDoc", discoveryDoc)
                );
            }
            
            log.info("Provider connection test successful: {}", id);
            return ConnectionTestResult.success("Provider connection successful");
            
        } catch (Exception e) {
            log.error("Provider connection test error for {}: {}", id, e.getMessage(), e);
            return ConnectionTestResult.failure(
                "Connection test failed: " + e.getMessage(),
                Map.of("error", e.getClass().getSimpleName())
            );
        }
    }
    
    /**
     * Get provider templates
     */
    @Transactional(readOnly = true)
    public List<ProviderTemplate> getProviderTemplates() {
        return ProviderTemplate.getDefaultTemplates();
    }
    
    /**
     * Rotate client secret
     */
    public Provider rotateClientSecret(UUID id, String newSecret) {
        log.info("Rotating client secret for provider: {}", id);
        
        if (newSecret == null || newSecret.isBlank()) {
            throw new ValidationException("New client secret cannot be null or empty");
        }
        
        Provider provider = getProviderById(id);
        
        // Encrypt new secret
        String encryptedSecret = encryptionService.encrypt(newSecret);
        provider.setClientSecretEncrypted(encryptedSecret);
        
        provider = providerRepository.save(provider);
        log.info("Client secret rotated successfully for provider: {}", id);
        
        return provider;
    }
    
    /**
     * Unset all primary providers for a tenant
     */
    private void unsetPrimaryProviders(UUID tenantId) {
        List<Provider> providers = providerRepository.findByTenantId(tenantId);
        
        providers.stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsPrimary()))
                .forEach(p -> {
                    p.setIsPrimary(false);
                    providerRepository.save(p);
                });
    }
}
