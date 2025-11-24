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

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reflection-based coverage tests for AsyncByteBufferPoolImpl.
 * Uses reflection to test the create(int, int) method without creating multiple instances.
 * Focuses on improving code coverage for the static factory method.
 */
class AsyncByteBufferPoolReflectionTest {

    @Test
    void testCreateMethodReflection() throws Exception {
        // Test the create(int, int) method using reflection
        Method createMethod = AsyncByteBufferPoolImpl.class.getDeclaredMethod("create", int.class, int.class);
        
        // Test valid parameters
        AsyncByteBufferPool pool = (AsyncByteBufferPool) createMethod.invoke(null, 1024, 4);
        assertNotNull(pool, "Pool should not be null");
        assertEquals(1024, pool.getDefaultBufferSize(), "Default buffer size should be 1024");
        
        // Test the pool with basic operations
        ByteBuffer buffer = pool.acquire(512);
        assertNotNull(buffer, "Buffer should not be null");
        assertTrue(buffer.capacity() >= 512, "Buffer capacity should be at least 512");
        pool.release(buffer);
        
        // Clean up
        pool.clear();
    }

    @Test
    void testCreateMethodInvalidParameters() throws Exception {
        // Test the create(int, int) method with invalid parameters using reflection
        Method createMethod = AsyncByteBufferPoolImpl.class.getDeclaredMethod("create", int.class, int.class);
        
        // Test with buffer size too small
        assertThrows(Exception.class, () -> {
            createMethod.invoke(null, 512, 4); // Below MIN_BUFFER_SIZE (1024)
        });
        
        // Test with buffer size too large
        assertThrows(Exception.class, () -> {
            createMethod.invoke(null, 2 * 1024 * 1024, 4); // Above MAX_BUFFER_SIZE (1MB)
        });
        
        // Test with negative buffer size
        assertThrows(Exception.class, () -> {
            createMethod.invoke(null, -1024, 4);
        });
        
        // Test with zero buffer size
        assertThrows(Exception.class, () -> {
            createMethod.invoke(null, 0, 4);
        });
        
        // Test with negative max buffers
        assertThrows(Exception.class, () -> {
            createMethod.invoke(null, 1024, -1);
        });
        
        // Test with zero max buffers
        assertThrows(Exception.class, () -> {
            createMethod.invoke(null, 1024, 0);
        });
    }

    @Test
    void testCreateMethodBoundaryParameters() throws Exception {
        // Test the create(int, int) method with boundary parameters using reflection
        Method createMethod = AsyncByteBufferPoolImpl.class.getDeclaredMethod("create", int.class, int.class);
        
        // Test with minimum buffer size (1024)
        AsyncByteBufferPool minPool = (AsyncByteBufferPool) createMethod.invoke(null, 1024, 2);
        assertNotNull(minPool, "Pool should not be null");
        assertEquals(1024, minPool.getDefaultBufferSize(), "Default buffer size should be 1024");
        minPool.clear();
        
        // Test with maximum buffer size (1MB)
        AsyncByteBufferPool maxPool = (AsyncByteBufferPool) createMethod.invoke(null, 1024 * 1024, 2);
        assertNotNull(maxPool, "Pool should not be null");
        assertEquals(1024 * 1024, maxPool.getDefaultBufferSize(), "Default buffer size should be 1MB");
        maxPool.clear();
        
        // Test with minimum max buffers (1)
        AsyncByteBufferPool minBuffersPool = (AsyncByteBufferPool) createMethod.invoke(null, 1024, 1);
        assertNotNull(minBuffersPool, "Pool should not be null");
        minBuffersPool.clear();
    }

    @Test
    void testCreateMethodWithDefaultParameters() throws Exception {
        // Test the create() method with default parameters using reflection
        Method createMethod = AsyncByteBufferPoolImpl.class.getDeclaredMethod("create");
        
        AsyncByteBufferPool pool = (AsyncByteBufferPool) createMethod.invoke(null);
        assertNotNull(pool, "Pool should not be null");
        
        // Default should be 64KB (64 * 1024)
        assertEquals(64 * 1024, pool.getDefaultBufferSize(), "Default buffer size should be 64KB");
        
        // Test basic operations
        ByteBuffer buffer = pool.acquire(1024);
        assertNotNull(buffer, "Buffer should not be null");
        assertTrue(buffer.capacity() >= 1024, "Buffer capacity should be at least 1024");
        pool.release(buffer);
        
        // Clean up
        pool.clear();
    }

    @Test
    void testSyncMethodsAfterCreate() throws Exception {
        // Test synchronous methods after creating pool with reflection
        Method createMethod = AsyncByteBufferPoolImpl.class.getDeclaredMethod("create", int.class, int.class);
        
        AsyncByteBufferPool pool = (AsyncByteBufferPool) createMethod.invoke(null, 2048, 3);
        
        try {
            // Test acquire method
            ByteBuffer buffer1 = pool.acquire(1024);
            ByteBuffer buffer2 = pool.acquire(2048);
            ByteBuffer buffer3 = pool.acquire(4096);
            
            assertNotNull(buffer1, "Buffer1 should not be null");
            assertNotNull(buffer2, "Buffer2 should not be null");
            assertNotNull(buffer3, "Buffer3 should not be null");
            
            assertTrue(buffer1.capacity() >= 1024, "Buffer1 capacity should be at least 1024");
            assertTrue(buffer2.capacity() >= 2048, "Buffer2 capacity should be at least 2048");
            assertTrue(buffer3.capacity() >= 4096, "Buffer3 capacity should be at least 4096");
            
            // Test release method
            pool.release(buffer1);
            pool.release(buffer2);
            pool.release(buffer3);
            
            // Test get methods
            int availableCount = pool.getAvailableCount();
            int totalCount = pool.getTotalCount();
            int defaultSize = pool.getDefaultBufferSize();
            
            assertTrue(availableCount >= 0, "Available count should be non-negative");
            assertTrue(totalCount >= 0, "Total count should be non-negative");
            assertEquals(2048, defaultSize, "Default size should be 2048");
            
        } finally {
            // Test clear method
            pool.clear();
        }
    }
}