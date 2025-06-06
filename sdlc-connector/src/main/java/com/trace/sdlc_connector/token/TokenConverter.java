package com.trace.sdlc_connector.token;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

@Converter
public class TokenConverter implements AttributeConverter<String, String> {

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final String ALGORITHM = "AES/GCM/NoPadding";

    @Value("${trace.token-secret}")
    private String secretKey;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;

        try {
            return encrypt(attribute);
        } catch (Exception e) {
            logger.error("Error encrypting token", e);
            throw new RuntimeException("Error encrypting token", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;

        try {
            return decrypt(dbData);
        } catch (Exception e) {
            logger.error("Error decrypting token", e);
            throw new RuntimeException("Error decrypting token", e);
        }
    }

    private String encrypt(String data) throws Exception {
        // Generate random IV
        byte[] iv = new byte[GCM_IV_LENGTH];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);

        // Create cipher instance
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, getKey(), parameterSpec);

        // Encrypt the data
        byte[] encryptedData = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));

        // Combine IV and encrypted data
        ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + encryptedData.length);
        byteBuffer.put(iv);
        byteBuffer.put(encryptedData);

        // Return as Base64 encoded string
        return Base64.getEncoder().encodeToString(byteBuffer.array());
    }

    private String decrypt(String encryptedData) throws Exception {
        // Decode from Base64
        byte[] decodedData = Base64.getDecoder().decode(encryptedData);

        // Extract IV and ciphertext
        ByteBuffer byteBuffer = ByteBuffer.wrap(decodedData);
        byte[] iv = new byte[GCM_IV_LENGTH];
        byteBuffer.get(iv);

        byte[] cipherText = new byte[byteBuffer.remaining()];
        byteBuffer.get(cipherText);

        // Initialize cipher for decryption
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, getKey(), parameterSpec);

        // Decrypt the data
        byte[] decryptedData = cipher.doFinal(cipherText);
        return new String(decryptedData, StandardCharsets.UTF_8);
    }

    private SecretKey getKey() {
        // Prepare key with proper length (16, 24, or 32 bytes for AES-128, AES-192, or AES-256)
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        byte[] validKey = new byte[32]; // Using AES-256

        if (keyBytes.length >= 32) {
            System.arraycopy(keyBytes, 0, validKey, 0, 32);
        } else {
            System.arraycopy(keyBytes, 0, validKey, 0, keyBytes.length);
            // Zero-pad if needed
            for (int i = keyBytes.length; i < 32; i++) {
                validKey[i] = 0;
            }
        }

        return new SecretKeySpec(validKey, "AES");
    }
}