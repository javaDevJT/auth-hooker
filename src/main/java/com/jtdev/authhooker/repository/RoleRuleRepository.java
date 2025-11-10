package com.jtdev.authhooker.repository;

import com.jtdev.authhooker.domain.RoleRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRuleRepository extends JpaRepository<RoleRule, UUID> {

    /**
     * Find all active rules for a tenant (ordered by priority descending)
     */
    @Query("SELECT rr FROM RoleRule rr WHERE rr.tenant.id = :tenantId AND rr.isActive = true AND rr.deletedAt IS NULL ORDER BY rr.priority DESC")
    List<RoleRule> findActiveByTenantId(UUID tenantId);

    /**
     * Find all active rules for a platform integration (ordered by priority descending)
     */
    @Query("SELECT rr FROM RoleRule rr WHERE rr.platformIntegration.id = :platformIntegrationId AND rr.isActive = true AND rr.deletedAt IS NULL ORDER BY rr.priority DESC")
    List<RoleRule> findActiveByPlatformIntegrationId(UUID platformIntegrationId);

    /**
     * Find all active rules for a tenant and platform (ordered by priority descending)
     */
    @Query("SELECT rr FROM RoleRule rr WHERE rr.tenant.id = :tenantId AND rr.platformIntegration.id = :platformIntegrationId AND rr.isActive = true AND rr.deletedAt IS NULL ORDER BY rr.priority DESC")
    List<RoleRule> findActiveByTenantIdAndPlatformIntegrationId(UUID tenantId, UUID platformIntegrationId);

    /**
     * Find all rules for a tenant (including inactive)
     */
    @Query("SELECT rr FROM RoleRule rr WHERE rr.tenant.id = :tenantId AND rr.deletedAt IS NULL")
    List<RoleRule> findByTenantId(UUID tenantId);

    /**
     * Find active rule by ID
     */
    @Query("SELECT rr FROM RoleRule rr WHERE rr.id = :id AND rr.deletedAt IS NULL")
    Optional<RoleRule> findActiveById(UUID id);
}
