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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implementation of AsyncByteBufferPool with non-blocking operations.
 * Uses direct ByteBuffers for optimal I/O performance.
 * Follows Single Responsibility Principle by focusing only on async buffer management.
 */
public class AsyncByteBufferPoolImpl implements AsyncByteBufferPool {

    /** Logger for the async buffer pool. */
    private static final Logger logger = LoggerFactory.getLogger(AsyncByteBufferPoolImpl.class);

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
    private final ReentrantLock cleanupLock;
    /** Executor service for async operations. */
    private final ExecutorService executorService;

    /**
     * Creates a new AsyncByteBufferPoolImpl with default settings.
     *
     * @return a new AsyncByteBufferPoolImpl with default settings
     * @throws IllegalArgumentException if default settings are invalid
     */
    public static AsyncByteBufferPoolImpl create() {
        return create(DEFAULT_BUFFER_SIZE, DEFAULT_MAX_BUFFERS);
    }

    /**
     * Creates a new AsyncByteBufferPoolImpl with specified settings.
     *
     * @param defaultBufferSize default buffer size
     * @param maxBuffers maximum number of buffers
     * @return a new AsyncByteBufferPoolImpl with specified settings
     * @throws IllegalArgumentException if parameters are invalid
     */
    public static AsyncByteBufferPoolImpl create(int defaultBufferSize, int maxBuffers) {
        if (defaultBufferSize < MIN_BUFFER_SIZE || defaultBufferSize > MAX_BUFFER_SIZE) {
            throw new IllegalArgumentException(
                String.format("Buffer size must be between %d and %d bytes",
                    MIN_BUFFER_SIZE, MAX_BUFFER_SIZE));
        }
        if (maxBuffers <= 0) {
            throw new IllegalArgumentException("Max buffers must be positive");
        }

        return new AsyncByteBufferPoolImpl(defaultBufferSize, maxBuffers);
    }

    /**
     * Creates a new AsyncByteBufferPoolImpl with specified settings.
     */
    private AsyncByteBufferPoolImpl(int defaultBufferSize, int maxBuffers) {
        this.defaultBufferSize = defaultBufferSize;
        this.availableBuffers = new ConcurrentLinkedQueue<>();
        this.totalBuffers = new AtomicInteger(0);
        this.buffersInUse = new AtomicInteger(0);
        this.closed = false;
        this.cleanupLock = new ReentrantLock();
        this.executorService = Executors.newCachedThreadPool(new DaemonThreadFactory());

        // Pre-allocate some buffers
        int initialBuffers = Math.min(maxBuffers / 2, 4); // Start with half capacity or 4, whichever is smaller
        for (int i = 0; i < initialBuffers; i++) {
            availableBuffers.offer(allocateBuffer(defaultBufferSize));
            totalBuffers.incrementAndGet();
        }

        logger.debug("Created AsyncByteBufferPoolImpl with default size {} and max {} buffers, pre-allocated {}",
                    defaultBufferSize, maxBuffers, initialBuffers);
    }

    @Override
    public CompletableFuture<ByteBuffer> acquireAsync(int size) {
        if (size <= 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Size must be positive"));
        }
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Buffer pool has been closed"));
        }

