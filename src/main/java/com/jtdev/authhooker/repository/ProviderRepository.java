package com.jtdev.authhooker.repository;

import com.jtdev.authhooker.domain.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderRepository extends JpaRepository<Provider, UUID> {

    /**
     * Find all active providers for a tenant
     */
    @Query("SELECT p FROM Provider p WHERE p.tenant.id = :tenantId AND p.deletedAt IS NULL")
    List<Provider> findByTenantId(UUID tenantId);

    /**
     * Find active provider by ID
     */
    @Query("SELECT p FROM Provider p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<Provider> findActiveById(UUID id);

    /**
     * Find all active providers for a tenant
     */
    @Query("SELECT p FROM Provider p WHERE p.tenant.id = :tenantId AND p.isActive = true AND p.deletedAt IS NULL")
    List<Provider> findActiveByTenantId(UUID tenantId);

    /**
     * Find primary provider for a tenant
     */
    @Query("SELECT p FROM Provider p WHERE p.tenant.id = :tenantId AND p.isPrimary = true AND p.deletedAt IS NULL")
    Optional<Provider> findPrimaryByTenantId(UUID tenantId);

    /**
     * Find providers by type
     */
    @Query("SELECT p FROM Provider p WHERE p.providerType = :providerType AND p.deletedAt IS NULL")
    List<Provider> findByProviderType(String providerType);

    /**
     * Find providers by tenant and type
     */
    @Query("SELECT p FROM Provider p WHERE p.tenant.id = :tenantId AND p.providerType = :providerType AND p.deletedAt IS NULL")
    List<Provider> findByTenantIdAndProviderType(UUID tenantId, String providerType);
}
