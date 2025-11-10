package com.jtdev.authhooker.repository;

import com.jtdev.authhooker.domain.Provider;
import com.jtdev.authhooker.domain.Tenant;
import com.jtdev.authhooker.domain.VerificationSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationSessionRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private VerificationSessionRepository sessionRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private ProviderRepository providerRepository;

    private Tenant testTenant;
    private Provider testProvider;
    private VerificationSession testSession;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        providerRepository.deleteAll();
        tenantRepository.deleteAll();

        testTenant = Tenant.builder()
                .name("Test Tenant")
                .ownerEmail("owner@test.com")
                .planTier("free")
                .status("active")
                .maxVerifiedUsers(50)
                .settings(Map.of())
                .build();
        testTenant = tenantRepository.save(testTenant);

        testProvider = Provider.builder()
                .tenant(testTenant)
                .providerType("google")
                .name("Google OAuth")
                .clientId("test-client-id")
                .clientSecretEncrypted("encrypted-secret")
                .config(Map.of())
                .isActive(true)
                .isPrimary(true)
                .build();
        testProvider = providerRepository.save(testProvider);

        testSession = VerificationSession.builder()
                .tenant(testTenant)
                .provider(testProvider)
                .stateToken("state-123456")
                .codeVerifier("verifier-abc123")
                .nonce("nonce-xyz789")
                .platformType("discord")
                .platformUserId("discord-user-123")
                .redirectUrl("https://example.com/callback")
                .sessionData(Map.of("discord_guild_id", "guild-456"))
                .status("pending")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
    }

    @Test
    void shouldSaveAndRetrieveSession() {
        // When
        VerificationSession saved = sessionRepository.save(testSession);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStateToken()).isEqualTo("state-123456");
        assertThat(saved.getCodeVerifier()).isEqualTo("verifier-abc123");
        assertThat(saved.getStatus()).isEqualTo("pending");
        assertThat(saved.isPending()).isTrue();
    }

    @Test
    void shouldFindByStateToken() {
        // Given
        sessionRepository.save(testSession);

        // When
        Optional<VerificationSession> found = sessionRepository.findByStateToken("state-123456");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getPlatformUserId()).isEqualTo("discord-user-123");
    }

    @Test
    void shouldFindPendingByTenantId() {
        // Given
        sessionRepository.save(testSession);

        VerificationSession completedSession = VerificationSession.builder()
                .tenant(testTenant)
                .provider(testProvider)
                .stateToken("state-completed")
                .codeVerifier("verifier-completed")
                .nonce("nonce-completed")
                .status("completed")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .completedAt(LocalDateTime.now())
                .sessionData(Map.of())
                .build();
        sessionRepository.save(completedSession);

        // When
        List<VerificationSession> pending = sessionRepository.findPendingByTenantId(testTenant.getId());

        // Then
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getStateToken()).isEqualTo("state-123456");
    }

    @Test
    void shouldFindExpiredSessions() {
        // Given
        VerificationSession expiredSession = VerificationSession.builder()
                .tenant(testTenant)
                .provider(testProvider)
                .stateToken("state-expired")
                .codeVerifier("verifier-expired")
                .status("pending")
                .expiresAt(LocalDateTime.now().minusMinutes(5))
                .sessionData(Map.of())
                .build();
        sessionRepository.save(expiredSession);

        sessionRepository.save(testSession); // Not expired

        // When
        List<VerificationSession> expired = sessionRepository.findExpiredSessions(LocalDateTime.now());

        // Then
        assertThat(expired).hasSize(1);
        assertThat(expired.get(0).getStateToken()).isEqualTo("state-expired");
    }

    @Test
    void shouldUpdateExpiredSessions() {
        // Given
        VerificationSession expiredSession = VerificationSession.builder()
                .tenant(testTenant)
                .provider(testProvider)
                .stateToken("state-expired")
                .codeVerifier("verifier-expired")
                .status("pending")
                .expiresAt(LocalDateTime.now().minusMinutes(5))
                .sessionData(Map.of())
                .build();
        sessionRepository.save(expiredSession);

        // When
        int updated = sessionRepository.expireOldSessions(LocalDateTime.now());

        // Then
        assertThat(updated).isEqualTo(1);

        Optional<VerificationSession> found = sessionRepository.findByStateToken("state-expired");
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo("expired");
    }

    @Test
    void shouldCompleteSession() {
        // Given
        VerificationSession saved = sessionRepository.save(testSession);

        // When
        saved.complete();
        sessionRepository.save(saved);

        // Then
        Optional<VerificationSession> found = sessionRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo("completed");
        assertThat(found.get().getCompletedAt()).isNotNull();
        assertThat(found.get().isPending()).isFalse();
    }

    @Test
    void shouldCheckExpiration() {
        // Given - expired session
        VerificationSession expiredSession = VerificationSession.builder()
                .tenant(testTenant)
                .provider(testProvider)
                .stateToken("state-expired")
                .codeVerifier("verifier-expired")
                .status("pending")
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .sessionData(Map.of())
                .build();

        // Then
        assertThat(expiredSession.isExpired()).isTrue();

        // Given - valid session
        assertThat(testSession.isExpired()).isFalse();
    }
}
