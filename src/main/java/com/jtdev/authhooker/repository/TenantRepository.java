package com.jtdev.authhooker.repository;

import com.jtdev.authhooker.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    /**
     * Find all active (non-deleted) tenants
     */
    @Query("SELECT t FROM Tenant t WHERE t.deletedAt IS NULL")
    List<Tenant> findAllActive();

    /**
     * Find active tenant by ID
     */
    @Query("SELECT t FROM Tenant t WHERE t.id = :id AND t.deletedAt IS NULL")
    Optional<Tenant> findActiveById(UUID id);

    /**
     * Find tenant by subdomain
     */
    Optional<Tenant> findBySubdomain(String subdomain);

    /**
     * Find tenant by Stripe customer ID
     */
    Optional<Tenant> findByStripeCustomerId(String stripeCustomerId);

    /**
     * Find all tenants by status
     */
    @Query("SELECT t FROM Tenant t WHERE t.status = :status AND t.deletedAt IS NULL")
    List<Tenant> findByStatus(String status);

    /**
     * Find all tenants by plan tier
     */
    @Query("SELECT t FROM Tenant t WHERE t.planTier = :planTier AND t.deletedAt IS NULL")
    List<Tenant> findByPlanTier(String planTier);

    /**
     * Find tenant by owner email
     */
    @Query("SELECT t FROM Tenant t WHERE t.ownerEmail = :ownerEmail AND t.deletedAt IS NULL")
    Optional<Tenant> findByOwnerEmail(String ownerEmail);
}
