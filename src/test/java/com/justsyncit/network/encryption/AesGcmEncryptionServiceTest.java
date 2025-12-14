/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.justsyncit.network.encryption;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for AesGcmEncryptionService.
 */
class AesGcmEncryptionServiceTest {

    private AesGcmEncryptionService encryptionService;
    private byte[] testKey;

    @BeforeEach
    void setUp() {
        encryptionService = new AesGcmEncryptionService();
        testKey = new byte[32]; // 256-bit key
        new SecureRandom().nextBytes(testKey);
    }

    @Test
    @DisplayName("Encrypt and decrypt roundtrip should preserve plaintext")
    void testEncryptDecryptRoundtrip() throws EncryptionException {
        byte[] plaintext = "Hello, World! This is a test message.".getBytes();

        byte[] ciphertext = encryptionService.encrypt(plaintext, testKey);
        byte[] decrypted = encryptionService.decrypt(ciphertext, testKey);

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Each encryption should produce unique output (unique IV)")
    void testUniqueIvPerEncryption() throws EncryptionException {
        byte[] plaintext = "Same plaintext".getBytes();

        byte[] ciphertext1 = encryptionService.encrypt(plaintext, testKey);
        byte[] ciphertext2 = encryptionService.encrypt(plaintext, testKey);

        // Ciphertexts should be different due to unique IVs
        assertFalse(Arrays.equals(ciphertext1, ciphertext2),
                "Ciphertexts should differ due to unique IVs");

        // But both should decrypt to same plaintext
        assertArrayEquals(plaintext, encryptionService.decrypt(ciphertext1, testKey));
        assertArrayEquals(plaintext, encryptionService.decrypt(ciphertext2, testKey));
    }

    @Test
    @DisplayName("Tampered ciphertext should fail authentication")
    void testAuthenticationFailsOnTamperedData() throws EncryptionException {
        byte[] plaintext = "Sensitive data".getBytes();
        byte[] ciphertext = encryptionService.encrypt(plaintext, testKey);

        // Tamper with ciphertext (modify a byte in the middle)
        int tamperIndex = ciphertext.length - 20;
        ciphertext[tamperIndex] = (byte) (ciphertext[tamperIndex] ^ 0xFF);

        EncryptionException ex = assertThrows(EncryptionException.class,
                () -> encryptionService.decrypt(ciphertext, testKey));

        assertTrue(ex.getMessage().contains("tampered") || ex.getMessage().contains("Authentication"));
    }

    @Test
    @DisplayName("Associated data should be validated during decryption")
    void testAssociatedDataValidation() throws EncryptionException {
        byte[] plaintext = "Protected data".getBytes();
        byte[] associatedData = "transfer-123".getBytes();
        byte[] wrongAssociatedData = "transfer-456".getBytes();

        byte[] ciphertext = encryptionService.encrypt(plaintext, testKey, associatedData);

        // Decryption with correct AAD should succeed
        byte[] decrypted = encryptionService.decrypt(ciphertext, testKey, associatedData);
        assertArrayEquals(plaintext, decrypted);

        // Decryption with wrong AAD should fail
        assertThrows(EncryptionException.class,
                () -> encryptionService.decrypt(ciphertext, testKey, wrongAssociatedData));
    }

    @Test
    @DisplayName("Empty plaintext should be handled correctly")
    void testEmptyPlaintext() throws EncryptionException {
        byte[] plaintext = new byte[0];

        byte[] ciphertext = encryptionService.encrypt(plaintext, testKey);
        byte[] decrypted = encryptionService.decrypt(ciphertext, testKey);

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Large data encryption should work correctly")
    void testLargeDataEncryption() throws EncryptionException {
        // 1 MB of data
        byte[] plaintext = new byte[1024 * 1024];
        new SecureRandom().nextBytes(plaintext);

        byte[] ciphertext = encryptionService.encrypt(plaintext, testKey);
        byte[] decrypted = encryptionService.decrypt(ciphertext, testKey);

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Invalid key size should throw exception")
    void testInvalidKeySize() {
        byte[] shortKey = new byte[16]; // 128-bit instead of 256-bit
        byte[] plaintext = "test".getBytes();

        assertThrows(EncryptionException.class,
                () -> encryptionService.encrypt(plaintext, shortKey));
    }

    @Test
    @DisplayName("Null key should throw exception")
    void testNullKey() {
        byte[] plaintext = "test".getBytes();

        assertThrows(EncryptionException.class,
                () -> encryptionService.encrypt(plaintext, null));
    }

    @Test
    @DisplayName("Ciphertext too short should throw exception")
    void testCiphertextTooShort() {
        byte[] shortCiphertext = new byte[10]; // Too short

        assertThrows(EncryptionException.class,
                () -> encryptionService.decrypt(shortCiphertext, testKey));
    }

    @Test
    @DisplayName("IV extraction should generate unique IVs")
    void testIvUniqueness() throws EncryptionException {
        Set<String> ivSet = new HashSet<>();
        byte[] plaintext = "test".getBytes();

        // Generate 100 ciphertexts and check IV uniqueness
        for (int i = 0; i < 100; i++) {
            byte[] ciphertext = encryptionService.encrypt(plaintext, testKey);
            // IV is first 12 bytes
            byte[] iv = Arrays.copyOf(ciphertext, 12);
            String ivHex = bytesToHex(iv);
            assertTrue(ivSet.add(ivHex), "IV should be unique: " + ivHex);
        }
    }

    @Test
    @DisplayName("Algorithm metadata should be correct")
    void testAlgorithmMetadata() {
        assertEquals("AES-256-GCM", encryptionService.getAlgorithmName());
        assertEquals(32, encryptionService.getKeySize());
        assertEquals(12, encryptionService.getIvSize());
    }

    @Test
    @DisplayName("Wrong key should fail decryption")
    void testWrongKeyFailsDecryption() throws EncryptionException {
        byte[] plaintext = "Secret message".getBytes();
        byte[] ciphertext = encryptionService.encrypt(plaintext, testKey);

        byte[] wrongKey = new byte[32];
        new SecureRandom().nextBytes(wrongKey);

        assertThrows(EncryptionException.class,
                () -> encryptionService.decrypt(ciphertext, wrongKey));
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