        return CompletableFuture.supplyAsync(() -> {
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
        }, executorService);
    }

    @Override
    public CompletableFuture<Void> releaseAsync(ByteBuffer buffer) {
        if (buffer == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Buffer cannot be null"));
        }
        if (closed) {
            // Pool is closed, just let the buffer be garbage collected
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            buffer.clear();
            if (availableBuffers.offer(buffer)) {
                buffersInUse.decrementAndGet();
                logger.trace("Released buffer of size {}", buffer.capacity());
            } else {
                // This shouldn't happen in normal operation
                logger.warn("Failed to release buffer to pool - pool may be full");
            }
        }, executorService);
    }

    @Override
    public CompletableFuture<Void> clearAsync() {
        // First check if already closed to avoid unnecessary locking
        if (closed) {
            return CompletableFuture.completedFuture(null);
        }

        cleanupLock.lock();
        try {
            if (closed) {
                return CompletableFuture.completedFuture(null);
            }

            int clearedCount = 0;
            while ((availableBuffers.poll()) != null) {
                // Direct buffers don't need explicit cleanup, but we can track
                clearedCount++;
            }

            logger.info("Cleared AsyncByteBufferPoolImpl, removed {} buffers from pool", clearedCount);
            availableBuffers.clear();
            totalBuffers.set(0);
            buffersInUse.set(0);
            closed = true;
            
            // Capture executor reference while holding lock
            final ExecutorService executorToShutdown = executorService;
            
            // Schedule shutdown on a separate daemon thread to avoid deadlock
            CompletableFuture<Void> result = new CompletableFuture<>();
            Thread shutdownThread = new Thread(() -> {
                try {
                    if (executorToShutdown != null && !executorToShutdown.isShutdown()) {
                        shutdownExecutorService(executorToShutdown, "AsyncByteBufferPool async executor");
                    }
                    result.complete(null);
                } catch (Exception e) {
                    result.completeExceptionally(e);
                }
            }, "AsyncByteBufferPool-shutdown");
            shutdownThread.setDaemon(true);
            shutdownThread.start();
            
            return result;
        } finally {
            cleanupLock.unlock();
        }
    }

    @Override
    public CompletableFuture<Integer> getAvailableCountAsync() {
        return CompletableFuture.supplyAsync(() -> availableBuffers.size(), executorService);
    }

    @Override
    public CompletableFuture<Integer> getTotalCountAsync() {
        return CompletableFuture.supplyAsync(() -> totalBuffers.get(), executorService);
    }

    @Override
    public CompletableFuture<Integer> getBuffersInUseAsync() {
        return CompletableFuture.supplyAsync(() -> buffersInUse.get(), executorService);
    }

    @Override
    public CompletableFuture<String> getStatsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            return String.format(
                    "AsyncByteBufferPoolImpl Stats - Total: %d, Available: %d, In Use: %d, Default Size: %d",
                    getTotalCount(), getAvailableCount(), getBuffersInUse(), getDefaultBufferSize());
        }, executorService);
    }

    @Override
    public ByteBuffer acquire(int size) {
        logger.debug("Acquiring buffer of size {}", size);
        
        if (closed) {
            throw new IllegalStateException("Buffer pool has been closed");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be positive");
        }

        // Try to find an existing buffer that's large enough
        logger.debug("Looking for existing buffer, available count: {}", availableBuffers.size());
        ByteBuffer buffer = availableBuffers.poll();
        int attempts = 0;
        while (buffer != null && attempts < 10) { // Add safety limit to prevent infinite loop
            attempts++;
            logger.debug("Checking buffer {} with capacity {}", attempts, buffer.capacity());
            if (buffer.capacity() >= size) {
                buffersInUse.incrementAndGet();
                buffer.clear();
                logger.debug("Acquired existing buffer of size {} for request {} after {} attempts", buffer.capacity(), size, attempts);
                return buffer;
            }
            // Buffer is too small, put it back and try another
            logger.debug("Buffer too small, putting back and trying another");
            availableBuffers.offer(buffer);
            buffer = availableBuffers.poll();
        }

        if (attempts >= 10) {
            logger.error("Too many attempts ({}) to find suitable buffer, available count: {}", attempts, availableBuffers.size());
        }

        // No suitable buffer found, allocate a new one
        logger.debug("No suitable buffer found, allocating new one");
        int allocateSize = Math.max(size, defaultBufferSize);
        buffer = allocateBuffer(allocateSize);
        buffersInUse.incrementAndGet();

        logger.debug("Allocated new buffer of size {} for request {}", allocateSize, size);
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
        // Capture executor reference locally to minimize critical section
        ExecutorService executorToShutdown = null;
        
        // First check if already closed to avoid unnecessary locking
        if (closed) {
            return;
        }

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

            logger.info("Cleared AsyncByteBufferPoolImpl, removed {} buffers from pool", clearedCount);
            availableBuffers.clear();
            totalBuffers.set(0);
            buffersInUse.set(0);
            closed = true;
            
            // Capture executor reference while holding lock
            executorToShutdown = executorService;
        } finally {
            cleanupLock.unlock();
        }
        
        // Shutdown executor service outside of lock to avoid deadlock
        if (executorToShutdown != null && !executorToShutdown.isShutdown()) {
            shutdownExecutorService(executorToShutdown, "AsyncByteBufferPool sync executor");
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

    @Override
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
     * Gets the number of buffers currently in use.
     *
     * @return the number of buffers in use
     */
    public int getBuffersInUse() {
        return buffersInUse.get();
    }

    /**
     * Daemon thread factory for the async buffer pool.
     * Creates daemon threads that don't prevent JVM shutdown.
     */
    private static class DaemonThreadFactory implements ThreadFactory {
        private static final AtomicInteger threadNumber = new AtomicInteger(1);
        private static final String NAME_PREFIX = "AsyncByteBufferPool-worker-";

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, NAME_PREFIX + threadNumber.getAndIncrement());
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        }
    }

    /**
     * Helper method to shutdown executor service with proper error handling and logging.
     *
     * @param executor the executor service to shutdown
     * @param executorName name for logging purposes
     */
    private void shutdownExecutorService(ExecutorService executor, String executorName) {
        if (executor == null || executor.isShutdown()) {
            return;
        }
        
        try {
            executor.shutdown();
            if (!executor.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)) {
                logger.warn("{} did not terminate gracefully, forcing shutdown", executorName);
                executor.shutdownNow();
                // Give a short time for forced shutdown
                if (!executor.awaitTermination(500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    logger.error("{} failed to terminate completely", executorName);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while waiting for {} shutdown", executorName, e);
            executor.shutdownNow();
        }
    }
}