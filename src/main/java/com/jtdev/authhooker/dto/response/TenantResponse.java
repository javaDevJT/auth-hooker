package com.jtdev.authhooker.dto.response;

import com.jtdev.authhooker.domain.Tenant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for Tenant entity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantResponse {
    private UUID id;
    private String name;
    private String subdomain;
    private String planTier;
    private String status;
    private Integer maxVerifiedUsers;
    private String ownerEmail;
    private String ownerName;
    private Map<String, Object> settings;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    /**
     * Convert Tenant entity to TenantResponse DTO
     */
    public static TenantResponse fromEntity(Tenant tenant) {
        if (tenant == null) {
            return null;
        }
        
        return TenantResponse.builder()
                .id(tenant.getId())
                .name(tenant.getName())
                .subdomain(tenant.getSubdomain())
                .planTier(tenant.getPlanTier())
                .status(tenant.getStatus())
                .maxVerifiedUsers(tenant.getMaxVerifiedUsers())
                .ownerEmail(tenant.getOwnerEmail())
                .ownerName(tenant.getOwnerName())
                .settings(tenant.getSettings())
                .createdAt(tenant.getCreatedAt())
                .updatedAt(tenant.getUpdatedAt())
                .build();
    }
}
