package com.jtdev.authhooker.service;

import com.jtdev.authhooker.domain.Tenant;
import com.jtdev.authhooker.dto.TenantCreateRequest;
import com.jtdev.authhooker.dto.UsageStats;
import com.jtdev.authhooker.exception.ConflictException;
import com.jtdev.authhooker.exception.PlanLimitExceededException;
import com.jtdev.authhooker.exception.ResourceNotFoundException;
import com.jtdev.authhooker.exception.ValidationException;
import com.jtdev.authhooker.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing tenants
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class TenantService {
    
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;
    private final PlatformIntegrationRepository platformIntegrationRepository;
    
    // Plan limits configuration
    private static final Map<String, Integer> PLAN_USER_LIMITS = Map.of(
        "free", 50,
        "starter", 500,
        "professional", 5000,
        "enterprise", Integer.MAX_VALUE
    );
    
    /**
     * Create a new tenant
     */
    public Tenant createTenant(TenantCreateRequest request) {
        log.info("Creating tenant: {}", request.getName());
        
        // Validate subdomain uniqueness
        if (request.getSubdomain() != null && 
            tenantRepository.findBySubdomain(request.getSubdomain()).isPresent()) {
            throw new ConflictException("Subdomain already exists: " + request.getSubdomain());
        }
        
        // Validate owner email uniqueness (one tenant per email)
        if (tenantRepository.findByOwnerEmail(request.getOwnerEmail()).isPresent()) {
            throw new ConflictException("Tenant already exists for email: " + request.getOwnerEmail());
        }
        
        // Get max users for plan
        Integer maxUsers = PLAN_USER_LIMITS.getOrDefault(request.getPlanTier(), 50);
        
        // Create tenant
        Tenant tenant = Tenant.builder()
                .name(request.getName())
                .subdomain(request.getSubdomain())
                .planTier(request.getPlanTier())
                .maxVerifiedUsers(maxUsers)
                .ownerEmail(request.getOwnerEmail())
                .ownerName(request.getOwnerName())
                .settings(request.getSettings() != null ? request.getSettings() : Map.of())
                .status("active")
                .build();
        
        tenant = tenantRepository.save(tenant);
        log.info("Tenant created successfully: {} (id={})", tenant.getName(), tenant.getId());
        
        return tenant;
    }
    
    /**
     * Get tenant by ID
     */
    @Transactional(readOnly = true)
    public Tenant getTenantById(UUID id) {
        return tenantRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", id));
    }
    
    /**
     * Get tenant by subdomain
     */
    @Transactional(readOnly = true)
    public Tenant getTenantBySubdomain(String subdomain) {
        if (subdomain == null || subdomain.isBlank()) {
            throw new ValidationException("Subdomain cannot be null or empty");
        }
        
        return tenantRepository.findBySubdomain(subdomain)
                .filter(t -> t.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Tenant not found with subdomain: " + subdomain));
    }
    
    /**
     * Update tenant settings
     */
    public Tenant updateTenantSettings(UUID id, Map<String, Object> settings) {
        log.info("Updating tenant settings: {}", id);
        
        if (settings == null) {
            throw new ValidationException("Settings cannot be null");
        }
        
        Tenant tenant = getTenantById(id);
        
        // Merge settings
        Map<String, Object> currentSettings = new HashMap<>(tenant.getSettings());
        currentSettings.putAll(settings);
        tenant.setSettings(currentSettings);
        
        tenant = tenantRepository.save(tenant);
        log.info("Tenant settings updated successfully: {}", id);
        
        return tenant;
    }
    
    /**
     * Update tenant plan
     */
    public Tenant updateTenantPlan(UUID id, String planTier, Integer maxUsers) {
        log.info("Updating tenant plan: {} to {}", id, planTier);
        
        if (planTier == null || planTier.isBlank()) {
            throw new ValidationException("Plan tier cannot be null or empty");
        }
        
        if (!PLAN_USER_LIMITS.containsKey(planTier)) {
            throw new ValidationException("Invalid plan tier: " + planTier);
        }
        
        Tenant tenant = getTenantById(id);
        
        // Check if downgrade would violate limits
        Long currentUserCount = userRepository.countActiveByTenantId(id);
        Integer newMaxUsers = maxUsers != null ? maxUsers : PLAN_USER_LIMITS.get(planTier);
        
        if (currentUserCount > newMaxUsers) {
            throw new PlanLimitExceededException(
                String.format("Cannot downgrade: tenant has %d users, new plan allows %d",
                    currentUserCount, newMaxUsers));
        }
        
        tenant.setPlanTier(planTier);
        tenant.setMaxVerifiedUsers(newMaxUsers);
        
        tenant = tenantRepository.save(tenant);
        log.info("Tenant plan updated successfully: {} to {}", id, planTier);
        
        return tenant;
    }
    
    /**
     * Validate tenant plan limits
     */
    @Transactional(readOnly = true)
    public boolean validatePlanLimits(UUID id, String limitType) {
        Tenant tenant = getTenantById(id);
        
        return switch (limitType.toLowerCase()) {
            case "users" -> {
                Long currentUsers = userRepository.countActiveByTenantId(id);
                yield currentUsers < tenant.getMaxVerifiedUsers();
            }
            case "providers" -> {
                // Free plan: 1 provider, Starter: 3, Professional: 10, Enterprise: unlimited
                Long currentProviders = (long) providerRepository.findActiveByTenantId(id).size();
                int maxProviders = switch (tenant.getPlanTier()) {
                    case "free" -> 1;
                    case "starter" -> 3;
                    case "professional" -> 10;
                    case "enterprise" -> Integer.MAX_VALUE;
                    default -> 1;
                };
                yield currentProviders < maxProviders;
            }
            case "integrations" -> {
                // Free plan: 1 integration, Starter: 2, Professional: 5, Enterprise: unlimited
                Long currentIntegrations = (long) platformIntegrationRepository.findActiveByTenantId(id).size();
                int maxIntegrations = switch (tenant.getPlanTier()) {
                    case "free" -> 1;
                    case "starter" -> 2;
                    case "professional" -> 5;
                    case "enterprise" -> Integer.MAX_VALUE;
                    default -> 1;
                };
                yield currentIntegrations < maxIntegrations;
            }
            default -> throw new ValidationException("Unknown limit type: " + limitType);
        };
    }
    
    /**
     * Get tenant usage statistics
     */
    @Transactional(readOnly = true)
    public UsageStats getTenantUsageStats(UUID id) {
        Tenant tenant = getTenantById(id);
        
        Long totalUsers = (long) userRepository.findByTenantId(id).size();
        Long activeUsers = userRepository.countActiveByTenantId(id);
        Long totalProviders = (long) providerRepository.findByTenantId(id).size();
        Long activeProviders = (long) providerRepository.findActiveByTenantId(id).size();
        Long totalIntegrations = (long) platformIntegrationRepository.findByTenantId(id).size();
        
        // Calculate MAU (users verified in last 30 days)
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        LocalDateTime now = LocalDateTime.now();
        Long monthlyActiveUsers = (long) userRepository
                .findByTenantIdAndLastVerifiedAtBetween(id, thirtyDaysAgo, now)
                .size();
        
        // Total verification count
        Long totalVerifications = userRepository.findByTenantId(id).stream()
                .mapToLong(u -> u.getVerificationCount())
                .sum();
        
        // Last verification timestamp
        LocalDateTime lastVerificationAt = userRepository.findByTenantId(id).stream()
                .map(u -> u.getLastVerifiedAt())
                .max(LocalDateTime::compareTo)
                .orElse(null);
        
        // Usage percentage
        double usagePercentage = (activeUsers.doubleValue() / tenant.getMaxVerifiedUsers()) * 100;
        
        return UsageStats.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .totalProviders(totalProviders)
                .activeProviders(activeProviders)
                .totalPlatformIntegrations(totalIntegrations)
                .monthlyActiveUsers(monthlyActiveUsers)
                .totalVerifications(totalVerifications)
                .lastVerificationAt(lastVerificationAt)
                .maxVerifiedUsers(tenant.getMaxVerifiedUsers())
                .usagePercentage(usagePercentage)
                .build();
    }
    
    /**
     * Suspend tenant
     */
    public Tenant suspendTenant(UUID id) {
        log.warn("Suspending tenant: {}", id);
        
        Tenant tenant = getTenantById(id);
        tenant.setStatus("suspended");
        
        tenant = tenantRepository.save(tenant);
        log.info("Tenant suspended: {}", id);
        
        return tenant;
    }
    
    /**
     * Delete tenant (soft delete)
     */
    public void deleteTenant(UUID id) {
        log.warn("Deleting tenant: {}", id);
        
        Tenant tenant = getTenantById(id);
        tenant.softDelete();
        tenant.setStatus("deleted");
        
        tenantRepository.save(tenant);
        log.info("Tenant deleted (soft): {}", id);
    }
}
