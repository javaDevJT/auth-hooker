package com.jtdev.authhooker.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Temporary OAuth flow state tracking
 */
@Entity
@Table(name = "verification_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    // OAuth state
    @Column(name = "state_token", nullable = false, unique = true)
    private String stateToken;

    @Column(name = "code_verifier")
    private String codeVerifier;

    @Column
    private String nonce;

    // Context
    @Column(name = "platform_type", length = 50)
    private String platformType;

    @Column(name = "platform_user_id")
    private String platformUserId;

    @Column(name = "redirect_url", columnDefinition = "TEXT")
    private String redirectUrl;

    // Session data (JSONB)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "session_data", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> sessionData = Map.of();

    // Status
    @Column(nullable = false, length = 50)
    @Builder.Default
    private String status = "pending";

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // Expiration
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // Metadata
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public void complete() {
        this.status = "completed";
        this.completedAt = LocalDateTime.now();
    }

    public void fail() {
        this.status = "failed";
        this.completedAt = LocalDateTime.now();
    }

    public void expire() {
        this.status = "expired";
    }

    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now()) || "expired".equals(status);
    }

    public boolean isPending() {
        return "pending".equals(status);
    }
}
