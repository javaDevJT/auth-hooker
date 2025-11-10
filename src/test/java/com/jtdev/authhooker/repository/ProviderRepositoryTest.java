package com.jtdev.authhooker.repository;

import com.jtdev.authhooker.domain.Provider;
import com.jtdev.authhooker.domain.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private Tenant testTenant;
    private Provider testProvider;

    @BeforeEach
    void setUp() {
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
                .config(Map.of("scopes", List.of("openid", "email", "profile")))
                .isActive(true)
                .isPrimary(false)
                .build();
    }

    @Test
    void shouldSaveAndRetrieveProvider() {
        // When
        Provider saved = providerRepository.save(testProvider);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getProviderType()).isEqualTo("google");
        assertThat(saved.getName()).isEqualTo("Google OAuth");
        assertThat(saved.getClientId()).isEqualTo("test-client-id");
        assertThat(saved.getIsActive()).isTrue();
        assertThat(saved.getIsPrimary()).isFalse();
    }

    @Test
    void shouldFindByTenantId() {
        // Given
        providerRepository.save(testProvider);

        Provider githubProvider = Provider.builder()
                .tenant(testTenant)
                .providerType("github")
                .name("GitHub OAuth")
                .clientId("github-client-id")
                .clientSecretEncrypted("encrypted-secret-2")
                .config(Map.of("scopes", List.of("read:user", "user:email")))
                .isActive(true)
                .isPrimary(false)
                .build();
        providerRepository.save(githubProvider);

        // When
        List<Provider> providers = providerRepository.findByTenantId(testTenant.getId());

        // Then
        assertThat(providers).hasSize(2);
        assertThat(providers).extracting(Provider::getProviderType)
                .containsExactlyInAnyOrder("google", "github");
    }

    @Test
    void shouldFindActiveByTenantId() {
        // Given
        providerRepository.save(testProvider);

        Provider inactiveProvider = Provider.builder()
                .tenant(testTenant)
                .providerType("microsoft")
                .name("Microsoft OAuth")
                .clientId("ms-client-id")
                .clientSecretEncrypted("encrypted-secret-3")
                .config(Map.of())
                .isActive(false)
                .isPrimary(false)
                .build();
        providerRepository.save(inactiveProvider);

        // When
        List<Provider> activeProviders = providerRepository.findActiveByTenantId(testTenant.getId());

        // Then
        assertThat(activeProviders).hasSize(1);
        assertThat(activeProviders.get(0).getProviderType()).isEqualTo("google");
    }

    @Test
    void shouldFindPrimaryByTenantId() {
        // Given
        testProvider.setIsPrimary(true);
        providerRepository.save(testProvider);

        // When
        Optional<Provider> primary = providerRepository.findPrimaryByTenantId(testTenant.getId());

        // Then
        assertThat(primary).isPresent();
        assertThat(primary.get().getProviderType()).isEqualTo("google");
        assertThat(primary.get().getIsPrimary()).isTrue();
    }

    @Test
    void shouldFindByProviderType() {
        // Given
        providerRepository.save(testProvider);

        Tenant anotherTenant = Tenant.builder()
                .name("Another Tenant")
                .ownerEmail("another@test.com")
                .planTier("free")
                .status("active")
                .maxVerifiedUsers(50)
                .settings(Map.of())
                .build();
        anotherTenant = tenantRepository.save(anotherTenant);

        Provider anotherGoogleProvider = Provider.builder()
                .tenant(anotherTenant)
                .providerType("google")
                .name("Google OAuth 2")
                .clientId("another-client-id")
                .clientSecretEncrypted("encrypted-secret-4")
                .config(Map.of())
                .isActive(true)
                .isPrimary(false)
                .build();
        providerRepository.save(anotherGoogleProvider);

        // When
        List<Provider> googleProviders = providerRepository.findByProviderType("google");

        // Then
        assertThat(googleProviders).hasSize(2);
    }

    @Test
    void shouldFindByTenantIdAndProviderType() {
        // Given
        providerRepository.save(testProvider);

        Provider githubProvider = Provider.builder()
                .tenant(testTenant)
                .providerType("github")
                .name("GitHub OAuth")
                .clientId("github-client-id")
                .clientSecretEncrypted("encrypted-secret-5")
                .config(Map.of())
                .isActive(true)
                .isPrimary(false)
                .build();
        providerRepository.save(githubProvider);

        // When
        List<Provider> googleProviders = providerRepository
                .findByTenantIdAndProviderType(testTenant.getId(), "google");

        // Then
        assertThat(googleProviders).hasSize(1);
        assertThat(googleProviders.get(0).getName()).isEqualTo("Google OAuth");
    }
}
