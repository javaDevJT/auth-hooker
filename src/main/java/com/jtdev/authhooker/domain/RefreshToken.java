package com.jtdev.authhooker.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * OAuth refresh tokens (encrypted)
 */
@Entity
@Table(name = "refresh_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    // Token data
    @Column(name = "encrypted_token", nullable = false, columnDefinition = "TEXT")
    private String encryptedToken;

    @Column(name = "token_type", length = 50)
    @Builder.Default
    private String tokenType = "refresh";

    @Column(length = 512)
    private String scope;

    // Expiration
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    // Rotation tracking
    @Column(name = "rotation_count", nullable = false)
    @Builder.Default
    private Integer rotationCount = 0;

    @Column(name = "last_rotated_at")
    private LocalDateTime lastRotatedAt;

    // Metadata
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void revoke() {
        this.revokedAt = LocalDateTime.now();
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    public void rotate(String newEncryptedToken) {
        this.encryptedToken = newEncryptedToken;
        this.rotationCount++;
        this.lastRotatedAt = LocalDateTime.now();
    }
}
