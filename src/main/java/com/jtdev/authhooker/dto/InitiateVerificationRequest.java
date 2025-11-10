package com.jtdev.authhooker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request to initiate OAuth verification flow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiateVerificationRequest {
    
    @NotNull(message = "Tenant ID is required")
    private UUID tenantId;
    
    @NotNull(message = "Provider ID is required")
    private UUID providerId;
    
    @NotBlank(message = "Platform type is required (e.g., 'discord', 'minecraft', 'slack')")
    private String platform;
    
    @NotBlank(message = "Platform user ID is required")
    private String platformUserId;
    
    /**
     * Optional redirect URL after verification completes
     * If not provided, uses default success page
     */
    private String redirectUrl;
}
