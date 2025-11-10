package com.jtdev.authhooker.filter;

import com.jtdev.authhooker.security.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter for logging all incoming HTTP requests
 * Generates correlation ID for request tracing
 */
@Slf4j
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {
    
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String MDC_CORRELATION_ID = "correlationId";
    private static final String MDC_TENANT_ID = "tenantId";
    
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        
        long startTime = System.currentTimeMillis();
        
        // Generate or extract correlation ID
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        
        // Add correlation ID to MDC for logging
        MDC.put(MDC_CORRELATION_ID, correlationId);
        
        // Add correlation ID to response headers
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        
        try {
            // Log request
            log.info("=> {} {} from {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getRemoteAddr());
            
            // Process request
            filterChain.doFilter(request, response);
            
            // Calculate duration
            long duration = System.currentTimeMillis() - startTime;
            
            // Add tenant ID to MDC if available
            if (TenantContext.isSet()) {
                MDC.put(MDC_TENANT_ID, TenantContext.getTenantId().toString());
            }
            
            // Log response
            log.info("<= {} {} {} ({}ms)",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    duration);
            
        } finally {
            // Clear MDC
            MDC.remove(MDC_CORRELATION_ID);
            MDC.remove(MDC_TENANT_ID);
        }
    }
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Skip logging for actuator endpoints to reduce noise
        String path = request.getRequestURI();
        return path.startsWith("/actuator/");
    }
}
