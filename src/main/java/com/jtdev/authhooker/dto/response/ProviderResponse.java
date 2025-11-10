package com.jtdev.authhooker.dto.response;

import com.jtdev.authhooker.domain.Provider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for Provider entity
 * Note: clientSecretEncrypted is excluded for security
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderResponse {
    private UUID id;
    private UUID tenantId;
    private String providerType;
    private String name;
    private String clientId;
    private Map<String, Object> config;
    private Boolean isActive;
    private Boolean isPrimary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    /**
     * Convert Provider entity to ProviderResponse DTO
     */
    public static ProviderResponse fromEntity(Provider provider) {
        if (provider == null) {
            return null;
        }
        
        return ProviderResponse.builder()
                .id(provider.getId())
                .tenantId(provider.getTenant() != null ? provider.getTenant().getId() : null)
                .providerType(provider.getProviderType())
                .name(provider.getName())
                .clientId(provider.getClientId())
                .config(provider.getConfig())
                .isActive(provider.getIsActive())
                .isPrimary(provider.getIsPrimary())
                .createdAt(provider.getCreatedAt())
                .updatedAt(provider.getUpdatedAt())
                .build();
    }
}
