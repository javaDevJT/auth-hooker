package com.jtdev.authhooker.repository;

import com.jtdev.authhooker.domain.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TenantRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private TenantRepository tenantRepository;

    private Tenant testTenant;

    @BeforeEach
    void setUp() {
        tenantRepository.deleteAll();

        testTenant = Tenant.builder()
                .name("Test Tenant")
                .ownerEmail("owner@test.com")
                .ownerName("Test Owner")
                .planTier("free")
                .status("active")
                .maxVerifiedUsers(50)
                .settings(Map.of("test", "value"))
                .build();
    }

    @Test
    void shouldSaveAndRetrieveTenant() {
        // When
        Tenant saved = tenantRepository.save(testTenant);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Test Tenant");
        assertThat(saved.getOwnerEmail()).isEqualTo("owner@test.com");
        assertThat(saved.getPlanTier()).isEqualTo("free");
        assertThat(saved.getStatus()).isEqualTo("active");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldFindAllActiveTenants() {
        // Given
        Tenant active1 = tenantRepository.save(testTenant);
        
        Tenant active2 = Tenant.builder()
                .name("Active Tenant 2")
                .ownerEmail("owner2@test.com")
                .planTier("professional")
                .status("active")
                .maxVerifiedUsers(100)
                .settings(Map.of())
                .build();
        tenantRepository.save(active2);

        Tenant deleted = Tenant.builder()
                .name("Deleted Tenant")
                .ownerEmail("deleted@test.com")
                .planTier("free")
                .status("cancelled")
                .maxVerifiedUsers(50)
                .settings(Map.of())
                .deletedAt(LocalDateTime.now())
                .build();
        tenantRepository.save(deleted);

        // When
        List<Tenant> activeTenants = tenantRepository.findAllActive();

        // Then
        assertThat(activeTenants).hasSize(2);
        assertThat(activeTenants).extracting(Tenant::getName)
                .containsExactlyInAnyOrder("Test Tenant", "Active Tenant 2");
    }

    @Test
    void shouldFindBySubdomain() {
        // Given
        testTenant.setSubdomain("test-subdomain");
        tenantRepository.save(testTenant);

        // When
        Optional<Tenant> found = tenantRepository.findBySubdomain("test-subdomain");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Test Tenant");
    }

    @Test
    void shouldFindByOwnerEmail() {
        // Given
        tenantRepository.save(testTenant);

        // When
        Optional<Tenant> found = tenantRepository.findByOwnerEmail("owner@test.com");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Test Tenant");
    }

    @Test
    void shouldFindByPlanTier() {
        // Given
        tenantRepository.save(testTenant);

        Tenant professional = Tenant.builder()
                .name("Pro Tenant")
                .ownerEmail("pro@test.com")
                .planTier("professional")
                .status("active")
                .maxVerifiedUsers(500)
                .settings(Map.of())
                .build();
        tenantRepository.save(professional);

        // When
        List<Tenant> freeTenants = tenantRepository.findByPlanTier("free");
        List<Tenant> proTenants = tenantRepository.findByPlanTier("professional");

        // Then
        assertThat(freeTenants).hasSize(1);
        assertThat(freeTenants.get(0).getName()).isEqualTo("Test Tenant");
        assertThat(proTenants).hasSize(1);
        assertThat(proTenants.get(0).getName()).isEqualTo("Pro Tenant");
    }

    @Test
    void shouldSoftDelete() {
        // Given
        Tenant saved = tenantRepository.save(testTenant);

        // When
        saved.softDelete();
        tenantRepository.save(saved);

        // Then
        Optional<Tenant> found = tenantRepository.findActiveById(saved.getId());
        assertThat(found).isEmpty();
        
        Optional<Tenant> foundById = tenantRepository.findById(saved.getId());
        assertThat(foundById).isPresent();
        assertThat(foundById.get().isDeleted()).isTrue();
    }

    @Test
    void shouldUpdateTimestampOnUpdate() throws InterruptedException {
        // Given
        Tenant saved = tenantRepository.save(testTenant);
        LocalDateTime originalUpdatedAt = saved.getUpdatedAt();
        
        // Wait to ensure timestamp difference
        Thread.sleep(100);

        // When
        saved.setName("Updated Name");
        Tenant updated = tenantRepository.save(saved);

        // Then
        assertThat(updated.getUpdatedAt()).isAfter(originalUpdatedAt);
        assertThat(updated.getName()).isEqualTo("Updated Name");
    }
}
