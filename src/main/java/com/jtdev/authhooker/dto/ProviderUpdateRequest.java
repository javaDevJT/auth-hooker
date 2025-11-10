package com.jtdev.authhooker.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request DTO for updating provider information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderUpdateRequest {
    
    @Size(max = 255, message = "Provider name must be at most 255 characters")
    private String name;
    
    @Size(max = 512, message = "Client ID must be at most 512 characters")
    private String clientId;
    
    private Map<String, Object> config;
    
    private Boolean isActive;
    
    private Boolean isPrimary;
}
