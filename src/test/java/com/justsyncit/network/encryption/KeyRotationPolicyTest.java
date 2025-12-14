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

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for KeyRotationPolicy.
 */
class KeyRotationPolicyTest {

    private KeyRotationPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new KeyRotationPolicy(
                Duration.ofHours(1), // 1 hour max age
                1_000_000L, // 1 MB max bytes
                100L // 100 max operations
        );
    }

    @Test
    @DisplayName("Fresh policy should not require rotation")
    void testFreshPolicyNoRotation() {
        assertFalse(policy.shouldRotate());
        assertFalse(policy.isAgeExceeded());
        assertFalse(policy.isByteLimitExceeded());
        assertFalse(policy.isOperationLimitExceeded());
    }

    @Test
    @DisplayName("Should rotate when byte limit exceeded")
    void testByteLimitRotation() {
        // Record enough bytes to exceed limit
        policy.recordOperation(1_000_001);

        assertTrue(policy.isByteLimitExceeded());
        assertTrue(policy.shouldRotate());
    }

    @Test
    @DisplayName("Should rotate when operation limit exceeded")
    void testOperationLimitRotation() {
        // Record enough operations to exceed limit
        for (int i = 0; i < 100; i++) {
            policy.recordOperation(1);
        }

        assertTrue(policy.isOperationLimitExceeded());
        assertTrue(policy.shouldRotate());
    }

    @Test
    @DisplayName("Record operation increments counters")
    void testRecordOperationIncrementsCounters() {
        assertEquals(0, policy.getCurrentBytesEncrypted());
        assertEquals(0, policy.getCurrentOperationCount());

        policy.recordOperation(500);
        assertEquals(500, policy.getCurrentBytesEncrypted());
        assertEquals(1, policy.getCurrentOperationCount());

        policy.recordOperation(300);
        assertEquals(800, policy.getCurrentBytesEncrypted());
        assertEquals(2, policy.getCurrentOperationCount());
    }

    @Test
    @DisplayName("Reset clears all counters")
    void testResetClearsCounters() {
        policy.recordOperation(1000);
        policy.recordOperation(1000);

        assertTrue(policy.getCurrentBytesEncrypted() > 0);
        assertTrue(policy.getCurrentOperationCount() > 0);

        policy.reset();

        assertEquals(0, policy.getCurrentBytesEncrypted());
        assertEquals(0, policy.getCurrentOperationCount());
    }

    @Test
    @DisplayName("Reset with custom time updates creation time")
    void testResetWithCustomTime() {
        Instant newTime = Instant.now().minusSeconds(3600);
        policy.reset(newTime);

        assertEquals(newTime, policy.getKeyCreationTime());
    }

    @Test
    @DisplayName("Default policy has reasonable defaults")
    void testDefaultPolicyDefaults() {
        KeyRotationPolicy defaultPolicy = new KeyRotationPolicy();

        assertEquals(Duration.ofDays(90), defaultPolicy.getMaxAge());
        assertEquals(1L << 40, defaultPolicy.getMaxBytesEncrypted()); // 1 TB
        assertEquals(1_000_000_000L, defaultPolicy.getMaxOperations()); // 1 billion
    }

    @Test
    @DisplayName("Custom parameters are stored correctly")
    void testCustomParameters() {
        Duration maxAge = Duration.ofDays(7);
        long maxBytes = 1024L * 1024 * 100; // 100 MB
        long maxOps = 5000L;

        KeyRotationPolicy custom = new KeyRotationPolicy(maxAge, maxBytes, maxOps);

        assertEquals(maxAge, custom.getMaxAge());
        assertEquals(maxBytes, custom.getMaxBytesEncrypted());
        assertEquals(maxOps, custom.getMaxOperations());
    }

    @Test
    @DisplayName("Thread-safe counter increments")
    void testThreadSafeCounterIncrements() throws InterruptedException {
        int numThreads = 10;
        int opsPerThread = 100;
        Thread[] threads = new Thread[numThreads];

        for (int i = 0; i < numThreads; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < opsPerThread; j++) {
                    policy.recordOperation(10);
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(numThreads * opsPerThread, policy.getCurrentOperationCount());
        assertEquals(numThreads * opsPerThread * 10, policy.getCurrentBytesEncrypted());
    }

    @Test
    @DisplayName("Multiple rotation triggers are detected")
    void testMultipleRotationTriggers() {
        // Exceed both byte and operation limits
        for (int i = 0; i < 100; i++) {
            policy.recordOperation(20_000);
        }

        assertTrue(policy.isByteLimitExceeded());
        assertTrue(policy.isOperationLimitExceeded());
        assertTrue(policy.shouldRotate());
    }
}
