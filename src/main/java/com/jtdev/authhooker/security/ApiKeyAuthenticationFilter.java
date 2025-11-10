package com.jtdev.authhooker.security;

import com.jtdev.authhooker.domain.Tenant;
import com.jtdev.authhooker.repository.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Filter for API Key-based authentication.
 * Extracts X-API-Key header and validates it against tenant API keys.
 * Used by bots and plugins.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    
    private final TenantRepository tenantRepository;
    
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        
        try {
            String apiKey = request.getHeader("X-API-Key");
            
            // Skip if no API key header
            if (apiKey == null || apiKey.isBlank()) {
                filterChain.doFilter(request, response);
                return;
            }
            
            // Find tenant by API key
            // Note: In production, consider adding an api_key column with index for performance
            // For now, we'll check settings.api_key
            Optional<Tenant> tenantOpt = tenantRepository.findAll().stream()
                    .filter(t -> {
                        Object storedKey = t.getSettings().get("api_key");
                        return storedKey != null && storedKey.toString().equals(apiKey);
                    })
                    .filter(t -> t.getDeletedAt() == null)
                    .filter(t -> "active".equals(t.getStatus()))
                    .findFirst();
            
            if (tenantOpt.isEmpty()) {
                log.debug("Invalid API key");
                filterChain.doFilter(request, response);
                return;
            }
            
            Tenant tenant = tenantOpt.get();
            
            // Set tenant context
            TenantContext.setTenantId(tenant.getId());
            
            // Set authentication in SecurityContext
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    tenant.getOwnerEmail(),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_API_CLIENT"))
            );
            
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            log.debug("API key authentication successful for tenant: {}", tenant.getId());
            
        } catch (Exception e) {
            log.error("API key authentication failed: {}", e.getMessage());
        }
        
        filterChain.doFilter(request, response);
    }
    
    @Override
    protected void afterRequest(HttpServletRequest request, HttpServletResponse response) {
        // Clear tenant context after request
        TenantContext.clear();
    }
}
