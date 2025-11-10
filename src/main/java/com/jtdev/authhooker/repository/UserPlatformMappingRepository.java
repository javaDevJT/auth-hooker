package com.jtdev.authhooker.repository;

import com.jtdev.authhooker.domain.UserPlatformMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserPlatformMappingRepository extends JpaRepository<UserPlatformMapping, UUID> {

    /**
     * Find mapping by user and platform type
     */
    @Query("SELECT upm FROM UserPlatformMapping upm WHERE upm.user.id = :userId AND upm.platformType = :platformType AND upm.unlinkedAt IS NULL")
    Optional<UserPlatformMapping> findByUserIdAndPlatformType(UUID userId, String platformType);

    /**
     * Find mapping by platform type and platform user ID
     */
    @Query("SELECT upm FROM UserPlatformMapping upm WHERE upm.platformType = :platformType AND upm.platformUserId = :platformUserId AND upm.unlinkedAt IS NULL")
    Optional<UserPlatformMapping> findByPlatformTypeAndPlatformUserId(String platformType, String platformUserId);

    /**
     * Find all active mappings for a user
     */
    @Query("SELECT upm FROM UserPlatformMapping upm WHERE upm.user.id = :userId AND upm.unlinkedAt IS NULL")
    List<UserPlatformMapping> findByUserId(UUID userId);

    /**
     * Find all mappings for a platform integration
     */
    @Query("SELECT upm FROM UserPlatformMapping upm WHERE upm.platformIntegration.id = :platformIntegrationId AND upm.unlinkedAt IS NULL")
    List<UserPlatformMapping> findByPlatformIntegrationId(UUID platformIntegrationId);

    /**
     * Find mappings needing role sync
     */
    @Query("SELECT upm FROM UserPlatformMapping upm WHERE upm.platformIntegration.id = :platformIntegrationId AND upm.isActive = true AND upm.unlinkedAt IS NULL AND (upm.lastRoleSyncAt IS NULL OR upm.lastRoleSyncAt < :syncThreshold)")
    List<UserPlatformMapping> findNeedingRoleSync(UUID platformIntegrationId, LocalDateTime syncThreshold);

    /**
     * Find all active mappings for a platform integration
     */
    @Query("SELECT upm FROM UserPlatformMapping upm WHERE upm.platformIntegration.id = :platformIntegrationId AND upm.isActive = true AND upm.unlinkedAt IS NULL")
    List<UserPlatformMapping> findActiveByPlatformIntegrationId(UUID platformIntegrationId);
}
