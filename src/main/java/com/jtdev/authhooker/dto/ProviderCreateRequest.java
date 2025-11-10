package com.jtdev.authhooker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request DTO for creating a new provider
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderCreateRequest {
    
    @NotBlank(message = "Provider type is required")
    @Size(max = 50, message = "Provider type must be at most 50 characters")
    private String providerType;
    
    @NotBlank(message = "Provider name is required")
    @Size(max = 255, message = "Provider name must be at most 255 characters")
    private String name;
    
    @NotBlank(message = "Client ID is required")
    @Size(max = 512, message = "Client ID must be at most 512 characters")
    private String clientId;
    
    @NotBlank(message = "Client secret is required")
    private String clientSecret;
    
    @NotNull(message = "Provider configuration is required")
    @Builder.Default
    private Map<String, Object> config = Map.of();
    
    @Builder.Default
    private Boolean isActive = true;
    
    @Builder.Default
    private Boolean isPrimary = false;
}
