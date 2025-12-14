/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.justsyncit.network.transfer.pipeline;

import com.justsyncit.network.encryption.AesGcmEncryptionService;
import com.justsyncit.network.encryption.EncryptionService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for EncryptStage.
 */
class EncryptStageTest {

    @TempDir
    Path tempDir;

    private ExecutorService executor;
    private EncryptionService encryptionService;
    private byte[] encryptionKey;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
        encryptionService = new AesGcmEncryptionService();
        encryptionKey = new byte[32];
        new SecureRandom().nextBytes(encryptionKey);
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    @DisplayName("Encrypt stage encrypts processed data")
    void testEncryptStageEncryptsData() throws Exception {
        EncryptStage stage = new EncryptStage(
                executor, encryptionService, encryptionKey, true, "testKey");

        ChunkTask task = new ChunkTask("transfer-123", tempDir.resolve("test.dat"),
                0, 100, 100);
        byte[] originalData = "Hello, World!".getBytes();
        task.setProcessedData(originalData);

        CompletableFuture<ChunkTask> result = stage.process(task);
        ChunkTask processed = result.join();

        assertNotNull(processed.getProcessedData());
        assertTrue(processed.isEncrypted());
        assertEquals("testKey", processed.getKeyAlias());

        // Encrypted data should be different from original
        assertFalse(Arrays.equals(originalData, processed.getProcessedData()));
    }

    @Test
    @DisplayName("Disabled encryption passes through unchanged")
    void testDisabledEncryptionPassthrough() throws Exception {
        EncryptStage stage = new EncryptStage(
                executor, encryptionService, encryptionKey, false, "testKey");

        ChunkTask task = new ChunkTask("transfer-123", tempDir.resolve("test.dat"),
                0, 100, 100);
        byte[] originalData = "Original data".getBytes();
        task.setProcessedData(originalData);

        CompletableFuture<ChunkTask> result = stage.process(task);
        ChunkTask processed = result.join();

        // Data should be unchanged when disabled
        assertArrayEquals(originalData, processed.getProcessedData());
        assertFalse(processed.isEncrypted());
    }

    @Test
    @DisplayName("Each chunk gets unique IV")
    void testUniqueIvPerChunk() throws Exception {
        EncryptStage stage = new EncryptStage(
                executor, encryptionService, encryptionKey, true, "testKey");

        Set<String> ivSet = new HashSet<>();
        byte[] sameData = "Same data for all chunks".getBytes();

        for (int i = 0; i < 10; i++) {
            ChunkTask task = new ChunkTask("transfer-123", tempDir.resolve("test.dat"),
                    i * 100L, 100, 1000);
            task.setProcessedData(sameData.clone());

            ChunkTask processed = stage.process(task).join();
            byte[] encrypted = processed.getProcessedData();

            // Extract IV (first 12 bytes)
            byte[] iv = Arrays.copyOf(encrypted, 12);
            String ivHex = bytesToHex(iv);

            assertTrue(ivSet.add(ivHex), "IV should be unique: " + ivHex);
        }
    }

    @Test
    @DisplayName("Encrypt and decrypt roundtrip works correctly")
    void testEncryptDecryptRoundtrip() throws Exception {
        EncryptStage stage = new EncryptStage(
                executor, encryptionService, encryptionKey, true, "testKey");

        ChunkTask task = new ChunkTask("transfer-123", tempDir.resolve("test.dat"),
                0, 100, 100);
        byte[] originalData = "Sensitive data to protect".getBytes();
        task.setProcessedData(originalData);

        ChunkTask encrypted = stage.process(task).join();

        // Decrypt manually
        byte[] associatedData = "transfer-123".getBytes();
        byte[] decrypted = encryptionService.decrypt(
                encrypted.getProcessedData(), encryptionKey, associatedData);

        assertArrayEquals(originalData, decrypted);
    }

    @Test
    @DisplayName("Uses raw data if processed data is null")
    void testUsesRawDataIfProcessedNull() throws Exception {
        EncryptStage stage = new EncryptStage(
                executor, encryptionService, encryptionKey, true, "testKey");

        ChunkTask task = new ChunkTask("transfer-123", tempDir.resolve("test.dat"),
                0, 100, 100);
        byte[] rawData = "Raw data".getBytes();
        task.setRawData(rawData);
        // processedData is null

        ChunkTask processed = stage.process(task).join();

        assertTrue(processed.isEncrypted());

        // Verify we can decrypt and get back original
        byte[] associatedData = "transfer-123".getBytes();
        byte[] decrypted = encryptionService.decrypt(
                processed.getProcessedData(), encryptionKey, associatedData);

        assertArrayEquals(rawData, decrypted);
    }

    @Test
    @DisplayName("Null key passes through unchanged")
    void testNullKeyPassthrough() throws Exception {
        EncryptStage stage = new EncryptStage(
                executor, encryptionService, null, true, "testKey");

        ChunkTask task = new ChunkTask("transfer-123", tempDir.resolve("test.dat"),
                0, 100, 100);
        byte[] originalData = "Data".getBytes();
        task.setProcessedData(originalData);

        ChunkTask processed = stage.process(task).join();

        // Should pass through unchanged
        assertArrayEquals(originalData, processed.getProcessedData());
    }

    @Test
    @DisplayName("Shutdown clears encryption key from memory")
    void testShutdownClearsKey() {
        byte[] keyToTest = new byte[32];
        new SecureRandom().nextBytes(keyToTest);
        byte[] originalKey = keyToTest.clone();

        EncryptStage stage = new EncryptStage(
                executor, encryptionService, keyToTest, true, "testKey");

        stage.shutdown();

        // Original array should still have data (we cloned it)
        assertFalse(allZeros(originalKey));
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private boolean allZeros(byte[] arr) {
        for (byte b : arr) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }
}
