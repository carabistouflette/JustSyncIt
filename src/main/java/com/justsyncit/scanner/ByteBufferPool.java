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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implementation of BufferPool using direct ByteBuffers for optimal I/O performance.
 * Follows Single Responsibility Principle by focusing only on buffer management.
 * Thread-safe implementation for concurrent access.
 */
public class ByteBufferPool implements BufferPool {

    /** Logger for the buffer pool. */
    private static final Logger logger = LoggerFactory.getLogger(ByteBufferPool.class);

    /** Default buffer size (64KB). */
    private static final int DEFAULT_BUFFER_SIZE = 64 * 1024;
    /** Default maximum number of buffers in pool. */
    private static final int DEFAULT_MAX_BUFFERS = 16;
    /** Minimum buffer size (1KB). */
    private static final int MIN_BUFFER_SIZE = 1024;
    /** Maximum buffer size (1MB). */
    private static final int MAX_BUFFER_SIZE = 1024 * 1024;

    /** Queue of available buffers. */
    private final ConcurrentLinkedQueue<ByteBuffer> availableBuffers;
    /** Total number of buffers created. */
    private final AtomicInteger totalBuffers;
    /** Number of buffers currently in use. */
    private final AtomicInteger buffersInUse;
    /** Default buffer size for this pool. */
    private final int defaultBufferSize;
    /** Whether the pool has been closed. */
    private volatile boolean closed;
    /** Lock for cleanup operations. */
    private final ReentrantLock cleanupLock = new ReentrantLock();

    /**
     * Creates a new ByteBufferPool with default settings.
     *
     * @return a new ByteBufferPool with default settings
     * @throws IllegalArgumentException if default settings are invalid
     */
    public static ByteBufferPool create() {
        return create(DEFAULT_BUFFER_SIZE, DEFAULT_MAX_BUFFERS);
    }

    /**
     * Creates a new ByteBufferPool with specified settings.
     *
     * @param defaultBufferSize default buffer size
     * @param maxBuffers maximum number of buffers
     * @return a new ByteBufferPool with specified settings
     * @throws IllegalArgumentException if parameters are invalid
     */
    public static ByteBufferPool create(int defaultBufferSize, int maxBuffers) {
        if (defaultBufferSize < MIN_BUFFER_SIZE || defaultBufferSize > MAX_BUFFER_SIZE) {
            throw new IllegalArgumentException(
                String.format("Buffer size must be between %d and %d bytes",
                    MIN_BUFFER_SIZE, MAX_BUFFER_SIZE));
        }
        if (maxBuffers <= 0) {
            throw new IllegalArgumentException("Max buffers must be positive");
        }

        return new ByteBufferPool(defaultBufferSize, maxBuffers);
    }

    /**
     * Creates a new ByteBufferPool with default settings.
     * @deprecated Use {@link #create()} instead
     */
    @Deprecated
    public ByteBufferPool() {
        this(DEFAULT_BUFFER_SIZE, DEFAULT_MAX_BUFFERS);
    }

    /**
     * Creates a new ByteBufferPool with specified settings.
     *
     * @param defaultBufferSize the default buffer size
     * @param maxBuffers maximum number of buffers
     * @throws IllegalArgumentException if parameters are invalid
     * @deprecated Use {@link #create(int, int)} instead
     */
    @Deprecated
    public ByteBufferPool(int defaultBufferSize, int maxBuffers) {
        // Parameters are assumed to be validated by static factory methods
        this.defaultBufferSize = defaultBufferSize;
        this.availableBuffers = new ConcurrentLinkedQueue<>();
        this.totalBuffers = new AtomicInteger(0);
        this.buffersInUse = new AtomicInteger(0);
        this.closed = false;

        // Pre-allocate some buffers
        int initialBuffers = Math.min(maxBuffers / 2, 4); // Start with half capacity or 4, whichever is smaller
        for (int i = 0; i < initialBuffers; i++) {
            availableBuffers.offer(allocateBuffer(defaultBufferSize));
            totalBuffers.incrementAndGet();
        }

        logger.debug("Created ByteBufferPool with default size {} and max {} buffers, pre-allocated {}",
                    defaultBufferSize, maxBuffers, initialBuffers);
    }

