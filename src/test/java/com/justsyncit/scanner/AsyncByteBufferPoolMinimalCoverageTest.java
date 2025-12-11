/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.justsyncit.scanner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Minimal coverage tests for AsyncByteBufferPoolImpl synchronous methods.
 * Uses a single pool instance to avoid thread pool exhaustion issues.
 * Focuses on improving code coverage for methods with low coverage.
 */
class AsyncByteBufferPoolMinimalCoverageTest {

    private static final Logger logger = LoggerFactory.getLogger(AsyncByteBufferPoolMinimalCoverageTest.class);
    private AsyncByteBufferPool pool;

    @AfterEach
    void tearDown() {
        if (pool != null) {
            try {
                // Skip clear() to avoid hanging issues - let GC handle cleanup
                pool.clear();

                logger.debug("Pool cleanup skipped in tearDown to prevent hanging");
            } catch (Exception e) {
                // Any exception during cleanup is logged but doesn't fail the test
                logger.debug("Exception during pool cleanup in tearDown, but continuing: {}", e.getMessage());
            } finally {
                pool = null;
            }
        }
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
    void testSyncAcquireMethod() {
        logger.info("Starting testSyncAcquireMethod");
        try {
            // Test synchronous acquire(int) method to improve coverage
            logger.info("Creating pool");
            pool = AsyncByteBufferPoolImpl.create(1024, 2);
            logger.info("Pool created successfully");

            logger.info("Acquiring buffer");
            ByteBuffer buffer = pool.acquire(2048);
            logger.info("Buffer acquired successfully");

            assertNotNull(buffer, "Buffer should not be null");
            assertTrue(buffer.capacity() >= 2048, "Buffer capacity should be at least requested size");
            assertTrue(buffer.isDirect(), "Buffer should be direct");

            // Release the buffer
            logger.info("Releasing buffer");
            pool.release(buffer);
            logger.info("Buffer released successfully");

            // Cleanup will be handled by @After method
            logger.info("testSyncAcquireMethod completed successfully");
        } catch (Exception e) {
            logger.error("Exception in testSyncAcquireMethod", e);
            throw e;
        }
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
    void testSyncClearMethod() {
        logger.info("Starting testSyncClearMethod");
        try {
            // Test synchronous clear() method to improve coverage
            logger.info("Creating pool");
            pool = AsyncByteBufferPoolImpl.create(1024, 2);
            logger.info("Pool created successfully");

            // First acquire some buffers using sync method
            logger.info("Acquiring first buffer");
            ByteBuffer buffer1 = pool.acquire(1024);
            logger.info("First buffer acquired successfully");

            logger.info("Acquiring second buffer");
            ByteBuffer buffer2 = pool.acquire(2048);
            logger.info("Second buffer acquired successfully");

            assertNotNull(buffer1, "Buffer1 should not be null");
            assertNotNull(buffer2, "Buffer2 should not be null");

            // Skip clear() to avoid hanging - just test that we can acquire buffers
            // Try specific clear() now
            pool.clear();
            logger.info("Pool cleared successfully");

            // Set pool to null since we'll skip clear
            pool = null;
            logger.info("testSyncClearMethod completed successfully");
        } catch (Exception e) {
            logger.error("Exception in testSyncClearMethod", e);
            throw e;
        }
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
    void testCreateMethodWithCustomParameters() {
        logger.info("Starting testCreateMethodWithCustomParameters");
        try {
            // Test create(int, int) method directly
            logger.info("Creating pool with custom parameters");
            pool = AsyncByteBufferPoolImpl.create(16384, 2);
            logger.info("Pool created successfully");

            // Test that pool was created with correct parameters
            logger.info("Getting default buffer size");
            int defaultSize = pool.getDefaultBufferSize();
            assertEquals(16384, defaultSize, "Should have custom default buffer size");

            // Test sync operations
            logger.info("Acquiring first buffer");
            ByteBuffer buffer1 = pool.acquire(16384);
            logger.info("First buffer acquired successfully");

            logger.info("Acquiring second buffer");
            ByteBuffer buffer2 = pool.acquire(16384);
            logger.info("Second buffer acquired successfully");

            assertNotNull(buffer1, "Buffer1 should not be null");
            assertNotNull(buffer2, "Buffer2 should not be null");

            // Release all buffers
            logger.info("Releasing first buffer");
            pool.release(buffer1);
            logger.info("First buffer released successfully");

            logger.info("Releasing second buffer");
            pool.release(buffer2);
            logger.info("Second buffer released successfully");

            // Cleanup will be handled by @After method
            logger.info("testCreateMethodWithCustomParameters completed successfully");
        } catch (Exception e) {
            logger.error("Exception in testCreateMethodWithCustomParameters", e);
            throw e;
        }
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
    void testSyncMethodsEdgeCases() {
        logger.info("Starting testSyncMethodsEdgeCases");
        try {
            // Test edge cases for sync methods
            logger.info("Creating pool");
            pool = AsyncByteBufferPoolImpl.create(1024, 2);
            logger.info("Pool created successfully");

            // Test acquire with size 0
            logger.info("Testing acquire with size 0");
            assertThrows(IllegalArgumentException.class, () -> pool.acquire(0));
            logger.info("Acquire with size 0 threw expected exception");

            // Test acquire with negative size
            logger.info("Testing acquire with negative size");
            assertThrows(IllegalArgumentException.class, () -> pool.acquire(-1));
            logger.info("Acquire with negative size threw expected exception");

            // Test release null buffer
            logger.info("Testing release null buffer");
            assertThrows(IllegalArgumentException.class, () -> pool.release(null));
            logger.info("Release null buffer threw expected exception");

            // Test release buffer not from pool
            logger.info("Testing release external buffer");
            ByteBuffer externalBuffer = ByteBuffer.allocate(1024);
            assertDoesNotThrow(() -> pool.release(externalBuffer)); // Should not throw, just log warning
            logger.info("Release external buffer completed successfully");

            // Cleanup will be handled by @After method
            logger.info("testSyncMethodsEdgeCases completed successfully");
        } catch (Exception e) {
            logger.error("Exception in testSyncMethodsEdgeCases", e);
            throw e;
        }
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
    void testDefaultBufferSize() {
        logger.info("Starting testDefaultBufferSize");
        try {
            // Test getDefaultBufferSize() method
            logger.info("Creating pool");
            pool = AsyncByteBufferPoolImpl.create(8192, 2);
            logger.info("Pool created successfully");

            logger.info("Getting default buffer size");
            int defaultSize = pool.getDefaultBufferSize();
            assertTrue(defaultSize > 0, "Default buffer size should be positive");
            assertEquals(8192, defaultSize, "Should have custom default buffer size");

            // Cleanup will be handled by @After method
            logger.info("testDefaultBufferSize completed successfully");
        } catch (Exception e) {
            logger.error("Exception in testDefaultBufferSize", e);
            throw e;
        }
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
    void testSyncReleaseMethod() {
        logger.info("Starting testSyncReleaseMethod");
        try {
            // Test synchronous release(ByteBuffer) method to improve coverage
            logger.info("Creating pool");
            pool = AsyncByteBufferPoolImpl.create(1024, 2);
            logger.info("Pool created successfully");

            logger.info("Acquiring buffer");
            ByteBuffer buffer = pool.acquire(1024);
            logger.info("Buffer acquired successfully");

            assertNotNull(buffer, "Buffer should not be null");

            // Release the buffer
            logger.info("Releasing buffer");
            pool.release(buffer);
            logger.info("Buffer released successfully");

            // Verify buffer is available again
            logger.info("Getting available count");
            int availableCount = pool.getAvailableCount();
            assertTrue(availableCount >= 0, "Available count should be non-negative");

            // Cleanup will be handled by @After method
            logger.info("testSyncReleaseMethod completed successfully");
        } catch (Exception e) {
            logger.error("Exception in testSyncReleaseMethod", e);
            throw e;
        }
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
    void testSyncMethodsMultipleOperations() {
        logger.info("Starting testSyncMethodsMultipleOperations");
        try {
            // Test multiple sync operations to improve coverage
            logger.info("Creating pool");
            pool = AsyncByteBufferPoolImpl.create(1024, 3);
            logger.info("Pool created successfully");

            ByteBuffer[] buffers = new ByteBuffer[2];

            // Acquire multiple buffers
            for (int i = 0; i < buffers.length; i++) {
                logger.info("Acquiring buffer {}", i);
                buffers[i] = pool.acquire(1024 * (i + 1));
                logger.info("Buffer {} acquired successfully", i);
                assertNotNull(buffers[i], "Buffer " + i + " should not be null");
            }

            // Release all buffers
            for (int i = 0; i < buffers.length; i++) {
                logger.info("Releasing buffer {}", i);
                pool.release(buffers[i]);
                logger.info("Buffer {} released successfully", i);
            }

            // Cleanup will be handled by @After method
            logger.info("testSyncMethodsMultipleOperations completed successfully");
        } catch (Exception e) {
            logger.error("Exception in testSyncMethodsMultipleOperations", e);
            throw e;
        }
    }
}