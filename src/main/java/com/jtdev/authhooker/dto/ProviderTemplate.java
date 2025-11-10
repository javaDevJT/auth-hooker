package com.jtdev.authhooker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Predefined provider templates for common OAuth/OIDC providers
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderTemplate {
    
    private String providerType;
    private String displayName;
    private String description;
    private String iconUrl;
    private List<String> requiredScopes;
    private Map<String, Object> defaultConfig;
    private List<String> supportedFeatures;
    
    /**
     * Get predefined templates for common providers
     */
    public static List<ProviderTemplate> getDefaultTemplates() {
        return List.of(
            // Google OAuth/OIDC
            ProviderTemplate.builder()
                .providerType("google")
                .displayName("Google")
                .description("Sign in with Google Workspace or Gmail")
                .iconUrl("https://www.google.com/favicon.ico")
                .requiredScopes(List.of("openid", "profile", "email"))
                .defaultConfig(Map.of(
                    "issuer", "https://accounts.google.com",
                    "authorization_endpoint", "https://accounts.google.com/o/oauth2/v2/auth",
                    "token_endpoint", "https://oauth2.googleapis.com/token",
                    "userinfo_endpoint", "https://openidconnect.googleapis.com/v1/userinfo",
                    "jwks_uri", "https://www.googleapis.com/oauth2/v3/certs",
                    "scopes", "openid profile email"
                ))
                .supportedFeatures(List.of("OIDC", "PKCE", "Groups", "Email Verification"))
                .build(),
                
            // GitHub OAuth
            ProviderTemplate.builder()
                .providerType("github")
                .displayName("GitHub")
                .description("Sign in with GitHub")
                .iconUrl("https://github.com/favicon.ico")
                .requiredScopes(List.of("read:user", "user:email"))
                .defaultConfig(Map.of(
                    "authorization_endpoint", "https://github.com/login/oauth/authorize",
                    "token_endpoint", "https://github.com/login/oauth/access_token",
                    "userinfo_endpoint", "https://api.github.com/user",
                    "scopes", "read:user user:email"
                ))
                .supportedFeatures(List.of("OAuth2", "Organizations", "Teams"))
                .build(),
                
            // Microsoft / Azure AD
            ProviderTemplate.builder()
                .providerType("microsoft")
                .displayName("Microsoft")
                .description("Sign in with Microsoft / Azure AD")
                .iconUrl("https://www.microsoft.com/favicon.ico")
                .requiredScopes(List.of("openid", "profile", "email"))
                .defaultConfig(Map.of(
                    "issuer", "https://login.microsoftonline.com/common/v2.0",
                    "authorization_endpoint", "https://login.microsoftonline.com/common/oauth2/v2.0/authorize",
                    "token_endpoint", "https://login.microsoftonline.com/common/oauth2/v2.0/token",
                    "userinfo_endpoint", "https://graph.microsoft.com/oidc/userinfo",
                    "jwks_uri", "https://login.microsoftonline.com/common/discovery/v2.0/keys",
                    "scopes", "openid profile email"
                ))
                .supportedFeatures(List.of("OIDC", "PKCE", "Groups", "Roles"))
                .build(),
                
            // Discord (for Discord platform integration)
            ProviderTemplate.builder()
                .providerType("discord")
                .displayName("Discord")
                .description("Sign in with Discord")
                .iconUrl("https://discord.com/favicon.ico")
                .requiredScopes(List.of("identify", "email"))
                .defaultConfig(Map.of(
                    "authorization_endpoint", "https://discord.com/api/oauth2/authorize",
                    "token_endpoint", "https://discord.com/api/oauth2/token",
                    "userinfo_endpoint", "https://discord.com/api/users/@me",
                    "scopes", "identify email guilds"
                ))
                .supportedFeatures(List.of("OAuth2", "Guilds", "Roles"))
                .build(),
                
            // Custom OIDC Provider
            ProviderTemplate.builder()
                .providerType("custom-oidc")
                .displayName("Custom OIDC Provider")
                .description("Any OpenID Connect compliant provider")
                .iconUrl(null)
                .requiredScopes(List.of("openid", "profile", "email"))
                .defaultConfig(Map.of(
                    "scopes", "openid profile email"
                ))
                .supportedFeatures(List.of("OIDC", "PKCE", "Discovery"))
                .build()
        );
    }
}
