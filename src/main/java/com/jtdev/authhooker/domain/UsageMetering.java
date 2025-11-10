package com.jtdev.authhooker.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * MAU tracking for billing
 */
@Entity
@Table(name = "usage_metering")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageMetering {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    // Billing period
    @Column(name = "billing_month", nullable = false)
    private LocalDate billingMonth;

    // Metrics
    @Column(name = "active_verified_users", nullable = false)
    @Builder.Default
    private Integer activeVerifiedUsers = 0;

    @Column(name = "total_verifications", nullable = false)
    @Builder.Default
    private Integer totalVerifications = 0;

    @Column(name = "total_role_syncs", nullable = false)
    @Builder.Default
    private Integer totalRoleSyncs = 0;

    // Calculated billing
    @Column(name = "metered_amount", precision = 10, scale = 2)
    private BigDecimal meteredAmount;

    @Column(length = 3)
    @Builder.Default
    private String currency = "USD";

    // Status
    @Column(name = "reported_to_stripe")
    @Builder.Default
    private Boolean reportedToStripe = false;

    @Column(name = "reported_at")
    private LocalDateTime reportedAt;

    // Metadata
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void reportToStripe() {
        this.reportedToStripe = true;
        this.reportedAt = LocalDateTime.now();
    }
}
