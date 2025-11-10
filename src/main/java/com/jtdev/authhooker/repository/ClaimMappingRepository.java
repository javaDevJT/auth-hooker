package com.jtdev.authhooker.repository;

import com.jtdev.authhooker.domain.ClaimMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClaimMappingRepository extends JpaRepository<ClaimMapping, UUID> {

    /**
     * Find all active claim mappings for a provider
     */
    @Query("SELECT cm FROM ClaimMapping cm WHERE cm.provider.id = :providerId AND cm.isActive = true AND cm.deletedAt IS NULL ORDER BY cm.priority DESC")
    List<ClaimMapping> findActiveByProviderId(UUID providerId);

    /**
     * Find all claim mappings for a provider (including inactive)
     */
    @Query("SELECT cm FROM ClaimMapping cm WHERE cm.provider.id = :providerId AND cm.deletedAt IS NULL")
    List<ClaimMapping> findByProviderId(UUID providerId);

    /**
     * Find claim mapping by provider and target field
     */
    @Query("SELECT cm FROM ClaimMapping cm WHERE cm.provider.id = :providerId AND cm.targetField = :targetField AND cm.deletedAt IS NULL")
    Optional<ClaimMapping> findByProviderIdAndTargetField(UUID providerId, String targetField);

    /**
     * Find active claim mapping by ID
     */
    @Query("SELECT cm FROM ClaimMapping cm WHERE cm.id = :id AND cm.deletedAt IS NULL")
    Optional<ClaimMapping> findActiveById(UUID id);
}
