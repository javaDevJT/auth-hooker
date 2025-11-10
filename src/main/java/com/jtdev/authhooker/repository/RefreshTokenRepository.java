package com.jtdev.authhooker.repository;

import com.jtdev.authhooker.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * Find active (non-revoked) refresh token for user and provider
     */
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.user.id = :userId AND rt.provider.id = :providerId AND rt.revokedAt IS NULL")
    Optional<RefreshToken> findActiveByUserIdAndProviderId(UUID userId, UUID providerId);

    /**
     * Find all tokens for a user
     */
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.user.id = :userId")
    List<RefreshToken> findByUserId(UUID userId);

    /**
     * Find all tokens for a provider
     */
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.provider.id = :providerId")
    List<RefreshToken> findByProviderId(UUID providerId);

    /**
     * Find expired tokens that haven't been revoked yet
     */
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.expiresAt < :now AND rt.revokedAt IS NULL")
    List<RefreshToken> findExpiredTokens(LocalDateTime now);

    /**
     * Delete revoked tokens older than specified date
     */
    @Query("DELETE FROM RefreshToken rt WHERE rt.revokedAt IS NOT NULL AND rt.revokedAt < :olderThan")
    void deleteRevokedTokensOlderThan(LocalDateTime olderThan);
}
