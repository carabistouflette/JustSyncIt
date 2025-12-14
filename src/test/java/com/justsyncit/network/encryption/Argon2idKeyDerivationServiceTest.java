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

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for Argon2idKeyDerivationService.
 */
class Argon2idKeyDerivationServiceTest {

    private Argon2idKeyDerivationService kdService;

    @BeforeEach
    void setUp() {
        // Use smaller memory for faster tests
        kdService = new Argon2idKeyDerivationService(64, 1, 1);
    }

    @Test
    @DisplayName("Same password and salt should produce same key")
    void testDeterministicKeyDerivation() throws EncryptionException {
        char[] password = "mySecurePassword123!".toCharArray();
        byte[] salt = kdService.generateSalt();

        byte[] key1 = kdService.deriveKey(password, salt, 32);
        byte[] key2 = kdService.deriveKey(password, salt, 32);

        assertArrayEquals(key1, key2, "Same password and salt should produce same key");
    }

    @Test
    @DisplayName("Different salt should produce different key")
    void testDifferentSaltProducesDifferentKey() throws EncryptionException {
        char[] password = "samePassword".toCharArray();
        byte[] salt1 = kdService.generateSalt();
        byte[] salt2 = kdService.generateSalt();

        byte[] key1 = kdService.deriveKey(password, salt1, 32);
        byte[] key2 = kdService.deriveKey(password, salt2, 32);

        assertFalse(Arrays.equals(key1, key2), "Different salts should produce different keys");
    }

    @Test
    @DisplayName("Different password should produce different key")
    void testDifferentPasswordProducesDifferentKey() throws EncryptionException {
        char[] password1 = "password1".toCharArray();
        char[] password2 = "password2".toCharArray();
        byte[] salt = kdService.generateSalt();

        byte[] key1 = kdService.deriveKey(password1, salt, 32);
        byte[] key2 = kdService.deriveKey(password2, salt, 32);

        assertFalse(Arrays.equals(key1, key2), "Different passwords should produce different keys");
    }

    @Test
    @DisplayName("Output length should match requested size")
    void testOutputLengthMatchesRequestedSize() throws EncryptionException {
        char[] password = "test".toCharArray();
        byte[] salt = kdService.generateSalt();

        byte[] key16 = kdService.deriveKey(password, salt, 16);
        byte[] key32 = kdService.deriveKey(password, salt, 32);
        byte[] key64 = kdService.deriveKey(password, salt, 64);

        assertEquals(16, key16.length);
        assertEquals(32, key32.length);
        assertEquals(64, key64.length);
    }

    @Test
    @DisplayName("Generated salt should have correct size")
    void testGeneratedSaltSize() {
        byte[] salt = kdService.generateSalt();

        assertEquals(kdService.getSaltSize(), salt.length);
        assertEquals(16, salt.length);
    }

    @Test
    @DisplayName("Salt should be random (unique)")
    void testSaltRandomness() {
        byte[] salt1 = kdService.generateSalt();
        byte[] salt2 = kdService.generateSalt();

        assertFalse(Arrays.equals(salt1, salt2), "Generated salts should be unique");
    }

    @Test
    @DisplayName("Null password should throw exception")
    void testNullPasswordThrows() {
        byte[] salt = kdService.generateSalt();

        assertThrows(EncryptionException.class,
                () -> kdService.deriveKey(null, salt, 32));
    }

    @Test
    @DisplayName("Empty password should throw exception")
    void testEmptyPasswordThrows() {
        byte[] salt = kdService.generateSalt();
        char[] emptyPassword = new char[0];

        assertThrows(EncryptionException.class,
                () -> kdService.deriveKey(emptyPassword, salt, 32));
    }

    @Test
    @DisplayName("Null salt should throw exception")
    void testNullSaltThrows() {
        char[] password = "test".toCharArray();

        assertThrows(EncryptionException.class,
                () -> kdService.deriveKey(password, null, 32));
    }

    @Test
    @DisplayName("Salt too short should throw exception")
    void testSaltTooShortThrows() {
        char[] password = "test".toCharArray();
        byte[] shortSalt = new byte[4];

        assertThrows(EncryptionException.class,
                () -> kdService.deriveKey(password, shortSalt, 32));
    }

    @Test
    @DisplayName("Invalid output length should throw exception")
    void testInvalidOutputLengthThrows() {
        char[] password = "test".toCharArray();
        byte[] salt = kdService.generateSalt();

        assertThrows(EncryptionException.class,
                () -> kdService.deriveKey(password, salt, 0));

        assertThrows(EncryptionException.class,
                () -> kdService.deriveKey(password, salt, -1));
    }

    @Test
    @DisplayName("Default derive method should return 32-byte key")
    void testDefaultDeriveReturns256BitKey() throws EncryptionException {
        char[] password = "test".toCharArray();
        byte[] salt = kdService.generateSalt();

        byte[] key = kdService.deriveKey(password, salt);

        assertEquals(32, key.length);
    }

    @Test
    @DisplayName("Algorithm name should be correct")
    void testAlgorithmName() {
        assertEquals("Argon2id", kdService.getAlgorithmName());
    }

    @Test
    @DisplayName("Custom parameters should be stored correctly")
    void testCustomParameters() {
        Argon2idKeyDerivationService custom = new Argon2idKeyDerivationService(1024, 5, 2);

        assertEquals(1024, custom.getMemoryKb());
        assertEquals(5, custom.getIterations());
        assertEquals(2, custom.getParallelism());
    }

    @Test
    @DisplayName("Default constructor should set standard parameters")
    void testDefaultConstructorParameters() {
        Argon2idKeyDerivationService standard = new Argon2idKeyDerivationService();

        assertEquals(512, standard.getMemoryKb());
        assertEquals(3, standard.getIterations());
        assertEquals(4, standard.getParallelism());
    }
}
