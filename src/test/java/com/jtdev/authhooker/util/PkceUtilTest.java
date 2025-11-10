package com.jtdev.authhooker.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PkceUtil
 */
class PkceUtilTest {
    
    @Test
    void generateCodeVerifier_shouldReturnValidString() {
        // When
        String codeVerifier = PkceUtil.generateCodeVerifier();
        
        // Then
        assertNotNull(codeVerifier);
        assertFalse(codeVerifier.isEmpty());
        assertTrue(codeVerifier.length() >= 43 && codeVerifier.length() <= 128,
                "Code verifier should be between 43 and 128 characters");
        
        // Should only contain URL-safe characters
        assertTrue(codeVerifier.matches("[A-Za-z0-9_-]+"),
                "Code verifier should only contain URL-safe characters");
    }
    
    @Test
    void generateCodeVerifier_shouldGenerateUniqueValues() {
        // When
        String verifier1 = PkceUtil.generateCodeVerifier();
        String verifier2 = PkceUtil.generateCodeVerifier();
        
        // Then
        assertNotEquals(verifier1, verifier2,
                "Each code verifier should be unique");
    }
    
    @Test
    void generateCodeChallenge_shouldReturnValidString() {
        // Given
        String codeVerifier = PkceUtil.generateCodeVerifier();
        
        // When
        String codeChallenge = PkceUtil.generateCodeChallenge(codeVerifier);
        
        // Then
        assertNotNull(codeChallenge);
        assertFalse(codeChallenge.isEmpty());
        assertEquals(43, codeChallenge.length(),
                "SHA-256 base64url-encoded hash should be 43 characters");
        
        // Should only contain URL-safe characters
        assertTrue(codeChallenge.matches("[A-Za-z0-9_-]+"),
                "Code challenge should only contain URL-safe characters");
    }
    
    @Test
    void generateCodeChallenge_shouldBeConsistent() {
        // Given
        String codeVerifier = "test-verifier-12345";
        
        // When
        String challenge1 = PkceUtil.generateCodeChallenge(codeVerifier);
        String challenge2 = PkceUtil.generateCodeChallenge(codeVerifier);
        
        // Then
        assertEquals(challenge1, challenge2,
                "Same verifier should produce same challenge");
    }
    
    @Test
    void generateCodeChallenge_shouldThrowOnNullVerifier() {
        // When/Then
        assertThrows(IllegalArgumentException.class,
                () -> PkceUtil.generateCodeChallenge(null));
    }
    
    @Test
    void generateCodeChallenge_shouldThrowOnEmptyVerifier() {
        // When/Then
        assertThrows(IllegalArgumentException.class,
                () -> PkceUtil.generateCodeChallenge(""));
        assertThrows(IllegalArgumentException.class,
                () -> PkceUtil.generateCodeChallenge("   "));
    }
    
    @Test
    void verifyCodeChallenge_shouldReturnTrueForValidPair() {
        // Given
        String codeVerifier = PkceUtil.generateCodeVerifier();
        String codeChallenge = PkceUtil.generateCodeChallenge(codeVerifier);
        
        // When
        boolean isValid = PkceUtil.verifyCodeChallenge(codeVerifier, codeChallenge);
        
        // Then
        assertTrue(isValid, "Valid verifier/challenge pair should verify");
    }
    
    @Test
    void verifyCodeChallenge_shouldReturnFalseForInvalidPair() {
        // Given
        String codeVerifier = PkceUtil.generateCodeVerifier();
        String wrongChallenge = PkceUtil.generateCodeChallenge(PkceUtil.generateCodeVerifier());
        
        // When
        boolean isValid = PkceUtil.verifyCodeChallenge(codeVerifier, wrongChallenge);
        
        // Then
        assertFalse(isValid, "Mismatched verifier/challenge pair should not verify");
    }
    
    @Test
    void verifyCodeChallenge_shouldReturnFalseForNullValues() {
        // When/Then
        assertFalse(PkceUtil.verifyCodeChallenge(null, "challenge"));
        assertFalse(PkceUtil.verifyCodeChallenge("verifier", null));
        assertFalse(PkceUtil.verifyCodeChallenge(null, null));
    }
}
