package com.jtdev.authhooker.repository;

import com.jtdev.authhooker.domain.Provider;
import com.jtdev.authhooker.domain.Tenant;
import com.jtdev.authhooker.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UserRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private ProviderRepository providerRepository;

    private Tenant testTenant;
    private Provider testProvider;
    private User testUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
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

        testUser = User.builder()
                .tenant(testTenant)
                .provider(testProvider)
                .subject("google-oauth2|123456")
                .email("user@example.com")
                .emailVerified(true)
                .rawClaims(Map.of(
                        "sub", "google-oauth2|123456",
                        "email", "user@example.com",
                        "email_verified", true,
                        "name", "Test User"
                ))
                .claims(Map.of(
                        "sub", "google-oauth2|123456",
                        "email", "user@example.com",
                        "email_domain", "example.com",
                        "name", "Test User"
                ))
                .isActive(true)
                .build();
    }

    @Test
    void shouldSaveAndRetrieveUser() {
        // When
        User saved = userRepository.save(testUser);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSubject()).isEqualTo("google-oauth2|123456");
        assertThat(saved.getEmail()).isEqualTo("user@example.com");
        assertThat(saved.getEmailVerified()).isTrue();
        assertThat(saved.getRawClaims()).containsKey("sub");
        assertThat(saved.getClaims()).containsKey("email_domain");
        assertThat(saved.getVerificationCount()).isEqualTo(1);
    }

    @Test
    void shouldFindByTenantProviderAndSubject() {
        // Given
        userRepository.save(testUser);

        // When
        Optional<User> found = userRepository.findByTenantIdAndProviderIdAndSubject(
                testTenant.getId(),
                testProvider.getId(),
                "google-oauth2|123456"
        );

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("user@example.com");
    }

    @Test
    void shouldFindByTenantId() {
        // Given
        userRepository.save(testUser);

        User anotherUser = User.builder()
                .tenant(testTenant)
                .provider(testProvider)
                .subject("google-oauth2|789012")
                .email("another@example.com")
                .emailVerified(true)
                .rawClaims(Map.of("sub", "google-oauth2|789012"))
                .claims(Map.of("sub", "google-oauth2|789012"))
                .isActive(true)
                .build();
        userRepository.save(anotherUser);

        // When
        List<User> users = userRepository.findByTenantId(testTenant.getId());

        // Then
        assertThat(users).hasSize(2);
    }

    @Test
    void shouldFindActiveByTenantId() {
        // Given
        userRepository.save(testUser);

        User inactiveUser = User.builder()
                .tenant(testTenant)
                .provider(testProvider)
                .subject("google-oauth2|999999")
                .email("inactive@example.com")
                .emailVerified(true)
                .rawClaims(Map.of("sub", "google-oauth2|999999"))
                .claims(Map.of("sub", "google-oauth2|999999"))
                .isActive(false)
                .build();
        userRepository.save(inactiveUser);

        // When
        List<User> activeUsers = userRepository.findActiveByTenantId(testTenant.getId());

        // Then
        assertThat(activeUsers).hasSize(1);
        assertThat(activeUsers.get(0).getEmail()).isEqualTo("user@example.com");
    }

    @Test
    void shouldFindByEmail() {
        // Given
        userRepository.save(testUser);

        // When
        List<User> users = userRepository.findByEmail("user@example.com");

        // Then
        assertThat(users).hasSize(1);
        assertThat(users.get(0).getSubject()).isEqualTo("google-oauth2|123456");
    }

    @Test
    void shouldCountActiveByTenantId() {
        // Given
        userRepository.save(testUser);

        User anotherUser = User.builder()
                .tenant(testTenant)
                .provider(testProvider)
                .subject("google-oauth2|789012")
                .email("another@example.com")
                .emailVerified(true)
                .rawClaims(Map.of("sub", "google-oauth2|789012"))
                .claims(Map.of("sub", "google-oauth2|789012"))
                .isActive(true)
                .build();
        userRepository.save(anotherUser);

        User inactiveUser = User.builder()
                .tenant(testTenant)
                .provider(testProvider)
                .subject("google-oauth2|999999")
                .email("inactive@example.com")
                .emailVerified(true)
                .rawClaims(Map.of("sub", "google-oauth2|999999"))
                .claims(Map.of("sub", "google-oauth2|999999"))
                .isActive(false)
                .build();
        userRepository.save(inactiveUser);

        // When
        Long count = userRepository.countActiveByTenantId(testTenant.getId());

        // Then
        assertThat(count).isEqualTo(2);
    }

    @Test
    void shouldFindByTenantIdAndLastVerifiedAtBetween() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime monthStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime nextMonth = monthStart.plusMonths(1);

        testUser.setLastVerifiedAt(now.minusDays(5));
        userRepository.save(testUser);

        User oldUser = User.builder()
                .tenant(testTenant)
                .provider(testProvider)
                .subject("google-oauth2|OLD")
                .email("old@example.com")
                .emailVerified(true)
                .rawClaims(Map.of("sub", "google-oauth2|OLD"))
                .claims(Map.of("sub", "google-oauth2|OLD"))
                .isActive(true)
                .lastVerifiedAt(now.minusMonths(2))
                .build();
        userRepository.save(oldUser);

        // When
        List<User> usersThisMonth = userRepository.findByTenantIdAndLastVerifiedAtBetween(
                testTenant.getId(),
                monthStart,
                nextMonth
        );

        // Then
        assertThat(usersThisMonth).hasSize(1);
        assertThat(usersThisMonth.get(0).getEmail()).isEqualTo("user@example.com");
    }
}
