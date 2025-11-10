package com.jtdev.authhooker.service;

import com.jtdev.authhooker.domain.*;
import com.jtdev.authhooker.exception.ConflictException;
import com.jtdev.authhooker.exception.PlanLimitExceededException;
import com.jtdev.authhooker.exception.ResourceNotFoundException;
import com.jtdev.authhooker.exception.ValidationException;
import com.jtdev.authhooker.repository.PlatformIntegrationRepository;
import com.jtdev.authhooker.repository.UserPlatformMappingRepository;
import com.jtdev.authhooker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for managing verified users
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UserService {
    
    private final UserRepository userRepository;
    private final UserPlatformMappingRepository mappingRepository;
    private final PlatformIntegrationRepository integrationRepository;
    private final TenantService tenantService;
    private final ProviderService providerService;
    private final AuditService auditService;
    
    /**
     * Create or update a verified user from OAuth claims
     */
    public User createVerifiedUser(UUID tenantId, UUID providerId, String subject, 
                                   Map<String, Object> claims) {
        log.info("Creating/updating verified user for tenant={}, provider={}, subject={}", 
                tenantId, providerId, subject);
        
        if (subject == null || subject.isBlank()) {
            throw new ValidationException("Subject cannot be null or empty");
        }
        
        if (claims == null || claims.isEmpty()) {
            throw new ValidationException("Claims cannot be null or empty");
        }
        
        // Validate tenant and provider
        Tenant tenant = tenantService.getTenantById(tenantId);
        Provider provider = providerService.getProviderById(providerId);
        
        // Check if user already exists
        Optional<User> existingUser = userRepository
                .findByTenantIdAndProviderIdAndSubject(tenantId, providerId, subject);
        
        if (existingUser.isPresent()) {
            // Update existing user
            User user = existingUser.get();
            user.setRawClaims(claims);
            user.setClaims(normalizeClaims(claims));
            user.setLastVerifiedAt(LocalDateTime.now());
            user.setVerificationCount(user.getVerificationCount() + 1);
            
            // Update email if present
            if (claims.containsKey("email")) {
                user.setEmail((String) claims.get("email"));
                user.setEmailVerified(
                    claims.containsKey("email_verified") && 
                    Boolean.TRUE.equals(claims.get("email_verified"))
                );
            }
            
            user = userRepository.save(user);
            log.info("User updated: {} (verification count: {})", 
                    user.getId(), user.getVerificationCount());
            
            // Audit log
            auditService.logAction(tenantId, user.getId(), "user.verified", 
                Map.of("subject", subject, "verificationCount", user.getVerificationCount()));
            
            return user;
        }
        
        // Check plan limits for new user
        if (!tenantService.validatePlanLimits(tenantId, "users")) {
            throw new PlanLimitExceededException(
                "User limit exceeded for current plan tier: " + tenant.getPlanTier());
        }
        
        // Create new user
        Map<String, Object> normalizedClaims = normalizeClaims(claims);
        
        User user = User.builder()
                .tenant(tenant)
                .provider(provider)
                .subject(subject)
                .email(claims.containsKey("email") ? (String) claims.get("email") : null)
                .emailVerified(
                    claims.containsKey("email_verified") && 
                    Boolean.TRUE.equals(claims.get("email_verified"))
                )
                .rawClaims(claims)
                .claims(normalizedClaims)
                .isActive(true)
                .build();
        
        user = userRepository.save(user);
        log.info("User created: {} (subject={})", user.getId(), subject);
        
        // Audit log
        auditService.logAction(tenantId, user.getId(), "user.created", 
            Map.of("subject", subject, "email", user.getEmail()));
        
        return user;
    }
    
    /**
     * Get user by ID
     */
    @Transactional(readOnly = true)
    public User getUserById(UUID id) {
        return userRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
    
    /**
     * Get user by subject (provider-specific identity)
     */
    @Transactional(readOnly = true)
    public Optional<User> getUserBySubject(UUID tenantId, UUID providerId, String subject) {
        return userRepository.findByTenantIdAndProviderIdAndSubject(tenantId, providerId, subject);
    }
    
    /**
     * Get all users for a tenant
     */
    @Transactional(readOnly = true)
    public List<User> getUsersByTenant(UUID tenantId) {
        // Validate tenant exists
        tenantService.getTenantById(tenantId);
        
        return userRepository.findByTenantId(tenantId);
    }
    
    /**
     * Update user claims
     */
    public User updateUserClaims(UUID userId, Map<String, Object> newClaims) {
        log.info("Updating claims for user: {}", userId);
        
        if (newClaims == null || newClaims.isEmpty()) {
            throw new ValidationException("Claims cannot be null or empty");
        }
        
        User user = getUserById(userId);
        
        // Merge with existing claims
        Map<String, Object> mergedClaims = new HashMap<>(user.getClaims());
        mergedClaims.putAll(normalizeClaims(newClaims));
        
        user.setClaims(mergedClaims);
        user.setLastVerifiedAt(LocalDateTime.now());
        
        user = userRepository.save(user);
        log.info("User claims updated: {}", userId);
        
        return user;
    }
    
    /**
     * Link user to a platform account
     */
    public UserPlatformMapping linkPlatformAccount(UUID userId, String platform, 
                                                   String platformUserId, 
                                                   Map<String, Object> metadata) {
        log.info("Linking user {} to platform {} (platformUserId={})", 
                userId, platform, platformUserId);
        
        if (platform == null || platform.isBlank()) {
            throw new ValidationException("Platform type cannot be null or empty");
        }
        
        if (platformUserId == null || platformUserId.isBlank()) {
            throw new ValidationException("Platform user ID cannot be null or empty");
        }
        
        User user = getUserById(userId);
        
        // Check if this platform user is already linked to another user
        Optional<UserPlatformMapping> existingMapping = mappingRepository
                .findByPlatformTypeAndPlatformUserId(platform, platformUserId);
        
        if (existingMapping.isPresent() && 
            !existingMapping.get().getUser().getId().equals(userId)) {
            throw new ConflictException(
                "Platform account already linked to another user: " + platform + "/" + platformUserId);
        }
        
        // Check if user already has a mapping for this platform
        Optional<UserPlatformMapping> userMapping = mappingRepository
                .findByUserIdAndPlatformType(userId, platform);
        
        if (userMapping.isPresent()) {
            // Update existing mapping
            UserPlatformMapping mapping = userMapping.get();
            mapping.setPlatformUserId(platformUserId);
            mapping.setIsActive(true);
            mapping.setUnlinkedAt(null);
            
            if (metadata != null && metadata.containsKey("username")) {
                mapping.setPlatformUsername((String) metadata.get("username"));
            }
            
            mapping = mappingRepository.save(mapping);
            log.info("Platform mapping updated: {}", mapping.getId());
            
            return mapping;
        }
        
        // Find platform integration
        PlatformIntegration integration = integrationRepository
                .findByTenantIdAndPlatformType(user.getTenant().getId(), platform)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Platform integration not found for: " + platform));
        
        // Create new mapping
        UserPlatformMapping mapping = UserPlatformMapping.builder()
                .user(user)
                .platformIntegration(integration)
                .platformType(platform)
                .platformUserId(platformUserId)
                .platformUsername(
                    metadata != null && metadata.containsKey("username") 
                        ? (String) metadata.get("username") 
                        : null
                )
                .currentRoles(List.of())
                .isActive(true)
                .build();
        
        mapping = mappingRepository.save(mapping);
        log.info("Platform mapping created: {} (user={}, platform={})", 
                mapping.getId(), userId, platform);
        
        // Audit log
        auditService.logAction(user.getTenant().getId(), userId, "platform.linked", 
            Map.of("platform", platform, "platformUserId", platformUserId));
        
        return mapping;
    }
    
    /**
     * Get user by platform account
     */
    @Transactional(readOnly = true)
    public Optional<User> getUserByPlatformId(UUID tenantId, String platform, String platformUserId) {
        // Validate inputs
        if (platform == null || platform.isBlank() || platformUserId == null || platformUserId.isBlank()) {
            return Optional.empty();
        }
        
        // Validate tenant exists
        tenantService.getTenantById(tenantId);
        
        return mappingRepository
                .findByPlatformTypeAndPlatformUserId(platform, platformUserId)
                .filter(mapping -> mapping.getUser().getTenant().getId().equals(tenantId))
                .map(UserPlatformMapping::getUser);
    }
    
    /**
     * Unlink platform account
     */
    public void unlinkPlatformAccount(UUID userId, String platform) {
        log.warn("Unlinking platform account for user {}: {}", userId, platform);
        
        User user = getUserById(userId);
        
        UserPlatformMapping mapping = mappingRepository
                .findByUserIdAndPlatformType(userId, platform)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Platform mapping not found for user " + userId + " and platform " + platform));
        
        mapping.unlink();
        mappingRepository.save(mapping);
        
        log.info("Platform mapping unlinked: {}", mapping.getId());
        
        // Audit log
        auditService.logAction(user.getTenant().getId(), userId, "platform.unlinked", 
            Map.of("platform", platform, "platformUserId", mapping.getPlatformUserId()));
    }
    
    /**
     * Get all platform mappings for a user
     */
    @Transactional(readOnly = true)
    public List<UserPlatformMapping> getUserPlatformMappings(UUID userId) {
        // Validate user exists
        getUserById(userId);
        
        return mappingRepository.findByUserId(userId);
    }
    
    /**
     * Delete user (soft delete)
     */
    public void deleteUser(UUID id) {
        log.warn("Deleting user: {}", id);
        
        User user = getUserById(id);
        
        // Unlink all platform accounts
        List<UserPlatformMapping> mappings = mappingRepository.findByUserId(id);
        mappings.forEach(mapping -> {
            mapping.unlink();
            mappingRepository.save(mapping);
        });
        
        // Soft delete user
        user.softDelete();
        user.setIsActive(false);
        
        userRepository.save(user);
        log.info("User deleted (soft): {}", id);
        
        // Audit log
        auditService.logAction(user.getTenant().getId(), id, "user.deleted", Map.of());
    }
    
    /**
     * Normalize OAuth claims to a standard format
     */
    private Map<String, Object> normalizeClaims(Map<String, Object> rawClaims) {
        Map<String, Object> normalized = new HashMap<>(rawClaims);
        
        // Extract email domain if email exists
        if (normalized.containsKey("email") && normalized.get("email") != null) {
            String email = (String) normalized.get("email");
            String domain = email.substring(email.indexOf('@') + 1);
            normalized.put("email_domain", domain);
        }
        
        // Normalize groups/roles if present
        if (normalized.containsKey("groups") && normalized.get("groups") instanceof List) {
            normalized.put("groups", normalized.get("groups"));
        } else if (normalized.containsKey("roles") && normalized.get("roles") instanceof List) {
            normalized.put("groups", normalized.get("roles"));
        }
        
        return normalized;
    }
}
