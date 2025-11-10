package com.jtdev.authhooker.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request DTO for updating tenant information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantUpdateRequest {
    
    @Size(max = 255, message = "Tenant name must be at most 255 characters")
    private String name;
    
    @Email(message = "Owner email must be valid")
    private String ownerEmail;
    
    @Size(max = 255, message = "Owner name must be at most 255 characters")
    private String ownerName;
    
    private Map<String, Object> settings;
    
    private String status;
}
