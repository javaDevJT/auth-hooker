package com.jtdev.authhooker.service;

import com.jtdev.authhooker.exception.EncryptionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for EncryptionService
 */
class EncryptionServiceTest {
    
    private EncryptionService encryptionService;
    
    @BeforeEach
    void setUp() {
        // Use a test encryption key (exactly 32 bytes when base64 decoded)
        // This is "test-encryption-key-32-bytes!" in base64
        String testKey = "dGVzdC1lbmNyeXB0aW9uLWtleS0zMi1ieXRlcyE="; 
        // Actually need exactly 32 bytes, let's use a proper one:
        // 32 bytes = 256 bits, base64 encoded
        byte[] keyBytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            keyBytes[i] = (byte) i;
        }
        testKey = java.util.Base64.getEncoder().encodeToString(keyBytes);
        encryptionService = new EncryptionService(testKey);
    }
    
    @Test
    void encrypt_shouldEncryptPlaintext() {
        // Given
        String plaintext = "my-secret-client-secret";
        
        // When
        String encrypted = encryptionService.encrypt(plaintext);
        
        // Then
        assertThat(encrypted).isNotNull();
        assertThat(encrypted).isNotEqualTo(plaintext);
        assertThat(encrypted).isBase64();
    }
    
    @Test
    void decrypt_shouldDecryptEncryptedText() {
        // Given
        String plaintext = "my-secret-client-secret";
        String encrypted = encryptionService.encrypt(plaintext);
        
        // When
        String decrypted = encryptionService.decrypt(encrypted);
        
        // Then
        assertThat(decrypted).isEqualTo(plaintext);
    }
    
    @Test
    void encryptDecrypt_shouldHandleSpecialCharacters() {
        // Given
        String plaintext = "Secret!@#$%^&*(){}[]|\\:;\"'<>,.?/~`+=";
        
        // When
        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);
        
        // Then
        assertThat(decrypted).isEqualTo(plaintext);
    }
    
    @Test
    void encryptDecrypt_shouldHandleUnicode() {
        // Given
        String plaintext = "Hello 世界 🌍 Привет";
        
        // When
        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);
        
        // Then
        assertThat(decrypted).isEqualTo(plaintext);
    }
    
    @Test
    void encrypt_shouldProduceDifferentCiphertextForSamePlaintext() {
        // Given
        String plaintext = "test-secret";
        
        // When
        String encrypted1 = encryptionService.encrypt(plaintext);
        String encrypted2 = encryptionService.encrypt(plaintext);
        
        // Then - different IV should produce different ciphertext
        assertThat(encrypted1).isNotEqualTo(encrypted2);
        
        // But both should decrypt to same plaintext
        assertThat(encryptionService.decrypt(encrypted1)).isEqualTo(plaintext);
        assertThat(encryptionService.decrypt(encrypted2)).isEqualTo(plaintext);
    }
    
    @Test
    void encrypt_shouldThrowExceptionForNullInput() {
        assertThatThrownBy(() -> encryptionService.encrypt(null))
                .isInstanceOf(Exception.class);
    }
    
    @Test
    void encrypt_shouldThrowExceptionForEmptyInput() {
        assertThatThrownBy(() -> encryptionService.encrypt(""))
                .isInstanceOf(Exception.class);
    }
    
    @Test
    void decrypt_shouldThrowExceptionForInvalidInput() {
        assertThatThrownBy(() -> encryptionService.decrypt("invalid-base64!@#"))
                .isInstanceOf(EncryptionException.class);
    }
    
    @Test
    void decrypt_shouldThrowExceptionForNullInput() {
        assertThatThrownBy(() -> encryptionService.decrypt(null))
                .isInstanceOf(Exception.class);
    }
    
    @Test
    void generateSecureRandomKey_shouldGenerateValidKey() {
        // When
        String key = encryptionService.generateSecureRandomKey();
        
        // Then
        assertThat(key).isNotNull();
        assertThat(key).isBase64();
        
        // Should be able to create a new service with this key
        EncryptionService newService = new EncryptionService(key);
        String plaintext = "test";
        String encrypted = newService.encrypt(plaintext);
        assertThat(newService.decrypt(encrypted)).isEqualTo(plaintext);
    }
}
