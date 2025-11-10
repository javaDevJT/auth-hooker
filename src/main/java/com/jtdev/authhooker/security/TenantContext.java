package com.jtdev.authhooker.security;

import java.util.UUID;

/**
 * ThreadLocal context for storing the current tenant ID.
 * Used for multi-tenant isolation in the application.
 */
public class TenantContext {
    
    private static final ThreadLocal<UUID> currentTenantId = new ThreadLocal<>();
    
    /**
     * Set the current tenant ID for this thread
     */
    public static void setTenantId(UUID tenantId) {
        currentTenantId.set(tenantId);
    }
    
    /**
     * Get the current tenant ID for this thread
     */
    public static UUID getTenantId() {
        return currentTenantId.get();
    }
    
    /**
     * Clear the tenant context for this thread
     * Should be called after request processing to prevent memory leaks
     */
    public static void clear() {
        currentTenantId.remove();
    }
    
    /**
     * Check if a tenant ID is currently set
     */
    public static boolean isSet() {
        return currentTenantId.get() != null;
    }
}
