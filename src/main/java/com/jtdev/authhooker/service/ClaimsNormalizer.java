package com.jtdev.authhooker.service;

import com.jtdev.authhooker.domain.Provider;
import com.jtdev.authhooker.dto.NormalizedClaims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for normalizing OAuth/OIDC claims from various providers
 * to a standardized format
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimsNormalizer {
    
    private final ClaimMappingService claimMappingService;
    
    /**
     * Normalize claims from various providers to standard format
     * 
     * @param rawClaims Claims from ID token
     * @param provider Provider configuration
     * @return Normalized claims
     */
    public NormalizedClaims normalize(Map<String, Object> rawClaims, Provider provider) {
        log.debug("Normalizing claims for provider: {} (type: {})", 
                provider.getId(), provider.getProviderType());
        
        if (rawClaims == null || rawClaims.isEmpty()) {
            throw new IllegalArgumentException("Raw claims cannot be null or empty");
        }
        
        String providerType = provider.getProviderType().toLowerCase();
        
        // Build normalized claims based on provider type
        NormalizedClaims.NormalizedClaimsBuilder builder = NormalizedClaims.builder();
        
        // Extract standard OIDC claims
        builder.subject(extractSubject(rawClaims, providerType));
        builder.email(extractEmail(rawClaims, providerType));
        builder.emailDomain(extractEmailDomain(extractEmail(rawClaims, providerType)));
        builder.name(extractName(rawClaims, providerType));
        builder.givenName(extractGivenName(rawClaims, providerType));
        builder.familyName(extractFamilyName(rawClaims, providerType));
        builder.avatarUrl(extractAvatarUrl(rawClaims, providerType));
        builder.verifiedEmail(extractEmailVerified(rawClaims, providerType));
        builder.groups(extractGroups(rawClaims, provider));
        builder.locale(extractLocale(rawClaims, providerType));
        
        // Store raw claims for reference
        builder.rawClaims(rawClaims);
        
        NormalizedClaims normalized = builder.build();
        
        log.debug("Claims normalized: subject={}, email={}, groups={}", 
                normalized.getSubject(), normalized.getEmail(), 
                normalized.getGroups() != null ? normalized.getGroups().size() : 0);
        
        return normalized;
    }
    
    /**
     * Extract email domain from email address
     * 
     * @param email Email address
     * @return Domain part (e.g., "gmail.com", "company.com")
     */
    public String extractEmailDomain(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return null;
        }
        
        int atIndex = email.indexOf('@');
        if (atIndex < 0 || atIndex == email.length() - 1) {
            return null;
        }
        
        String domain = email.substring(atIndex + 1);
        log.debug("Extracted email domain: {}", domain);
        
        return domain;
    }
    
    /**
     * Extract groups from provider-specific claim
     * 
     * Different providers put groups in different places:
     * - Google: "groups" array
     * - Microsoft: "groups" array
     * - GitHub: Need to fetch via API or use custom mapping
     * - Custom: Configured in claim_mappings
     */
    public List<String> extractGroups(Map<String, Object> claims, Provider provider) {
        log.debug("Extracting groups for provider type: {}", provider.getProviderType());
        
        List<String> groups = new ArrayList<>();
        String providerType = provider.getProviderType().toLowerCase();
        
        // Try standard "groups" claim first
        Object groupsClaim = claims.get("groups");
        if (groupsClaim instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> groupsList = (List<Object>) groupsClaim;
            for (Object group : groupsList) {
                if (group != null) {
                    groups.add(group.toString());
                }
            }
        }
        
        // Provider-specific group extraction
        switch (providerType) {
            case "google":
                // Google Workspace groups are in the "groups" claim
                // Already extracted above
                break;
                
            case "microsoft":
            case "azure":
            case "azuread":
                // Azure AD groups
                // Groups might also be in "roles" claim
                Object rolesClaim = claims.get("roles");
                if (rolesClaim instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> rolesList = (List<Object>) rolesClaim;
                    for (Object role : rolesList) {
                        if (role != null && !groups.contains(role.toString())) {
                            groups.add(role.toString());
                        }
                    }
                }
                break;
                
            case "github":
                // GitHub doesn't provide groups in ID token
                // Would need to fetch organizations/teams via API
                // For now, we can check if there's a custom groups claim
                log.debug("GitHub groups need to be fetched separately via API");
                break;
                
            case "discord":
                // Discord might have roles in custom claim
                Object discordRoles = claims.get("discord_roles");
                if (discordRoles instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> rolesList = (List<Object>) discordRoles;
                    for (Object role : rolesList) {
                        if (role != null) {
                            groups.add(role.toString());
                        }
                    }
                }
                break;
                
            default:
                // For custom OIDC providers, check claim mappings
                // This allows custom group claim paths
                log.debug("Using custom claim mappings for provider type: {}", providerType);
                break;
        }
        
        log.debug("Extracted {} groups", groups.size());
        return groups;
    }
    
    // Private helper methods for extracting specific claims
    
    private String extractSubject(Map<String, Object> claims, String providerType) {
        // Standard OIDC "sub" claim
        Object sub = claims.get("sub");
        if (sub != null) {
            return sub.toString();
        }
        
        // Fallback to provider-specific IDs
        switch (providerType) {
            case "github":
                Object id = claims.get("id");
                return id != null ? id.toString() : null;
            default:
                return null;
        }
    }
    
    private String extractEmail(Map<String, Object> claims, String providerType) {
        Object email = claims.get("email");
        return email != null ? email.toString() : null;
    }
    
    private Boolean extractEmailVerified(Map<String, Object> claims, String providerType) {
        Object emailVerified = claims.get("email_verified");
        
        if (emailVerified instanceof Boolean) {
            return (Boolean) emailVerified;
        } else if (emailVerified instanceof String) {
            return Boolean.parseBoolean((String) emailVerified);
        }
        
        // GitHub emails are always verified if returned
        if ("github".equals(providerType)) {
            return claims.containsKey("email");
        }
        
        return null;
    }
    
    private String extractName(Map<String, Object> claims, String providerType) {
        // Try standard "name" claim
        Object name = claims.get("name");
        if (name != null) {
            return name.toString();
        }
        
        // Fallback: combine given_name and family_name
        Object givenName = claims.get("given_name");
        Object familyName = claims.get("family_name");
        
        if (givenName != null && familyName != null) {
            return givenName.toString() + " " + familyName.toString();
        } else if (givenName != null) {
            return givenName.toString();
        } else if (familyName != null) {
            return familyName.toString();
        }
        
        // Provider-specific fallbacks
        switch (providerType) {
            case "github":
                Object login = claims.get("login");
                return login != null ? login.toString() : null;
            default:
                return null;
        }
    }
    
    private String extractGivenName(Map<String, Object> claims, String providerType) {
        Object givenName = claims.get("given_name");
        return givenName != null ? givenName.toString() : null;
    }
    
    private String extractFamilyName(Map<String, Object> claims, String providerType) {
        Object familyName = claims.get("family_name");
        return familyName != null ? familyName.toString() : null;
    }
    
    private String extractAvatarUrl(Map<String, Object> claims, String providerType) {
        // Standard "picture" claim
        Object picture = claims.get("picture");
        if (picture != null) {
            return picture.toString();
        }
        
        // Provider-specific avatar URLs
        switch (providerType) {
            case "github":
                Object avatarUrl = claims.get("avatar_url");
                return avatarUrl != null ? avatarUrl.toString() : null;
            case "discord":
                Object discordAvatar = claims.get("avatar");
                return discordAvatar != null ? discordAvatar.toString() : null;
            default:
                return null;
        }
    }
    
    private String extractLocale(Map<String, Object> claims, String providerType) {
        Object locale = claims.get("locale");
        return locale != null ? locale.toString() : null;
    }
}
