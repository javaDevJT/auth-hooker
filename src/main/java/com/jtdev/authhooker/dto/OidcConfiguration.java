package com.jtdev.authhooker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * OIDC Provider discovery document
 * From /.well-known/openid-configuration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OidcConfiguration {
    
    /**
     * Issuer identifier (base URL)
     */
    @JsonProperty("issuer")
    private String issuer;
    
    /**
     * OAuth 2.0 authorization endpoint URL
     */
    @JsonProperty("authorization_endpoint")
    private String authorizationEndpoint;
    
    /**
     * OAuth 2.0 token endpoint URL
     */
    @JsonProperty("token_endpoint")
    private String tokenEndpoint;
    
    /**
     * OIDC UserInfo endpoint URL
     */
    @JsonProperty("userinfo_endpoint")
    private String userinfoEndpoint;
    
    /**
     * JWKS (JSON Web Key Set) endpoint URL
     */
    @JsonProperty("jwks_uri")
    private String jwksUri;
    
    /**
     * OAuth 2.0 revocation endpoint URL (optional)
     */
    @JsonProperty("revocation_endpoint")
    private String revocationEndpoint;
    
    /**
     * End session endpoint for logout (optional)
     */
    @JsonProperty("end_session_endpoint")
    private String endSessionEndpoint;
    
    /**
     * Supported scopes
     */
    @JsonProperty("scopes_supported")
    private List<String> scopesSupported;
    
    /**
     * Supported response types
     */
    @JsonProperty("response_types_supported")
    private List<String> responseTypesSupported;
    
    /**
     * Supported grant types
     */
    @JsonProperty("grant_types_supported")
    private List<String> grantTypesSupported;
    
    /**
     * Supported subject types
     */
    @JsonProperty("subject_types_supported")
    private List<String> subjectTypesSupported;
    
    /**
     * Supported ID token signing algorithms
     */
    @JsonProperty("id_token_signing_alg_values_supported")
    private List<String> idTokenSigningAlgValuesSupported;
    
    /**
     * Supported claims
     */
    @JsonProperty("claims_supported")
    private List<String> claimsSupported;
    
    /**
     * Supported code challenge methods (PKCE)
     */
    @JsonProperty("code_challenge_methods_supported")
    private List<String> codeChallengeMethodsSupported;
}
