package com.jtdev.authhooker.repository;

import com.jtdev.authhooker.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Find audit logs for a tenant (paginated, ordered by timestamp desc)
     */
    @Query("SELECT al FROM AuditLog al WHERE al.tenant.id = :tenantId ORDER BY al.timestamp DESC")
    Page<AuditLog> findByTenantId(UUID tenantId, Pageable pageable);

    /**
     * Find audit logs for a user (paginated, ordered by timestamp desc)
     */
    @Query("SELECT al FROM AuditLog al WHERE al.user.id = :userId ORDER BY al.timestamp DESC")
    Page<AuditLog> findByUserId(UUID userId, Pageable pageable);

    /**
     * Find audit logs by action
     */
    @Query("SELECT al FROM AuditLog al WHERE al.action = :action ORDER BY al.timestamp DESC")
    List<AuditLog> findByAction(String action);

    /**
     * Find audit logs by tenant and action
     */
    @Query("SELECT al FROM AuditLog al WHERE al.tenant.id = :tenantId AND al.action = :action ORDER BY al.timestamp DESC")
    List<AuditLog> findByTenantIdAndAction(UUID tenantId, String action);

    /**
     * Find audit logs within a time range
     */
    @Query("SELECT al FROM AuditLog al WHERE al.timestamp >= :startDate AND al.timestamp < :endDate ORDER BY al.timestamp DESC")
    List<AuditLog> findByTimestampBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find audit logs by resource
     */
    @Query("SELECT al FROM AuditLog al WHERE al.resourceType = :resourceType AND al.resourceId = :resourceId ORDER BY al.timestamp DESC")
    List<AuditLog> findByResource(String resourceType, String resourceId);

    /**
     * Delete audit logs older than specified date (for cleanup)
     */
    @Query("DELETE FROM AuditLog al WHERE al.timestamp < :olderThan")
    void deleteLogsOlderThan(LocalDateTime olderThan);
}
