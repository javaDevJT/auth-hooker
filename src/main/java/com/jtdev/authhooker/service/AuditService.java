package com.jtdev.authhooker.service;

import com.jtdev.authhooker.domain.AuditLog;
import com.jtdev.authhooker.domain.Tenant;
import com.jtdev.authhooker.domain.User;
import com.jtdev.authhooker.repository.AuditLogRepository;
import com.jtdev.authhooker.repository.TenantRepository;
import com.jtdev.authhooker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for audit logging and activity tracking
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AuditService {
    
    private final AuditLogRepository auditLogRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    
    /**
     * Log an action
     */
    public AuditLog logAction(UUID tenantId, UUID userId, String action, 
                              Map<String, Object> details) {
        return logAction(tenantId, userId, action, details, null, null);
    }
    
    /**
     * Log an action with full context
     */
    public AuditLog logAction(UUID tenantId, UUID userId, String action, 
                              Map<String, Object> details, String ipAddress, String userAgent) {
        log.debug("Logging action: tenant={}, user={}, action={}", tenantId, userId, action);
        
        // Fetch entities (may be null)
        Tenant tenant = tenantId != null 
                ? tenantRepository.findById(tenantId).orElse(null) 
                : null;
        
        User user = userId != null 
                ? userRepository.findById(userId).orElse(null) 
                : null;
        
        // Determine actor
        String actorType = user != null ? "user" : "system";
        String actorId = user != null ? user.getId().toString() : null;
        
        // Extract resource info from details if present
        String resourceType = details != null && details.containsKey("resourceType")
                ? (String) details.get("resourceType")
                : null;
        
        String resourceId = details != null && details.containsKey("resourceId")
                ? (String) details.get("resourceId")
                : null;
        
        AuditLog auditLog = AuditLog.builder()
                .tenant(tenant)
                .user(user)
                .actorType(actorType)
                .actorId(actorId)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .details(details != null ? details : Map.of())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();
        
        auditLog = auditLogRepository.save(auditLog);
        
        return auditLog;
    }
    
    /**
     * Get audit logs for a tenant within a time range
     */
    @Transactional(readOnly = true)
    public List<AuditLog> getAuditLogs(UUID tenantId, LocalDateTime from, LocalDateTime to) {
        return getAuditLogs(tenantId, from, to, 1000); // Default limit
    }
    
    /**
     * Get audit logs for a tenant within a time range with pagination
     */
    @Transactional(readOnly = true)
    public List<AuditLog> getAuditLogs(UUID tenantId, LocalDateTime from, LocalDateTime to, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        Page<AuditLog> page = auditLogRepository.findByTenantId(tenantId, pageable);
        
        // Filter by date range
        return page.getContent().stream()
                .filter(log -> {
                    LocalDateTime timestamp = log.getTimestamp();
                    boolean afterFrom = from == null || timestamp.isAfter(from) || timestamp.isEqual(from);
                    boolean beforeTo = to == null || timestamp.isBefore(to);
                    return afterFrom && beforeTo;
                })
                .toList();
    }
    
    /**
     * Get audit logs for a specific user
     */
    @Transactional(readOnly = true)
    public List<AuditLog> getAuditLogsByUser(UUID userId) {
        return getAuditLogsByUser(userId, 1000); // Default limit
    }
    
    /**
     * Get audit logs for a specific user with limit
     */
    @Transactional(readOnly = true)
    public List<AuditLog> getAuditLogsByUser(UUID userId, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        Page<AuditLog> page = auditLogRepository.findByUserId(userId, pageable);
        return page.getContent();
    }
    
    /**
     * Get audit logs by action type
     */
    @Transactional(readOnly = true)
    public List<AuditLog> getAuditLogsByAction(String action) {
        return auditLogRepository.findByAction(action);
    }
    
    /**
     * Get audit logs for a specific resource
     */
    @Transactional(readOnly = true)
    public List<AuditLog> getAuditLogsByResource(String resourceType, String resourceId) {
        return auditLogRepository.findByResource(resourceType, resourceId);
    }
    
    /**
     * Clean up old audit logs (older than specified days)
     */
    public void cleanupOldLogs(int daysToKeep) {
        log.info("Cleaning up audit logs older than {} days", daysToKeep);
        
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);
        auditLogRepository.deleteLogsOlderThan(cutoffDate);
        
        log.info("Old audit logs cleaned up (older than {})", cutoffDate);
    }
}
