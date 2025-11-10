package com.jtdev.authhooker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Tenant usage statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageStats {
    
    private Long totalUsers;
    private Long activeUsers;
    private Long totalProviders;
    private Long activeProviders;
    private Long totalPlatformIntegrations;
    private Long monthlyActiveUsers;
    private Long totalVerifications;
    private LocalDateTime lastVerificationAt;
    private Integer maxVerifiedUsers;
    private Double usagePercentage;
}
