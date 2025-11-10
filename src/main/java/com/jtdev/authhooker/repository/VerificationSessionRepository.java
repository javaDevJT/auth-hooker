package com.jtdev.authhooker.repository;

import com.jtdev.authhooker.domain.VerificationSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationSessionRepository extends JpaRepository<VerificationSession, UUID> {

    /**
     * Find session by state token
     */
    Optional<VerificationSession> findByStateToken(String stateToken);

    /**
     * Find pending sessions for a tenant
     */
    @Query("SELECT vs FROM VerificationSession vs WHERE vs.tenant.id = :tenantId AND vs.status = 'pending'")
    List<VerificationSession> findPendingByTenantId(UUID tenantId);

    /**
     * Find expired pending sessions
     */
    @Query("SELECT vs FROM VerificationSession vs WHERE vs.status = 'pending' AND vs.expiresAt < :now")
    List<VerificationSession> findExpiredSessions(LocalDateTime now);

    /**
     * Update expired sessions to 'expired' status
     */
    @Modifying
    @Query("UPDATE VerificationSession vs SET vs.status = 'expired' WHERE vs.status = 'pending' AND vs.expiresAt < :now")
    int expireOldSessions(LocalDateTime now);

    /**
     * Find sessions by tenant
     */
    @Query("SELECT vs FROM VerificationSession vs WHERE vs.tenant.id = :tenantId ORDER BY vs.createdAt DESC")
    List<VerificationSession> findByTenantId(UUID tenantId);

    /**
     * Find sessions by provider
     */
    @Query("SELECT vs FROM VerificationSession vs WHERE vs.provider.id = :providerId ORDER BY vs.createdAt DESC")
    List<VerificationSession> findByProviderId(UUID providerId);

    /**
     * Delete completed or failed sessions older than specified date
     */
    @Modifying
    @Query("DELETE FROM VerificationSession vs WHERE vs.status IN ('completed', 'failed', 'expired') AND vs.createdAt < :olderThan")
    int deleteOldSessions(LocalDateTime olderThan);
}
