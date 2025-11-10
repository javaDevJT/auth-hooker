package com.jtdev.authhooker.repository;

import com.jtdev.authhooker.domain.PlatformIntegration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlatformIntegrationRepository extends JpaRepository<PlatformIntegration, UUID> {

    /**
     * Find all active integrations for a tenant
     */
    @Query("SELECT pi FROM PlatformIntegration pi WHERE pi.tenant.id = :tenantId AND pi.deletedAt IS NULL")
    List<PlatformIntegration> findByTenantId(UUID tenantId);

    /**
     * Find active integrations for a tenant
     */
    @Query("SELECT pi FROM PlatformIntegration pi WHERE pi.tenant.id = :tenantId AND pi.isActive = true AND pi.deletedAt IS NULL")
    List<PlatformIntegration> findActiveByTenantId(UUID tenantId);

    /**
     * Find integration by tenant and platform type
     */
    @Query("SELECT pi FROM PlatformIntegration pi WHERE pi.tenant.id = :tenantId AND pi.platformType = :platformType AND pi.deletedAt IS NULL")
    Optional<PlatformIntegration> findByTenantIdAndPlatformType(UUID tenantId, String platformType);

    /**
     * Find integration by platform type and platform ID
     */
    @Query("SELECT pi FROM PlatformIntegration pi WHERE pi.platformType = :platformType AND pi.platformId = :platformId AND pi.deletedAt IS NULL")
    Optional<PlatformIntegration> findByPlatformTypeAndPlatformId(String platformType, String platformId);

    /**
     * Find active integration by ID
     */
    @Query("SELECT pi FROM PlatformIntegration pi WHERE pi.id = :id AND pi.deletedAt IS NULL")
    Optional<PlatformIntegration> findActiveById(UUID id);
}
