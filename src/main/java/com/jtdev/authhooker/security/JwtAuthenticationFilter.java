package com.jtdev.authhooker.security;

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
import java.util.UUID;

/**
 * Filter for JWT-based authentication.
 * Extracts Bearer token from Authorization header and validates it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtService jwtService;
    
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        
        try {
            String authHeader = request.getHeader("Authorization");
            
            // Skip if no Authorization header or not Bearer token
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                filterChain.doFilter(request, response);
                return;
            }
            
            // Extract token
            String token = authHeader.substring(7);
            
            // Validate token
            if (!jwtService.validateToken(token)) {
                log.debug("Invalid JWT token");
                filterChain.doFilter(request, response);
                return;
            }
            
            // Extract tenant ID and email
            UUID tenantId = jwtService.extractTenantId(token);
            String email = jwtService.extractEmail(token);
            
            if (tenantId == null || email == null) {
                log.debug("JWT token missing required claims");
                filterChain.doFilter(request, response);
                return;
            }
            
            // Set tenant context
            TenantContext.setTenantId(tenantId);
            
            // Set authentication in SecurityContext
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    email,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"))
            );
            
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            log.debug("JWT authentication successful for tenant: {}", tenantId);
            
        } catch (Exception e) {
            log.error("JWT authentication failed: {}", e.getMessage());
        }
        
        filterChain.doFilter(request, response);
    }
    
    @Override
    protected void afterRequest(HttpServletRequest request, HttpServletResponse response) {
        // Clear tenant context after request
        TenantContext.clear();
    }
}
