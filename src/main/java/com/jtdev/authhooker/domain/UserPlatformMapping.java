package com.jtdev.authhooker.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Links verified users to platform accounts (Discord, Minecraft, etc.)
 */
@Entity
@Table(name = "user_platform_mappings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPlatformMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platform_integration_id", nullable = false)
    private PlatformIntegration platformIntegration;

    // Platform identity
    @Column(name = "platform_type", nullable = false, length = 50)
    private String platformType;

    @Column(name = "platform_user_id", nullable = false)
    private String platformUserId;

    @Column(name = "platform_username")
    private String platformUsername;

    // Role sync state (JSONB)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "current_roles", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<String> currentRoles = List.of();

    @Column(name = "last_role_sync_at")
    private LocalDateTime lastRoleSyncAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pending_role_changes", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> pendingRoleChanges = Map.of();

    // Status
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // Metadata
    @Column(name = "linked_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime linkedAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "unlinked_at")
    private LocalDateTime unlinkedAt;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void unlink() {
        this.unlinkedAt = LocalDateTime.now();
        this.isActive = false;
    }

    public boolean isUnlinked() {
        return unlinkedAt != null;
    }
}
