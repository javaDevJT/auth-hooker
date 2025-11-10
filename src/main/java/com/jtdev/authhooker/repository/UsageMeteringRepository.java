package com.jtdev.authhooker.repository;

import com.jtdev.authhooker.domain.UsageMetering;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UsageMeteringRepository extends JpaRepository<UsageMetering, UUID> {

    /**
     * Find usage record for a tenant and billing month
     */
    @Query("SELECT um FROM UsageMetering um WHERE um.tenant.id = :tenantId AND um.billingMonth = :billingMonth")
    Optional<UsageMetering> findByTenantIdAndBillingMonth(UUID tenantId, LocalDate billingMonth);

    /**
     * Find all usage records for a tenant (ordered by month desc)
     */
    @Query("SELECT um FROM UsageMetering um WHERE um.tenant.id = :tenantId ORDER BY um.billingMonth DESC")
    List<UsageMetering> findByTenantId(UUID tenantId);

    /**
     * Find unreported usage records
     */
    @Query("SELECT um FROM UsageMetering um WHERE um.reportedToStripe = false ORDER BY um.billingMonth ASC")
    List<UsageMetering> findUnreportedUsage();

    /**
     * Find usage records for a specific billing month
     */
    @Query("SELECT um FROM UsageMetering um WHERE um.billingMonth = :billingMonth")
    List<UsageMetering> findByBillingMonth(LocalDate billingMonth);

    /**
     * Find usage records within a date range
     */
    @Query("SELECT um FROM UsageMetering um WHERE um.billingMonth >= :startMonth AND um.billingMonth <= :endMonth ORDER BY um.billingMonth ASC")
    List<UsageMetering> findByBillingMonthBetween(LocalDate startMonth, LocalDate endMonth);
}
