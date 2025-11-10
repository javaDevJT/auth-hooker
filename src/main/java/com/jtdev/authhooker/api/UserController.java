package com.jtdev.authhooker.api;

import com.jtdev.authhooker.domain.User;
import com.jtdev.authhooker.domain.UserPlatformMapping;
import com.jtdev.authhooker.dto.response.SuccessResponse;
import com.jtdev.authhooker.dto.response.UserPlatformMappingResponse;
import com.jtdev.authhooker.dto.response.UserResponse;
import com.jtdev.authhooker.security.TenantContext;
import com.jtdev.authhooker.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST API for user management
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    
    private final UserService userService;
    
    /**
     * Get all verified users for current tenant
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'API_CLIENT')")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        UUID tenantId = TenantContext.getTenantId();
        List<User> users = userService.getUsersByTenant(tenantId);
        
        List<UserResponse> response = users.stream()
                .map(UserResponse::fromEntity)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get user by ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'API_CLIENT')")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID id) {
        User user = userService.getUserById(id);
        
        // Verify user belongs to current tenant
        UUID tenantId = TenantContext.getTenantId();
        if (!user.getTenant().getId().equals(tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        return ResponseEntity.ok(UserResponse.fromEntity(user));
    }
    
    /**
     * Delete user (soft delete)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<SuccessResponse> deleteUser(@PathVariable UUID id) {
        // Verify user belongs to current tenant
        User user = userService.getUserById(id);
        UUID tenantId = TenantContext.getTenantId();
        if (!user.getTenant().getId().equals(tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        userService.deleteUser(id);
        return ResponseEntity.ok(SuccessResponse.of("User deleted successfully"));
    }
    
    /**
     * Get user's platform mappings
     */
    @GetMapping("/{id}/platform-mappings")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'API_CLIENT')")
    public ResponseEntity<List<UserPlatformMappingResponse>> getUserPlatformMappings(
            @PathVariable UUID id) {
        
        // Verify user belongs to current tenant
        User user = userService.getUserById(id);
        UUID tenantId = TenantContext.getTenantId();
        if (!user.getTenant().getId().equals(tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        List<UserPlatformMapping> mappings = userService.getUserPlatformMappings(id);
        
        List<UserPlatformMappingResponse> response = mappings.stream()
                .map(UserPlatformMappingResponse::fromEntity)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Link user to platform account
     */
    @PostMapping("/{id}/platform-mappings")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'API_CLIENT')")
    public ResponseEntity<UserPlatformMappingResponse> linkPlatformAccount(
            @PathVariable UUID id,
            @Valid @RequestBody PlatformLinkRequest request) {
        
        // Verify user belongs to current tenant
        User user = userService.getUserById(id);
        UUID tenantId = TenantContext.getTenantId();
        if (!user.getTenant().getId().equals(tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        UserPlatformMapping mapping = userService.linkPlatformAccount(
                id,
                request.getPlatform(),
                request.getPlatformUserId(),
                request.getMetadata()
        );
        
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(UserPlatformMappingResponse.fromEntity(mapping));
    }
    
    /**
     * Unlink platform account
     */
    @DeleteMapping("/{id}/platform-mappings/{platform}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'API_CLIENT')")
    public ResponseEntity<SuccessResponse> unlinkPlatformAccount(
            @PathVariable UUID id,
            @PathVariable String platform) {
        
        // Verify user belongs to current tenant
        User user = userService.getUserById(id);
        UUID tenantId = TenantContext.getTenantId();
        if (!user.getTenant().getId().equals(tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        userService.unlinkPlatformAccount(id, platform);
        return ResponseEntity.ok(SuccessResponse.of("Platform account unlinked successfully"));
    }
    
    /**
     * Request DTO for linking platform account
     */
    public record PlatformLinkRequest(
            String platform,
            String platformUserId,
            Map<String, Object> metadata
    ) {}
}
