package com.jtdev.authhooker.api;

import com.jtdev.authhooker.domain.Provider;
import com.jtdev.authhooker.dto.ConnectionTestResult;
import com.jtdev.authhooker.dto.ProviderCreateRequest;
import com.jtdev.authhooker.dto.ProviderTemplate;
import com.jtdev.authhooker.dto.ProviderUpdateRequest;
import com.jtdev.authhooker.dto.response.ProviderResponse;
import com.jtdev.authhooker.dto.response.SuccessResponse;
import com.jtdev.authhooker.security.TenantContext;
import com.jtdev.authhooker.service.ProviderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST API for provider management
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
public class ProviderController {
    
    private final ProviderService providerService;
    
    /**
     * Get all providers for current tenant
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'API_CLIENT')")
    public ResponseEntity<List<ProviderResponse>> getAllProviders() {
        UUID tenantId = TenantContext.getTenantId();
        List<Provider> providers = providerService.getProvidersByTenant(tenantId);
        
        List<ProviderResponse> response = providers.stream()
                .map(ProviderResponse::fromEntity)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Create a new provider
     */
    @PostMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ProviderResponse> createProvider(
            @Valid @RequestBody ProviderCreateRequest request) {
        
        UUID tenantId = TenantContext.getTenantId();
        Provider provider = providerService.createProvider(tenantId, request);
        
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ProviderResponse.fromEntity(provider));
    }
    
    /**
     * Get provider by ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'API_CLIENT')")
    public ResponseEntity<ProviderResponse> getProvider(@PathVariable UUID id) {
        Provider provider = providerService.getProviderById(id);
        
        // Verify provider belongs to current tenant
        UUID tenantId = TenantContext.getTenantId();
        if (!provider.getTenant().getId().equals(tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        return ResponseEntity.ok(ProviderResponse.fromEntity(provider));
    }
    
    /**
     * Update provider
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ProviderResponse> updateProvider(
            @PathVariable UUID id,
            @Valid @RequestBody ProviderUpdateRequest request) {
        
        // Verify provider belongs to current tenant
        Provider existingProvider = providerService.getProviderById(id);
        UUID tenantId = TenantContext.getTenantId();
        if (!existingProvider.getTenant().getId().equals(tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        Provider provider = providerService.updateProvider(id, request);
        return ResponseEntity.ok(ProviderResponse.fromEntity(provider));
    }
    
    /**
     * Delete provider (soft delete)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<SuccessResponse> deleteProvider(@PathVariable UUID id) {
        // Verify provider belongs to current tenant
        Provider provider = providerService.getProviderById(id);
        UUID tenantId = TenantContext.getTenantId();
        if (!provider.getTenant().getId().equals(tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        providerService.deleteProvider(id);
        return ResponseEntity.ok(SuccessResponse.of("Provider deleted successfully"));
    }
    
    /**
     * Test provider connection
     */
    @PostMapping("/{id}/test")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ConnectionTestResult> testProvider(@PathVariable UUID id) {
        // Verify provider belongs to current tenant
        Provider provider = providerService.getProviderById(id);
        UUID tenantId = TenantContext.getTenantId();
        if (!provider.getTenant().getId().equals(tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        ConnectionTestResult result = providerService.testProviderConnection(id);
        return ResponseEntity.ok(result);
    }
    
    /**
     * Set provider as primary
     */
    @PostMapping("/{id}/set-primary")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ProviderResponse> setPrimaryProvider(@PathVariable UUID id) {
        // Verify provider belongs to current tenant
        Provider provider = providerService.getProviderById(id);
        UUID tenantId = TenantContext.getTenantId();
        if (!provider.getTenant().getId().equals(tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        Provider updatedProvider = providerService.setPrimaryProvider(id);
        return ResponseEntity.ok(ProviderResponse.fromEntity(updatedProvider));
    }
    
    /**
     * Get provider templates/presets
     */
    @GetMapping("/templates")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<List<ProviderTemplate>> getProviderTemplates() {
        List<ProviderTemplate> templates = providerService.getProviderTemplates();
        return ResponseEntity.ok(templates);
    }
}
