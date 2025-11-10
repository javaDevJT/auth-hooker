package com.jtdev.authhooker.repository;

import com.jtdev.authhooker.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Find user by tenant, provider, and subject (unique identity)
     */
    @Query("SELECT u FROM User u WHERE u.tenant.id = :tenantId AND u.provider.id = :providerId AND u.subject = :subject AND u.deletedAt IS NULL")
    Optional<User> findByTenantIdAndProviderIdAndSubject(UUID tenantId, UUID providerId, String subject);

    /**
     * Find all active users for a tenant
     */
    @Query("SELECT u FROM User u WHERE u.tenant.id = :tenantId AND u.deletedAt IS NULL")
    List<User> findByTenantId(UUID tenantId);

    /**
     * Find active users for a tenant
     */
    @Query("SELECT u FROM User u WHERE u.tenant.id = :tenantId AND u.isActive = true AND u.deletedAt IS NULL")
    List<User> findActiveByTenantId(UUID tenantId);

    /**
     * Find users by email
     */
    @Query("SELECT u FROM User u WHERE u.email = :email AND u.deletedAt IS NULL")
    List<User> findByEmail(String email);

    /**
     * Find active user by ID
     */
    @Query("SELECT u FROM User u WHERE u.id = :id AND u.deletedAt IS NULL")
    Optional<User> findActiveById(UUID id);

    /**
     * Find users verified within a date range (for MAU calculation)
     */
    @Query("SELECT u FROM User u WHERE u.tenant.id = :tenantId AND u.lastVerifiedAt >= :startDate AND u.lastVerifiedAt < :endDate AND u.deletedAt IS NULL")
    List<User> findByTenantIdAndLastVerifiedAtBetween(UUID tenantId, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Count active verified users for a tenant
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.tenant.id = :tenantId AND u.isActive = true AND u.deletedAt IS NULL")
    Long countActiveByTenantId(UUID tenantId);

    /**
     * Find users by provider
     */
    @Query("SELECT u FROM User u WHERE u.provider.id = :providerId AND u.deletedAt IS NULL")
    List<User> findByProviderId(UUID providerId);
}
