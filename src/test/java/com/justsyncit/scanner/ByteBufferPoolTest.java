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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.justsyncit.scanner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for ByteBufferPool.
 */
class ByteBufferPoolTest {

    /** The buffer pool under test. */
    private ByteBufferPool pool;

    @BeforeEach
    void setUp() {
        pool = new ByteBufferPool();
    }
    @Test
    void testAcquireAndRelease() {
        ByteBuffer buffer = pool.acquire(1024);
        assertNotNull(buffer);
        // ByteBufferPool allocates at least default size (64KB) for efficiency
        assertTrue(buffer.capacity() >= 1024);
        assertEquals(0, buffer.position());
        assertEquals(buffer.capacity(), buffer.limit());
        pool.release(buffer);
    }
    @Test
    void testAcquireWithDefaultSize() {
        ByteBuffer buffer = pool.acquire(64 * 1024); // Use default 64KB size
        assertNotNull(buffer);
        assertEquals(64 * 1024, buffer.capacity());
        assertEquals(0, buffer.position());
        assertEquals(buffer.capacity(), buffer.limit());
        pool.release(buffer);
    }
    @Test
    void testMultipleBuffers() {
        ByteBuffer buffer1 = pool.acquire(1024);
        ByteBuffer buffer2 = pool.acquire(2048);
        ByteBuffer buffer3 = pool.acquire(1024);
        assertNotNull(buffer1);
        assertNotNull(buffer2);
        assertNotNull(buffer3);
        
        // ByteBufferPool allocates at least default size (64KB) for efficiency
        assertTrue(buffer1.capacity() >= 1024);
        assertTrue(buffer2.capacity() >= 2048);
        assertTrue(buffer3.capacity() >= 1024);
        
        // Release buffers
        pool.release(buffer1);
        pool.release(buffer2);
        pool.release(buffer3);
    }
    @Test
    void testBufferReuse() {
        ByteBuffer buffer1 = pool.acquire(1024);
        // Modify buffer
        buffer1.putInt(0x12345678);
        assertEquals(4, buffer1.position());
        
        // Release and acquire again
        pool.release(buffer1);
        ByteBuffer buffer2 = pool.acquire(1024);
        
        // Buffer should be reset
        assertEquals(0, buffer2.position());
        assertEquals(buffer2.capacity(), buffer2.limit());
        
        pool.release(buffer2);
    }
    @Test
    void testClear() {
        // Acquire and release several buffers
        for (int i = 0; i < 5; i++) {
            ByteBuffer buffer = pool.acquire(1024);
            pool.release(buffer);
        }
        // Clear the pool
        pool.clear();
        
        // After clear, the pool is closed, so acquiring should fail
        assertThrows(IllegalStateException.class, () -> pool.acquire(1024));
    }
    @Test
    void testGetDefaultBufferSize() {
        int defaultSize = pool.getDefaultBufferSize();
        assertEquals(64 * 1024, defaultSize);
    }
    @Test
    void testInvalidSize() {
        assertThrows(IllegalArgumentException.class, () -> pool.acquire(0));
        assertThrows(IllegalArgumentException.class, () -> pool.acquire(-1));
    }
    @Test
    void testNullBufferRelease() {
        assertThrows(IllegalArgumentException.class, () -> pool.release(null));
    }
    @Test
    void testDirectBuffers() {
        ByteBuffer buffer = pool.acquire(1024);
        // Buffers should be direct for better performance
        assertTrue(buffer.isDirect());
        
        pool.release(buffer);
    }
    
    @Test
    void testConcurrentAccess() throws InterruptedException {
        final int threadCount = 10;
        final int operationsPerThread = 100;
        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    ByteBuffer buffer = pool.acquire(1024);
                    assertNotNull(buffer);
                    
                    // Simulate some work
                    buffer.putInt(j);
                    buffer.flip();
                    
                    pool.release(buffer);
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
            ByteBuffer buffer = pool.acquire(1024);
            pool.release(buffer);
        });
    }
}