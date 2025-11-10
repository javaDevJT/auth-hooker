package com.jtdev.authhooker.api;

import com.jtdev.authhooker.domain.Tenant;
import com.jtdev.authhooker.dto.TenantUpdateRequest;
import com.jtdev.authhooker.dto.UsageStats;
import com.jtdev.authhooker.dto.response.SuccessResponse;
import com.jtdev.authhooker.dto.response.TenantResponse;
import com.jtdev.authhooker.security.TenantContext;
import com.jtdev.authhooker.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * REST API for tenant management
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tenant")
@RequiredArgsConstructor
public class TenantController {
    
    private final TenantService tenantService;
    private final SecureRandom secureRandom = new SecureRandom();
    
    /**
     * Get current tenant information
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'API_CLIENT')")
    public ResponseEntity<TenantResponse> getCurrentTenant() {
        UUID tenantId = TenantContext.getTenantId();
        Tenant tenant = tenantService.getTenantById(tenantId);
        return ResponseEntity.ok(TenantResponse.fromEntity(tenant));
    }
    
    /**
     * Update tenant settings
     */
    @PutMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<TenantResponse> updateTenantSettings(
            @Valid @RequestBody TenantUpdateRequest request) {
        
        UUID tenantId = TenantContext.getTenantId();
        Tenant tenant = tenantService.updateTenantSettings(tenantId, request.getSettings());
        
        return ResponseEntity.ok(TenantResponse.fromEntity(tenant));
    }
    
    /**
     * Get tenant usage statistics
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'API_CLIENT')")
    public ResponseEntity<UsageStats> getTenantStats() {
        UUID tenantId = TenantContext.getTenantId();
        UsageStats stats = tenantService.getTenantUsageStats(tenantId);
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Rotate API key
     * Generates a new secure random API key
     */
    @PostMapping("/api-key/rotate")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<Map<String, String>> rotateApiKey() {
        UUID tenantId = TenantContext.getTenantId();
        
        // Generate new API key (256-bit random)
        byte[] keyBytes = new byte[32];
        secureRandom.nextBytes(keyBytes);
        String newApiKey = Base64.getUrlEncoder().withoutPadding().encodeToString(keyBytes);
        
        // Update tenant settings with new API key
        tenantService.updateTenantSettings(tenantId, Map.of("api_key", newApiKey));
        
        log.info("API key rotated for tenant: {}", tenantId);
        
        return ResponseEntity.ok(Map.of(
                "apiKey", newApiKey,
                "message", "API key rotated successfully. Update your bots and plugins with the new key."
        ));
    }
}
