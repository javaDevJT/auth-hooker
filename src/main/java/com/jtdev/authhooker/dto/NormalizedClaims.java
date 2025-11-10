package com.jtdev.authhooker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Normalized OAuth/OIDC claims in standardized format
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NormalizedClaims {
    
    /**
     * Unique subject identifier from identity provider
     */
    private String subject;
    
    /**
     * User's email address
     */
    private String email;
    
    /**
     * Email domain (e.g., "gmail.com", "company.com")
     */
    private String emailDomain;
    
    /**
     * Display name
     */
    private String name;
    
    /**
     * List of groups/roles from identity provider
     */
    @Builder.Default
    private List<String> groups = List.of();
    
    /**
     * Profile picture URL
     */
    private String avatarUrl;
    
    /**
     * Whether email is verified by provider
     */
    private Boolean verifiedEmail;
    
    /**
     * Raw claims from ID token (for debugging/audit)
     */
    private Map<String, Object> rawClaims;
    
    /**
     * Given name / first name
     */
    private String givenName;
    
    /**
     * Family name / last name
     */
    private String familyName;
    
    /**
     * Locale/language preference
     */
    private String locale;
}