    @Override
    public ByteBuffer acquire(int size) {
        if (closed) {
            throw new IllegalStateException("Buffer pool has been closed");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be positive");
        }

        // Try to find an existing buffer that's large enough
        ByteBuffer buffer = availableBuffers.poll();
        while (buffer != null) {
            if (buffer.capacity() >= size) {
                buffersInUse.incrementAndGet();
                buffer.clear();
                logger.trace("Acquired buffer of size {} for request {}", buffer.capacity(), size);
                return buffer;
            }
            // Buffer is too small, put it back and try another
            availableBuffers.offer(buffer);
            buffer = availableBuffers.poll();
        }

        // No suitable buffer found, allocate a new one
        int allocateSize = Math.max(size, defaultBufferSize);
        buffer = allocateBuffer(allocateSize);
        buffersInUse.incrementAndGet();

        logger.trace("Allocated new buffer of size {} for request {}", allocateSize, size);
        return buffer;
    }

    @Override
    public void release(ByteBuffer buffer) {
        if (buffer == null) {
            throw new IllegalArgumentException("Buffer cannot be null");
        }
        if (closed) {
            // Pool is closed, just let the buffer be garbage collected
            return;
        }

        buffer.clear();
        if (availableBuffers.offer(buffer)) {
            buffersInUse.decrementAndGet();
            logger.trace("Released buffer of size {}", buffer.capacity());
        } else {
            // This shouldn't happen in normal operation
            logger.warn("Failed to release buffer to pool - pool may be full");
        }
    }

    @Override
    public void clear() {
        cleanupLock.lock();
        try {
            if (closed) {
                return;
            }

            int clearedCount = 0;
            while ((availableBuffers.poll()) != null) {
                // Direct buffers don't need explicit cleanup, but we can track
                clearedCount++;
            }

            logger.info("Cleared ByteBufferPool, removed {} buffers from pool", clearedCount);
            availableBuffers.clear();
            totalBuffers.set(0);
            buffersInUse.set(0);
            closed = true;
        } finally {
            cleanupLock.unlock();
        }
    }

    @Override
    public int getAvailableCount() {
        return availableBuffers.size();
    }

    @Override
    public int getTotalCount() {
        return totalBuffers.get();
    }

    /**
     * Gets the number of buffers currently in use.
     *
     * @return the number of buffers in use
     */
    public int getBuffersInUse() {
        return buffersInUse.get();
    }

    /**
     * Gets the default buffer size for this pool.
     *
     * @return the default buffer size
     */
    public int getDefaultBufferSize() {
        return defaultBufferSize;
    }

    /**
     * Allocates a new direct ByteBuffer of the specified size.
     *
     * @param size the buffer size
     * @return the allocated buffer
     */
    private ByteBuffer allocateBuffer(int size) {
        try {
            totalBuffers.incrementAndGet();
            return ByteBuffer.allocateDirect(size);
        } catch (OutOfMemoryError e) {
            logger.error("Failed to allocate direct buffer of size {} bytes", size, e);
            // Try to free some space by clearing available buffers
            cleanupLock.lock();
            try {
                int freedCount = 0;
                while ((availableBuffers.poll()) != null && freedCount < 4) {
                    freedCount++;
                }
                logger.warn("Freed {} buffers from pool due to out of memory", freedCount);
                totalBuffers.addAndGet(-freedCount);
            } finally {
                cleanupLock.unlock();
            }

            // Retry allocation
            totalBuffers.incrementAndGet();
            return ByteBuffer.allocateDirect(size);
        }
    }

    /**
     * Gets statistics about the buffer pool.
     *
     * @return statistics string
     */
    public String getStats() {
        return String.format(
                "ByteBufferPool Stats - Total: %d, Available: %d, In Use: %d, Default Size: %d",
                getTotalCount(), getAvailableCount(), getBuffersInUse(), getDefaultBufferSize());
    }
}