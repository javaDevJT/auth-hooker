package com.jtdev.authhooker.dto.response;

import com.jtdev.authhooker.domain.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for User entity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private UUID id;
    private UUID tenantId;
    private UUID providerId;
    private String subject;
    private String email;
    private Boolean emailVerified;
    private Map<String, Object> claims;
    private Boolean isActive;
    private Integer verificationCount;
    private LocalDateTime lastVerifiedAt;
    private LocalDateTime verifiedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    /**
     * Convert User entity to UserResponse DTO
     */
    public static UserResponse fromEntity(User user) {
        if (user == null) {
            return null;
        }
        
        return UserResponse.builder()
                .id(user.getId())
                .tenantId(user.getTenant() != null ? user.getTenant().getId() : null)
                .providerId(user.getProvider() != null ? user.getProvider().getId() : null)
                .subject(user.getSubject())
                .email(user.getEmail())
                .emailVerified(user.getEmailVerified())
                .claims(user.getClaims())
                .isActive(user.getIsActive())
                .verificationCount(user.getVerificationCount())
                .lastVerifiedAt(user.getLastVerifiedAt())
                .verifiedAt(user.getVerifiedAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
