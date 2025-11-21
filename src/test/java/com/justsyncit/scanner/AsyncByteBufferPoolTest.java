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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AsyncByteBufferPool.
 * Tests async buffer management operations.
 */
class AsyncByteBufferPoolTest {

    /** The async buffer pool under test. */
    private AsyncByteBufferPool pool;

    @BeforeEach
    void setUp() {
        pool = AsyncByteBufferPoolImpl.create();
    }

    @Test
    void testAcquireAndReleaseAsync() throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<ByteBuffer> acquireFuture = pool.acquireAsync(1024);
        ByteBuffer buffer = acquireFuture.get(5, TimeUnit.SECONDS);

        assertNotNull(buffer, "Buffer should not be null");
        // AsyncByteBufferPoolImpl allocates at least default size (64KB) for efficiency
        assertTrue(buffer.capacity() >= 1024, "Buffer capacity should be at least requested size");
        assertEquals(0, buffer.position(), "Buffer position should be 0");
        assertEquals(buffer.capacity(), buffer.limit(), "Buffer limit should equal capacity");

        CompletableFuture<Void> releaseFuture = pool.releaseAsync(buffer);
        releaseFuture.get(5, TimeUnit.SECONDS);
    }

    @Test
    void testAcquireWithDefaultSizeAsync() throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<ByteBuffer> acquireFuture = pool.acquireAsync(64 * 1024); // Use default 64KB size
        ByteBuffer buffer = acquireFuture.get(5, TimeUnit.SECONDS);

        assertNotNull(buffer, "Buffer should not be null");
        assertEquals(64 * 1024, buffer.capacity(), "Buffer capacity should equal default size");
        assertEquals(0, buffer.position(), "Buffer position should be 0");
        assertEquals(buffer.capacity(), buffer.limit(), "Buffer limit should equal capacity");

        pool.releaseAsync(buffer).get(5, TimeUnit.SECONDS);
    }

    @Test
    void testMultipleBuffersAsync() throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<ByteBuffer> future1 = pool.acquireAsync(1024);
        CompletableFuture<ByteBuffer> future2 = pool.acquireAsync(2048);
        CompletableFuture<ByteBuffer> future3 = pool.acquireAsync(1024);

        ByteBuffer buffer1 = future1.get(5, TimeUnit.SECONDS);
        ByteBuffer buffer2 = future2.get(5, TimeUnit.SECONDS);
        ByteBuffer buffer3 = future3.get(5, TimeUnit.SECONDS);

        assertNotNull(buffer1, "Buffer1 should not be null");
        assertNotNull(buffer2, "Buffer2 should not be null");
        assertNotNull(buffer3, "Buffer3 should not be null");

        // AsyncByteBufferPoolImpl allocates at least default size (64KB) for efficiency
        assertTrue(buffer1.capacity() >= 1024, "Buffer1 capacity should be at least requested size");
        assertTrue(buffer2.capacity() >= 2048, "Buffer2 capacity should be at least requested size");
        assertTrue(buffer3.capacity() >= 1024, "Buffer3 capacity should be at least requested size");

        // Release buffers
        pool.releaseAsync(buffer1).get(5, TimeUnit.SECONDS);
        pool.releaseAsync(buffer2).get(5, TimeUnit.SECONDS);
        pool.releaseAsync(buffer3).get(5, TimeUnit.SECONDS);
    }

    @Test
    void testBufferReuseAsync() throws ExecutionException, InterruptedException, TimeoutException {
        // Acquire buffer
        CompletableFuture<ByteBuffer> acquireFuture1 = pool.acquireAsync(1024);
        ByteBuffer buffer1 = acquireFuture1.get(5, TimeUnit.SECONDS);

        // Modify buffer
        buffer1.putInt(0x12345678);
        assertEquals(4, buffer1.position(), "Buffer position should be 4 after putInt");

        // Release and acquire again
        pool.releaseAsync(buffer1).get(5, TimeUnit.SECONDS);
        CompletableFuture<ByteBuffer> acquireFuture2 = pool.acquireAsync(1024);
        ByteBuffer buffer2 = acquireFuture2.get(5, TimeUnit.SECONDS);

        // Buffer should be reset
        assertEquals(0, buffer2.position(), "Buffer position should be 0 after reuse");
        assertEquals(buffer2.capacity(), buffer2.limit(), "Buffer limit should equal capacity");

        pool.releaseAsync(buffer2).get(5, TimeUnit.SECONDS);
    }

    @Test
    void testClearAsync() throws ExecutionException, InterruptedException, TimeoutException {
        // Acquire and release several buffers
        for (int i = 0; i < 5; i++) {
            CompletableFuture<ByteBuffer> acquireFuture = pool.acquireAsync(1024);
            ByteBuffer buffer = acquireFuture.get(5, TimeUnit.SECONDS);
            pool.releaseAsync(buffer).get(5, TimeUnit.SECONDS);
        }

        // Clear the pool
        CompletableFuture<Void> clearFuture = pool.clearAsync();
        clearFuture.get(5, TimeUnit.SECONDS);

        // After clear, acquiring should fail
        CompletableFuture<ByteBuffer> acquireFuture = pool.acquireAsync(1024);
        assertThrows(ExecutionException.class, () -> acquireFuture.get(5, TimeUnit.SECONDS));
        assertTrue(acquireFuture.isCompletedExceptionally(), "Future should be completed exceptionally");
    }

    @Test
    void testGetStatsAsync() throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<String> statsFuture = pool.getStatsAsync();
        String stats = statsFuture.get(5, TimeUnit.SECONDS);

        assertNotNull(stats, "Stats should not be null");
        assertTrue(stats.contains("AsyncByteBufferPoolImpl"), "Stats should contain class name");
        assertTrue(stats.contains("Total:"), "Stats should contain total count");
        assertTrue(stats.contains("Available:"), "Stats should contain available count");
        assertTrue(stats.contains("In Use:"), "Stats should contain in-use count");
    }

    @Test
    void testGetAvailableCountAsync() throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<Integer> countFuture = pool.getAvailableCountAsync();
        Integer count = countFuture.get(5, TimeUnit.SECONDS);

        assertNotNull(count, "Count should not be null");
        assertTrue(count >= 0, "Count should be non-negative");
    }

    @Test
    void testGetTotalCountAsync() throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<Integer> countFuture = pool.getTotalCountAsync();
        Integer count = countFuture.get(5, TimeUnit.SECONDS);

        assertNotNull(count, "Count should not be null");
        assertTrue(count >= 0, "Count should be non-negative");
    }

    @Test
    void testGetBuffersInUseAsync() throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<Integer> countFuture = pool.getBuffersInUseAsync();
        Integer count = countFuture.get(5, TimeUnit.SECONDS);

        assertNotNull(count, "Count should not be null");
        assertTrue(count >= 0, "Count should be non-negative");
    }

    @Test
    void testInvalidSizeAsync() {
        CompletableFuture<ByteBuffer> future1 = pool.acquireAsync(0);
        CompletableFuture<ByteBuffer> future2 = pool.acquireAsync(-1);

        assertTrue(future1.isCompletedExceptionally(), "Future should be completed exceptionally for size 0");
        assertTrue(future2.isCompletedExceptionally(), "Future should be completed exceptionally for negative size");
    }

    @Test
    void testNullBufferReleaseAsync() {
        CompletableFuture<Void> future = pool.releaseAsync(null);

        assertTrue(future.isCompletedExceptionally(), "Future should be completed exceptionally for null buffer");
        assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
    }

    @Test
    void testDirectBuffersAsync() throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<ByteBuffer> acquireFuture = pool.acquireAsync(1024);
        ByteBuffer buffer = acquireFuture.get(5, TimeUnit.SECONDS);

        // Buffers should be direct for better performance
        assertTrue(buffer.isDirect(), "Buffer should be direct");

        pool.releaseAsync(buffer).get(5, TimeUnit.SECONDS);
    }

    @Test
    void testConcurrentAccessAsync() throws InterruptedException, ExecutionException, TimeoutException {
        final int threadCount = 10;
        final int operationsPerThread = 100;
        Thread[] threads = new Thread[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    try {
                        CompletableFuture<ByteBuffer> acquireFuture = pool.acquireAsync(1024);
                        ByteBuffer buffer = acquireFuture.get(5, TimeUnit.SECONDS);
                        assertNotNull(buffer, "Buffer should not be null");

                        // Simulate some work
                        buffer.putInt(j);
                        buffer.flip();

                        pool.releaseAsync(buffer).get(5, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        fail("Concurrent operation failed: " + e.getMessage());
                    }
                }
            });
        }

        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Should not throw any exceptions
        assertDoesNotThrow(() -> {
            CompletableFuture<ByteBuffer> acquireFuture = pool.acquireAsync(1024);
            ByteBuffer buffer = acquireFuture.get(5, TimeUnit.SECONDS);
            pool.releaseAsync(buffer).get(5, TimeUnit.SECONDS);
        });
    }

    @Test
    void testCustomPoolAsync() throws ExecutionException, InterruptedException, TimeoutException {
        AsyncByteBufferPool customPool = AsyncByteBufferPoolImpl.create(32 * 1024, 8); // 32KB default, max 8 buffers

        CompletableFuture<ByteBuffer> acquireFuture = customPool.acquireAsync(32 * 1024);
        ByteBuffer buffer = acquireFuture.get(5, TimeUnit.SECONDS);

        assertNotNull(buffer, "Buffer should not be null");
        assertEquals(32 * 1024, buffer.capacity(), "Buffer should have custom default size");

        customPool.releaseAsync(buffer).get(5, TimeUnit.SECONDS);
        customPool.clearAsync().get(5, TimeUnit.SECONDS);
    }

    @Test
    void testSyncCompatibilityAsync() throws ExecutionException, InterruptedException, TimeoutException {
        // Test that async pool still works with sync interface methods
        ByteBuffer buffer = pool.acquire(1024);
        assertNotNull(buffer, "Sync acquire should work");

        pool.release(buffer);

        // Test async operations after sync operations
        CompletableFuture<ByteBuffer> acquireFuture = pool.acquireAsync(1024);
        ByteBuffer asyncBuffer = acquireFuture.get(5, TimeUnit.SECONDS);
        assertNotNull(asyncBuffer, "Async acquire should work after sync operations");

        pool.releaseAsync(asyncBuffer).get(5, TimeUnit.SECONDS);
    }
}