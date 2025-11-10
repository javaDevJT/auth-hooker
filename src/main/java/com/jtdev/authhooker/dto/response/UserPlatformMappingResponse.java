package com.jtdev.authhooker.dto.response;

import com.jtdev.authhooker.domain.UserPlatformMapping;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for UserPlatformMapping entity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPlatformMappingResponse {
    private UUID id;
    private UUID userId;
    private String platform;
    private String platformUserId;
    private String platformUsername;
    private List<String> currentRoles;
    private LocalDateTime lastSyncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    /**
     * Convert UserPlatformMapping entity to UserPlatformMappingResponse DTO
     */
    public static UserPlatformMappingResponse fromEntity(UserPlatformMapping mapping) {
        if (mapping == null) {
            return null;
        }
        
        return UserPlatformMappingResponse.builder()
                .id(mapping.getId())
                .userId(mapping.getUser() != null ? mapping.getUser().getId() : null)
                .platform(mapping.getPlatform())
                .platformUserId(mapping.getPlatformUserId())
                .platformUsername(mapping.getPlatformUsername())
                .currentRoles(mapping.getCurrentRoles())
                .lastSyncedAt(mapping.getLastSyncedAt())
                .createdAt(mapping.getCreatedAt())
                .updatedAt(mapping.getUpdatedAt())
                .build();
    }
}
