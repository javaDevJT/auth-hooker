package com.jtdev.authhooker.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request DTO for creating a new tenant
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantCreateRequest {
    
    @NotBlank(message = "Tenant name is required")
    @Size(max = 255, message = "Tenant name must be at most 255 characters")
    private String name;
    
    @Pattern(regexp = "^[a-z0-9-]{3,63}$", 
            message = "Subdomain must be 3-63 lowercase alphanumeric characters or hyphens")
    private String subdomain;
    
    @NotBlank(message = "Owner email is required")
    @Email(message = "Owner email must be valid")
    private String ownerEmail;
    
    @Size(max = 255, message = "Owner name must be at most 255 characters")
    private String ownerName;
    
    @Pattern(regexp = "^(free|starter|professional|enterprise)$", 
            message = "Plan tier must be one of: free, starter, professional, enterprise")
    @Builder.Default
    private String planTier = "free";
    
    @Builder.Default
    private Map<String, Object> settings = Map.of();
}
