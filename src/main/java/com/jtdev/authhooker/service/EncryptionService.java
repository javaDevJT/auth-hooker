package com.jtdev.authhooker.service;

import com.jtdev.authhooker.exception.EncryptionException;
import com.jtdev.authhooker.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service for encrypting and decrypting sensitive data using AES-256-GCM
 */
@Slf4j
@Service
public class EncryptionService {
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final int AES_KEY_SIZE = 256;
    
    private final SecretKey secretKey;
    private final SecureRandom secureRandom;
    
    public EncryptionService(@Value("${app.security.encryption-key:}") String encryptionKey) {
        this.secureRandom = new SecureRandom();
        
        if (encryptionKey == null || encryptionKey.isBlank()) {
            log.warn("No encryption key provided, generating a random key. " +
                    "This key will not persist across restarts!");
            this.secretKey = generateKey();
        } else {
            try {
                byte[] decodedKey = Base64.getDecoder().decode(encryptionKey);
                if (decodedKey.length != 32) {
                    throw new IllegalArgumentException(
                        "Encryption key must be 32 bytes (256 bits) when base64 decoded");
                }
                this.secretKey = new SecretKeySpec(decodedKey, "AES");
                log.info("Encryption service initialized with provided key");
            } catch (Exception e) {
                throw new EncryptionException("Failed to initialize encryption key", e);
            }
        }
    }
    
    /**
     * Encrypt plaintext using AES-256-GCM
     * 
     * @param plaintext the text to encrypt
     * @return base64-encoded encrypted data with IV prepended
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            throw new ValidationException("Plaintext cannot be null or empty");
        }
        
        try {
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
            
            // Encrypt
            byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
            byte[] ciphertext = cipher.doFinal(plaintextBytes);
            
            // Combine IV + ciphertext
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);
            
            // Return base64-encoded result
            return Base64.getEncoder().encodeToString(byteBuffer.array());
            
        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new EncryptionException("Failed to encrypt data", e);
        }
    }
    
    /**
     * Decrypt encrypted data using AES-256-GCM
     * 
     * @param encrypted base64-encoded encrypted data with IV prepended
     * @return decrypted plaintext
     */
    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) {
            throw new ValidationException("Encrypted data cannot be null or empty");
        }
        
        try {
            // Decode base64
            byte[] decodedBytes = Base64.getDecoder().decode(encrypted);
            
            // Extract IV and ciphertext
            ByteBuffer byteBuffer = ByteBuffer.wrap(decodedBytes);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] ciphertext = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertext);
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
            
            // Decrypt
            byte[] plaintextBytes = cipher.doFinal(ciphertext);
            
            return new String(plaintextBytes, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new EncryptionException("Failed to decrypt data", e);
        }
    }
    
    /**
     * Generate a secure random encryption key
     * 
     * @return base64-encoded 256-bit key
     */
    public String generateSecureRandomKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(AES_KEY_SIZE, secureRandom);
            SecretKey key = keyGenerator.generateKey();
            return Base64.getEncoder().encodeToString(key.getEncoded());
        } catch (Exception e) {
            throw new EncryptionException("Failed to generate encryption key", e);
        }
    }
    
    private SecretKey generateKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(AES_KEY_SIZE, secureRandom);
            return keyGenerator.generateKey();
        } catch (Exception e) {
            throw new EncryptionException("Failed to generate encryption key", e);
        }
    }
}
