package com.jtdev.authhooker.service;

import com.jtdev.authhooker.domain.*;
import com.jtdev.authhooker.exception.ConflictException;
import com.jtdev.authhooker.exception.PlanLimitExceededException;
import com.jtdev.authhooker.exception.ResourceNotFoundException;
import com.jtdev.authhooker.repository.PlatformIntegrationRepository;
import com.jtdev.authhooker.repository.UserPlatformMappingRepository;
import com.jtdev.authhooker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserService
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private UserPlatformMappingRepository mappingRepository;
    
    @Mock
    private PlatformIntegrationRepository integrationRepository;
    
    @Mock
    private TenantService tenantService;
    
    @Mock
    private ProviderService providerService;
    
    @Mock
    private AuditService auditService;
    
    @InjectMocks
    private UserService userService;
    
    private Tenant testTenant;
    private Provider testProvider;
    private User testUser;
    private Map<String, Object> testClaims;
    
    @BeforeEach
    void setUp() {
        testTenant = Tenant.builder()
                .id(UUID.randomUUID())
                .name("Test Tenant")
                .planTier("free")
                .maxVerifiedUsers(50)
                .build();
        
        testProvider = Provider.builder()
                .id(UUID.randomUUID())
                .tenant(testTenant)
                .providerType("google")
                .name("Google")
                .build();
        
        testClaims = Map.of(
                "sub", "google-user-123",
                "email", "user@example.com",
                "email_verified", true,
                "name", "Test User"
        );
        
        testUser = User.builder()
                .id(UUID.randomUUID())
                .tenant(testTenant)
                .provider(testProvider)
                .subject("google-user-123")
                .email("user@example.com")
                .emailVerified(true)
                .rawClaims(testClaims)
                .claims(testClaims)
                .isActive(true)
                .verificationCount(1)
                .verifiedAt(LocalDateTime.now())
                .lastVerifiedAt(LocalDateTime.now())
                .build();
    }
    
    @Test
    void createVerifiedUser_shouldCreateNewUser() {
        // Given
        when(tenantService.getTenantById(any())).thenReturn(testTenant);
        when(providerService.getProviderById(any())).thenReturn(testProvider);
        when(userRepository.findByTenantIdAndProviderIdAndSubject(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(tenantService.validatePlanLimits(any(), eq("users"))).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        
        // When
        User created = userService.createVerifiedUser(
                testTenant.getId(), 
                testProvider.getId(), 
                "google-user-123", 
                testClaims
        );
        
        // Then
        assertThat(created).isNotNull();
        assertThat(created.getSubject()).isEqualTo("google-user-123");
        assertThat(created.getEmail()).isEqualTo("user@example.com");
        assertThat(created.getEmailVerified()).isTrue();
        assertThat(created.getClaims()).containsKey("email_domain");
        
        verify(userRepository).save(any(User.class));
        verify(auditService).logAction(any(), any(), eq("user.created"), any());
    }
    
    @Test
    void createVerifiedUser_shouldUpdateExistingUser() {
        // Given
        when(tenantService.getTenantById(any())).thenReturn(testTenant);
        when(providerService.getProviderById(any())).thenReturn(testProvider);
        when(userRepository.findByTenantIdAndProviderIdAndSubject(any(), any(), any()))
                .thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        
        int initialCount = testUser.getVerificationCount();
        
        // When
        User updated = userService.createVerifiedUser(
                testTenant.getId(), 
                testProvider.getId(), 
                "google-user-123", 
                testClaims
        );
        
        // Then
        assertThat(updated.getVerificationCount()).isEqualTo(initialCount + 1);
        
        verify(userRepository).save(testUser);
        verify(auditService).logAction(any(), any(), eq("user.verified"), any());
    }
    
    @Test
    void createVerifiedUser_shouldThrowExceptionWhenPlanLimitExceeded() {
        // Given
        when(tenantService.getTenantById(any())).thenReturn(testTenant);
        when(providerService.getProviderById(any())).thenReturn(testProvider);
        when(userRepository.findByTenantIdAndProviderIdAndSubject(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(tenantService.validatePlanLimits(any(), eq("users"))).thenReturn(false);
        
        // When/Then
        assertThatThrownBy(() -> userService.createVerifiedUser(
                testTenant.getId(), 
                testProvider.getId(), 
                "new-user", 
                testClaims
        ))
                .isInstanceOf(PlanLimitExceededException.class);
        
        verify(userRepository, never()).save(any());
    }
    
    @Test
    void linkPlatformAccount_shouldCreateNewMapping() {
        // Given
        UUID userId = testUser.getId();
        String platform = "discord";
        String platformUserId = "discord-123";
        
        PlatformIntegration integration = PlatformIntegration.builder()
                .id(UUID.randomUUID())
                .tenant(testTenant)
                .platformType(platform)
                .build();
        
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(testUser));
        when(mappingRepository.findByPlatformTypeAndPlatformUserId(platform, platformUserId))
                .thenReturn(Optional.empty());
        when(mappingRepository.findByUserIdAndPlatformType(userId, platform))
                .thenReturn(Optional.empty());
        when(integrationRepository.findByTenantIdAndPlatformType(any(), eq(platform)))
                .thenReturn(Optional.of(integration));
        when(mappingRepository.save(any(UserPlatformMapping.class))).thenAnswer(i -> {
            UserPlatformMapping m = i.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });
        
        // When
        UserPlatformMapping mapping = userService.linkPlatformAccount(
                userId, 
                platform, 
                platformUserId, 
                Map.of("username", "testuser")
        );
        
        // Then
        assertThat(mapping).isNotNull();
        assertThat(mapping.getPlatformType()).isEqualTo(platform);
        assertThat(mapping.getPlatformUserId()).isEqualTo(platformUserId);
        assertThat(mapping.getPlatformUsername()).isEqualTo("testuser");
        
        verify(mappingRepository).save(any(UserPlatformMapping.class));
        verify(auditService).logAction(any(), any(), eq("platform.linked"), any());
    }
    
    @Test
    void linkPlatformAccount_shouldThrowExceptionWhenPlatformUserAlreadyLinked() {
        // Given
        UUID userId = testUser.getId();
        String platform = "discord";
        String platformUserId = "discord-123";
        
        User otherUser = User.builder().id(UUID.randomUUID()).build();
        UserPlatformMapping existingMapping = UserPlatformMapping.builder()
                .user(otherUser)
                .platformType(platform)
                .platformUserId(platformUserId)
                .build();
        
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(testUser));
        when(mappingRepository.findByPlatformTypeAndPlatformUserId(platform, platformUserId))
                .thenReturn(Optional.of(existingMapping));
        
        // When/Then
        assertThatThrownBy(() -> userService.linkPlatformAccount(
                userId, 
                platform, 
                platformUserId, 
                null
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already linked");
        
        verify(mappingRepository, never()).save(any());
    }
    
    @Test
    void getUserByPlatformId_shouldReturnUser() {
        // Given
        UUID tenantId = testTenant.getId();
        String platform = "discord";
        String platformUserId = "discord-123";
        
        UserPlatformMapping mapping = UserPlatformMapping.builder()
                .user(testUser)
                .platformType(platform)
                .platformUserId(platformUserId)
                .build();
        
        when(tenantService.getTenantById(tenantId)).thenReturn(testTenant);
        when(mappingRepository.findByPlatformTypeAndPlatformUserId(platform, platformUserId))
                .thenReturn(Optional.of(mapping));
        
        // When
        Optional<User> found = userService.getUserByPlatformId(tenantId, platform, platformUserId);
        
        // Then
        assertThat(found).isPresent();
        assertThat(found.get()).isEqualTo(testUser);
    }
    
    @Test
    void unlinkPlatformAccount_shouldUnlinkMapping() {
        // Given
        UUID userId = testUser.getId();
        String platform = "discord";
        
        UserPlatformMapping mapping = UserPlatformMapping.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .platformType(platform)
                .platformUserId("discord-123")
                .build();
        
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(testUser));
        when(mappingRepository.findByUserIdAndPlatformType(userId, platform))
                .thenReturn(Optional.of(mapping));
        when(mappingRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        
        // When
        userService.unlinkPlatformAccount(userId, platform);
        
        // Then
        assertThat(mapping.getUnlinkedAt()).isNotNull();
        assertThat(mapping.getIsActive()).isFalse();
        
        verify(mappingRepository).save(mapping);
        verify(auditService).logAction(any(), any(), eq("platform.unlinked"), any());
    }
    
    @Test
    void deleteUser_shouldSoftDeleteAndUnlinkAll() {
        // Given
        UUID userId = testUser.getId();
        
        UserPlatformMapping mapping1 = UserPlatformMapping.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .platformType("discord")
                .build();
        
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(testUser));
        when(mappingRepository.findByUserId(userId)).thenReturn(List.of(mapping1));
        when(mappingRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        
        // When
        userService.deleteUser(userId);
        
        // Then
        assertThat(testUser.getDeletedAt()).isNotNull();
        assertThat(testUser.getIsActive()).isFalse();
        assertThat(mapping1.getUnlinkedAt()).isNotNull();
        
        verify(userRepository).save(testUser);
        verify(mappingRepository).save(mapping1);
        verify(auditService).logAction(any(), any(), eq("user.deleted"), any());
    }
}
