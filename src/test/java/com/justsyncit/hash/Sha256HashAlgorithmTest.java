/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.justsyncit.hash;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for Sha256HashAlgorithm.
 */
@DisplayName("Sha256HashAlgorithm Tests")
class Sha256HashAlgorithmTest {

    private Sha256HashAlgorithm algorithm;

    @BeforeEach
    void setUp() throws HashingException {
        algorithm = Sha256HashAlgorithm.create();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (algorithm != null) {
            algorithm.close();
        }
    }

    @Test
    @Timeout(5)
    @DisplayName("Should create instance successfully")
    void shouldCreateInstanceSuccessfully() throws HashingException, IOException {
        Sha256HashAlgorithm instance = Sha256HashAlgorithm.create();
        assertNotNull(instance);
        instance.close();
    }

    @Test
    @Timeout(5)
    @DisplayName("Should return correct algorithm name")
    void shouldReturnCorrectAlgorithmName() {
        assertEquals("SHA-256", algorithm.getAlgorithmName());
    }

    @Test
    @Timeout(5)
    @DisplayName("Should return correct hash length")
    void shouldReturnCorrectHashLength() {
        assertEquals(32, algorithm.getHashLength()); // 256 bits = 32 bytes
    }

    @Test
    @Timeout(5)
    @DisplayName("Should return correct block size")
    void shouldReturnCorrectBlockSize() {
        assertTrue(algorithm.getBlockSize().isPresent());
        assertEquals(64, algorithm.getBlockSize().get().intValue());
    }

    @Test
    @Timeout(5)
    @DisplayName("Should return correct security level")
    void shouldReturnCorrectSecurityLevel() {
        assertTrue(algorithm.getSecurityLevel().isPresent());
        assertEquals(128, algorithm.getSecurityLevel().get().intValue());
    }

    @Test
    @Timeout(5)
    @DisplayName("Should hash empty data")
    void shouldHashEmptyData() throws HashingException {
        byte[] result = algorithm.digest();
        assertNotNull(result);
        assertEquals(32, result.length);
    }

    @Test
    @Timeout(5)
    @DisplayName("Should hash simple byte array")
    void shouldHashSimpleByteArray() throws HashingException {
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
        algorithm.update(data);
        byte[] result = algorithm.digest();

        assertNotNull(result);
        assertEquals(32, result.length);
    }

    @Test
    @Timeout(5)
    @DisplayName("Should hash with offset and length")
    void shouldHashWithOffsetAndLength() throws HashingException {
        byte[] data = "hello world".getBytes(StandardCharsets.UTF_8);
        algorithm.update(data, 0, 5); // Only "hello"
        byte[] result = algorithm.digest();

        assertNotNull(result);
        assertEquals(32, result.length);
    }

    @Test
    @Timeout(5)
    @DisplayName("Should reset algorithm state")
    void shouldResetAlgorithmState() throws HashingException {
        byte[] data = "test".getBytes(StandardCharsets.UTF_8);
        algorithm.update(data);
        algorithm.reset();
        algorithm.update(data);
        byte[] result = algorithm.digest();

        assertNotNull(result);
        assertEquals(32, result.length);
    }

    @Test
    @Timeout(5)
    @DisplayName("Should create new instance for thread safety")
    void shouldCreateNewInstanceForThreadSafety() throws HashingException, IOException {
        assertTrue(algorithm.isThreadSafe()); // SHA-256 implementation is thread-safe
        HashAlgorithm newInstance = algorithm.createInstance();
        assertNotNull(newInstance);
        newInstance.close();
    }

    @Test
    @Timeout(5)
    @DisplayName("Should verify matching hash")
    void shouldVerifyMatchingHash() throws HashingException, IOException {
        byte[] data = "test".getBytes(StandardCharsets.UTF_8);
        algorithm.update(data);
        byte[] hash = algorithm.digest();

        // Create new instance for verification
        Sha256HashAlgorithm verifier = Sha256HashAlgorithm.create();
        verifier.update(data);
        assertTrue(verifier.verify(hash));
        verifier.close();
    }

    @Test
    @Timeout(5)
    @DisplayName("Should reject non-matching hash")
    void shouldRejectNonMatchingHash() throws HashingException {
        byte[] data = "test".getBytes(StandardCharsets.UTF_8);
        algorithm.update(data);
        byte[] wrongHash = new byte[32]; // All zeros

        assertFalse(algorithm.verify(wrongHash));
    }

    @Test
    @Timeout(5)
    @DisplayName("Should hash ByteBuffer")
    void shouldHashByteBuffer() throws HashingException {
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.wrap(data);

        algorithm.update(buffer);
        byte[] result = algorithm.digest();

        assertNotNull(result);
        assertEquals(32, result.length);
    }

    @Test
    @Timeout(5)
    @DisplayName("Should throw exception when closed")
    void shouldThrowExceptionWhenClosed() throws IOException {
        algorithm.close();

        assertThrows(IllegalStateException.class, () -> {
            algorithm.update(new byte[10]);
        });
    }

    @Test
    @Timeout(5)
    @DisplayName("Close should be idempotent")
    void closeShouldBeIdempotent() throws IOException {
        algorithm.close();
        algorithm.close(); // Should not throw
    }
}
