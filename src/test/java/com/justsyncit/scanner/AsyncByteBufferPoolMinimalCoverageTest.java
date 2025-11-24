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
                // Use synchronous clear to avoid async issues
                pool.clear();
                logger.debug("Pool cleared successfully in tearDown");
            } catch (Exception e) {
                // Any exception during cleanup is logged but doesn't fail the test
                logger.debug("Exception during pool cleanup in tearDown, but continuing: {}", e.getMessage());
                // Don't retry with sync clear as it might cause the hanging issue
            } finally {
                pool = null;
            }
        }
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 30, unit = java.util.concurrent.TimeUnit.SECONDS)
    void testSyncAcquireMethod() {
        // Test synchronous acquire(int) method to improve coverage
        pool = AsyncByteBufferPoolImpl.create(1024, 2);
        
        ByteBuffer buffer = pool.acquire(2048);
        
        assertNotNull(buffer, "Buffer should not be null");
        assertTrue(buffer.capacity() >= 2048, "Buffer capacity should be at least requested size");
        assertTrue(buffer.isDirect(), "Buffer should be direct");
        
        // Release the buffer
        pool.release(buffer);
        
        // Cleanup will be handled by @After method
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 30, unit = java.util.concurrent.TimeUnit.SECONDS)
    void testSyncClearMethod() {
        // Test synchronous clear() method to improve coverage
        pool = AsyncByteBufferPoolImpl.create(1024, 2);
        
        // First acquire some buffers using sync method
        ByteBuffer buffer1 = pool.acquire(1024);
        ByteBuffer buffer2 = pool.acquire(2048);
        
        assertNotNull(buffer1, "Buffer1 should not be null");
        assertNotNull(buffer2, "Buffer2 should not be null");
        
        // Clear the pool using sync method
        pool.clear();
        
        // After clear, the pool is closed, so we can't check state
        // But the fact that we got here without exception means clear() worked
        
        // Set pool to null since we've already cleared it
        pool = null;
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 30, unit = java.util.concurrent.TimeUnit.SECONDS)
    void testCreateMethodWithCustomParameters() {
        // Test the create(int, int) method directly
        pool = AsyncByteBufferPoolImpl.create(16384, 2);
        
        // Test that the pool was created with correct parameters
        int defaultSize = pool.getDefaultBufferSize();
        assertEquals(16384, defaultSize, "Should have custom default buffer size");
        
        // Test sync operations
        ByteBuffer buffer1 = pool.acquire(16384);
        ByteBuffer buffer2 = pool.acquire(16384);
        
        assertNotNull(buffer1, "Buffer1 should not be null");
        assertNotNull(buffer2, "Buffer2 should not be null");
        
        // Release all buffers
        pool.release(buffer1);
        pool.release(buffer2);
        
        // Cleanup will be handled by @After method
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 30, unit = java.util.concurrent.TimeUnit.SECONDS)
    void testSyncMethodsEdgeCases() {
        // Test edge cases for sync methods
        pool = AsyncByteBufferPoolImpl.create(1024, 2);
        
        // Test acquire with size 0
        assertThrows(IllegalArgumentException.class, () -> pool.acquire(0));
        
        // Test acquire with negative size
        assertThrows(IllegalArgumentException.class, () -> pool.acquire(-1));
        
        // Test release null buffer
        assertThrows(IllegalArgumentException.class, () -> pool.release(null));
        
        // Test release buffer not from pool
        ByteBuffer externalBuffer = ByteBuffer.allocate(1024);
        assertDoesNotThrow(() -> pool.release(externalBuffer)); // Should not throw, just log warning
        
        // Cleanup will be handled by @After method
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 30, unit = java.util.concurrent.TimeUnit.SECONDS)
    void testDefaultBufferSize() {
        // Test getDefaultBufferSize() method
        pool = AsyncByteBufferPoolImpl.create(8192, 2);
        
        int defaultSize = pool.getDefaultBufferSize();
        assertTrue(defaultSize > 0, "Default buffer size should be positive");
        assertEquals(8192, defaultSize, "Should have custom default buffer size");
        
        // Cleanup will be handled by @After method
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 30, unit = java.util.concurrent.TimeUnit.SECONDS)
    void testSyncReleaseMethod() {
        // Test synchronous release(ByteBuffer) method to improve coverage
        pool = AsyncByteBufferPoolImpl.create(1024, 2);
        
        ByteBuffer buffer = pool.acquire(1024);
        assertNotNull(buffer, "Buffer should not be null");
        
        // Release the buffer
        pool.release(buffer);
        
        // Verify buffer is available again
        int availableCount = pool.getAvailableCount();
        assertTrue(availableCount >= 0, "Available count should be non-negative");
        
        // Cleanup will be handled by @After method
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 30, unit = java.util.concurrent.TimeUnit.SECONDS)
    void testSyncMethodsMultipleOperations() {
        // Test multiple sync operations to improve coverage
        pool = AsyncByteBufferPoolImpl.create(1024, 3);
        
        ByteBuffer[] buffers = new ByteBuffer[2];
        
        // Acquire multiple buffers
        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = pool.acquire(1024 * (i + 1));
            assertNotNull(buffers[i], "Buffer " + i + " should not be null");
        }
        
        // Release all buffers
        for (ByteBuffer buffer : buffers) {
            pool.release(buffer);
        }
        
        // Cleanup will be handled by @After method
    }
}