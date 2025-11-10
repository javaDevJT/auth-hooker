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
 * Request DTO for creating/updating claim mappings
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimMappingRequest {
    
    @NotBlank(message = "Mapping name is required")
    @Size(max = 255, message = "Mapping name must be at most 255 characters")
    private String name;
    
    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String description;
    
    @NotBlank(message = "Source path is required")
    @Size(max = 500, message = "Source path must be at most 500 characters")
    private String sourcePath;
    
    @NotBlank(message = "Target field is required")
    @Size(max = 255, message = "Target field must be at most 255 characters")
    private String targetField;
    
    @NotNull(message = "Transform configuration is required")
    @Builder.Default
    private Map<String, Object> transform = Map.of();
    
    @Builder.Default
    private Integer priority = 0;
    
    @Builder.Default
    private Boolean isActive = true;
}
