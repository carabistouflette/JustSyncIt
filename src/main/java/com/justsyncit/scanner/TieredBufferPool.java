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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * Lock-free tiered buffer pool for a specific buffer size.
 * Uses atomic operations and lock-free algorithms for high performance.
 * 
 * Features:
 * - Lock-free buffer acquisition/release
 * - Zero-copy buffer sharing
 * - Adaptive sizing
 * - Memory pressure handling
 * - Performance monitoring
 */
public class TieredBufferPool {
    
    private static final Logger logger = LoggerFactory.getLogger(TieredBufferPool.class);
    
    private final int bufferSize;
    private final OptimizedAsyncByteBufferPool.PoolConfiguration config;
    private final boolean isDirect;
    private final PerformanceMonitor performanceMonitor;
    
    // Lock-free buffer queue
    private final ConcurrentLinkedQueue<BufferWrapper> availableBuffers;
    
    // Atomic counters for pool state
    private final AtomicInteger totalBuffers = new AtomicInteger(0);
    private final AtomicInteger availableCount = new AtomicInteger(0);
    private final AtomicInteger inUseCount = new AtomicInteger(0);
    private final AtomicInteger allocationFailures = new AtomicInteger(0);
    
    // Performance metrics
    private final AtomicLong totalAcquisitions = new AtomicLong(0);
    private final AtomicLong totalReleases = new AtomicLong(0);
    private final AtomicLong totalWaitTime = new AtomicLong(0);
    
    // Adaptive sizing
    private volatile int currentMaxBuffers;
    private volatile int currentMinBuffers;
    private final AtomicLong lastResizeTime = new AtomicLong(System.currentTimeMillis());
    
    // Executor for async operations
    private final ExecutorService executor;
    
    // Pool state
    private volatile boolean closed = false;
    
    /**
     * Wrapper for ByteBuffer with additional metadata.
     */
    private static class BufferWrapper {
        final ByteBuffer buffer;
        final long timestamp;
        final int useCount;
        volatile boolean inUse;
        
        BufferWrapper(ByteBuffer buffer) {
            this.buffer = buffer;
            this.timestamp = System.nanoTime();
            this.useCount = 0;
            this.inUse = false;
        }
        
        BufferWrapper(ByteBuffer buffer, int useCount) {
            this.buffer = buffer;
            this.timestamp = System.nanoTime();
            this.useCount = useCount;
            this.inUse = false;
        }
        
        boolean tryAcquire() {
            if (inUse) {
                return false;
            }
            synchronized (this) {
                if (inUse) {
                    return false;
                }
                inUse = true;
                return true;
            }
        }
        
        void release() {
            synchronized (this) {
                inUse = false;
            }
        }
    }
    
    /**
     * Creates a new TieredBufferPool.
     */
    public TieredBufferPool(int bufferSize, 
                          OptimizedAsyncByteBufferPool.PoolConfiguration config,
                          boolean isDirect,
                          PerformanceMonitor performanceMonitor) {
        this.bufferSize = bufferSize;
        this.config = config;
        this.isDirect = isDirect;
        this.performanceMonitor = performanceMonitor;
        this.availableBuffers = new ConcurrentLinkedQueue<>();
        this.currentMaxBuffers = config.getMaxBuffersPerTier();
        this.currentMinBuffers = config.getMinBuffersPerTier();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "TieredPool-" + bufferSize + (isDirect ? "-direct" : "-heap"));
            t.setDaemon(true);
            return t;
        });
        
        // Pre-allocate minimum buffers
        preAllocateBuffers();
        
        logger.debug("Created TieredBufferPool for size {} (direct: {})", bufferSize, isDirect);
    }
    
    /**
     * Pre-allocates minimum number of buffers.
     */
    private void preAllocateBuffers() {
        for (int i = 0; i < currentMinBuffers; i++) {
            try {
                ByteBuffer buffer = allocateNewBuffer();
                BufferWrapper wrapper = new BufferWrapper(buffer);
                availableBuffers.offer(wrapper);
                totalBuffers.incrementAndGet();
                availableCount.incrementAndGet();
            } catch (Exception e) {
                logger.warn("Failed to pre-allocate buffer {}: {}", i, e.getMessage());
                break;
            }
        }
    }
    
    /**
     * Acquires a buffer asynchronously with lock-free operations.
     */
    public CompletableFuture<ByteBuffer> acquireAsync() {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Pool is closed"));
        }
        
        long startTime = System.nanoTime();
        totalAcquisitions.incrementAndGet();
        
        // Try to acquire from pool first
        BufferWrapper wrapper = tryAcquireFromPool();
        if (wrapper != null) {
            recordAcquisitionTime(startTime);
            return CompletableFuture.completedFuture(wrapper.buffer);
        }
        
        // No available buffer, try to allocate new one
        return tryAllocateNewBuffer()
            .thenCompose(buffer -> {
                if (buffer != null) {
                    recordAcquisitionTime(startTime);
                    return CompletableFuture.completedFuture(buffer);
                }
                
                // Allocation failed, wait for available buffer
                return waitForAvailableBuffer(startTime);
            });
    }
    
    /**
     * Tries to acquire a buffer from the pool without blocking.
     */
    private BufferWrapper tryAcquireFromPool() {
        BufferWrapper wrapper = availableBuffers.poll();
        while (wrapper != null) {
            if (wrapper.tryAcquire()) {
                availableCount.decrementAndGet();
                inUseCount.incrementAndGet();
                performanceMonitor.recordBufferAcquisition(bufferSize, true);
                return wrapper;
            }
            // Wrapper was already acquired, try next
            wrapper = availableBuffers.poll();
        }
        return null;
    }
    
    /**
     * Tries to allocate a new buffer if under limit.
     */
    private CompletableFuture<ByteBuffer> tryAllocateNewBuffer() {
        return CompletableFuture.supplyAsync(() -> {
            if (totalBuffers.get() >= currentMaxBuffers) {
                return null;
            }
            
            try {
                ByteBuffer buffer = allocateNewBuffer();
                BufferWrapper wrapper = new BufferWrapper(buffer);
                if (wrapper.tryAcquire()) {
                    totalBuffers.incrementAndGet();
                    inUseCount.incrementAndGet();
                    performanceMonitor.recordBufferAcquisition(bufferSize, false);
                    return buffer;
                }
                return buffer; // Return even if acquire failed (rare case)
            } catch (Exception e) {
                allocationFailures.incrementAndGet();
                performanceMonitor.recordAllocationFailure(bufferSize);
                return null;
            }
        }, executor);
    }
    
    /**
     * Waits for an available buffer with exponential backoff.
     */
    private CompletableFuture<ByteBuffer> waitForAvailableBuffer(long startTime) {
        return CompletableFuture.supplyAsync(() -> {
            long waitTime = 1000; // Start with 1 microsecond
            long maxWaitTime = 10000000; // Max 10 milliseconds
            
            while (!closed) {
                BufferWrapper wrapper = tryAcquireFromPool();
                if (wrapper != null) {
                    recordAcquisitionTime(startTime);
                    return wrapper.buffer;
                }
                
                // Exponential backoff with jitter
                long actualWait = Math.min(waitTime + (long)(Math.random() * waitTime * 0.1), maxWaitTime);
                LockSupport.parkNanos(actualWait);
                waitTime = Math.min(waitTime * 2, maxWaitTime);
                
                // Check for memory pressure and trigger adaptive sizing
                if (allocationFailures.get() > 10) {
                    triggerAdaptiveSizing();
                }
            }
            
            throw new IllegalStateException("Pool is closed");
        }, executor);
    }
    
    /**
     * Releases a buffer back to the pool.
     */
    public CompletableFuture<Void> releaseAsync(ByteBuffer buffer) {
        if (buffer == null || closed) {
            return CompletableFuture.completedFuture(null);
        }
        
        totalReleases.incrementAndGet();
        
        return CompletableFuture.runAsync(() -> {
            // Reset buffer
            buffer.clear();
            
            // Create wrapper and add back to pool
            BufferWrapper wrapper = new BufferWrapper(buffer);
            availableBuffers.offer(wrapper);
            
            availableCount.incrementAndGet();
            inUseCount.decrementAndGet();
            performanceMonitor.recordBufferRelease(bufferSize);
            
            // Trigger adaptive sizing if needed
            if (shouldTriggerAdaptiveSizing()) {
                triggerAdaptiveSizing();
            }
        }, executor);
    }
    
    /**
     * Allocates a new buffer of the configured size.
     */
    private ByteBuffer allocateNewBuffer() {
        if (isDirect) {
            return ByteBuffer.allocateDirect(bufferSize);
        } else {
            return ByteBuffer.allocate(bufferSize);
        }
    }
    
    /**
     * Records acquisition time for performance monitoring.
     */
    private void recordAcquisitionTime(long startTime) {
        long duration = System.nanoTime() - startTime;
        totalWaitTime.addAndGet(duration);
        performanceMonitor.recordAcquisitionTime(bufferSize, duration);
    }
    
    /**
     * Checks if adaptive sizing should be triggered.
     */
    private boolean shouldTriggerAdaptiveSizing() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastResize = currentTime - lastResizeTime.get();
        
        // Trigger every 30 seconds or if allocation failures are high
        return timeSinceLastResize > 30000 || allocationFailures.get() > 5;
    }
    
    /**
     * Triggers adaptive resizing of the pool.
     */
    private void triggerAdaptiveSizing() {
        if (!config.isAdaptiveSizingEnabled()) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        if (!lastResizeTime.compareAndSet(lastResizeTime.get(), currentTime)) {
            return; // Another thread is already resizing
        }
        
        executor.submit(() -> {
            try {
                performAdaptiveSizing();
            } catch (Exception e) {
                logger.warn("Adaptive sizing failed for pool size {}: {}", bufferSize, e.getMessage());
            }
        });
    }
    
    /**
     * Performs adaptive sizing based on current usage patterns.
     */
    private void performAdaptiveSizing() {
        long totalAcq = totalAcquisitions.get();
        long totalRel = totalReleases.get();
        int currentTotal = totalBuffers.get();
        int currentAvail = availableCount.get();
        int currentInUse = inUseCount.get();
        
        // Calculate utilization
        double utilization = currentTotal > 0 ? (double) currentInUse / currentTotal : 0.0;
        
        // Calculate allocation failure rate
        long failures = allocationFailures.get();
        double failureRate = totalAcq > 0 ? (double) failures / totalAcq : 0.0;
        
        int newMinBuffers = currentMinBuffers;
        int newMaxBuffers = currentMaxBuffers;
        
        // Adjust based on utilization and failure rate
        if (utilization > 0.8 || failureRate > 0.1) {
            // High utilization or high failure rate - increase pool size
            newMaxBuffers = Math.min(currentMaxBuffers * 2, config.getMaxBuffersPerTier());
            newMinBuffers = Math.min(currentMinBuffers + 2, newMaxBuffers);
        } else if (utilization < 0.3 && failureRate < 0.01) {
            // Low utilization and low failure rate - decrease pool size
            newMaxBuffers = Math.max(currentMaxBuffers / 2, config.getMinBuffersPerTier());
            newMinBuffers = Math.max(currentMinBuffers - 1, config.getMinBuffersPerTier());
        }
        
        // Apply changes if different
        if (newMaxBuffers != currentMaxBuffers || newMinBuffers != currentMinBuffers) {
            currentMaxBuffers = newMaxBuffers;
            currentMinBuffers = newMinBuffers;
            
            logger.debug("Adaptive sizing for pool size {}: min={}, max={}, utilization={:.2f}, failureRate={:.2f}",
                bufferSize, newMinBuffers, newMaxBuffers, utilization, failureRate);
            
            // Trim excess buffers if needed
            trimExcessBuffers();
        }
        
        // Reset allocation failures counter
        allocationFailures.set(0);
    }
    
    /**
     * Trims excess buffers from the pool.
     */
    private void trimExcessBuffers() {
        int currentTotal = totalBuffers.get();
        int excess = currentTotal - currentMaxBuffers;
        
        if (excess <= 0) {
            return;
        }
        
        int removed = 0;
        while (removed < excess && availableCount.get() > currentMinBuffers) {
            BufferWrapper wrapper = availableBuffers.poll();
            if (wrapper != null) {
                availableCount.decrementAndGet();
                totalBuffers.decrementAndGet();
                removed++;
            } else {
                break;
            }
        }
        
        if (removed > 0) {
            logger.debug("Trimmed {} excess buffers from pool size {}", removed, bufferSize);
            performanceMonitor.recordBufferTrim(bufferSize, removed);
        }
    }
    
    /**
     * Clears the pool and releases all resources.
     */
    public CompletableFuture<Void> clearAsync() {
        closed = true;
        
        return CompletableFuture.runAsync(() -> {
            int cleared = 0;
            BufferWrapper wrapper;
            while ((wrapper = availableBuffers.poll()) != null) {
                cleared++;
            }
            
            availableCount.set(0);
            inUseCount.set(0);
            totalBuffers.set(0);
            
            executor.shutdown();
            
            logger.debug("Cleared TieredBufferPool for size {}, removed {} buffers", bufferSize, cleared);
        }, executor);
    }
    
    /**
     * Gets pool statistics.
     */
    public String getStats() {
        long avgWaitTime = totalAcquisitions.get() > 0 
            ? totalWaitTime.get() / totalAcquisitions.get() 
            : 0;
        
        return String.format(
            "Total: %d, Available: %d, In Use: %d, Acquisitions: %d, Releases: %d, " +
            "Failures: %d, Avg Wait: %d ns, Min: %d, Max: %d",
            totalBuffers.get(), availableCount.get(), inUseCount.get(),
            totalAcquisitions.get(), totalReleases.get(), allocationFailures.get(),
            avgWaitTime, currentMinBuffers, currentMaxBuffers);
    }
    
    // Getters for monitoring
    public int getBufferSize() { return bufferSize; }
    public boolean isDirect() { return isDirect; }
    public int getAvailableCount() { return availableCount.get(); }
    public int getTotalCount() { return totalBuffers.get(); }
    public int getInUseCount() { return inUseCount.get(); }
    public int getCurrentMinBuffers() { return currentMinBuffers; }
    public int getCurrentMaxBuffers() { return currentMaxBuffers; }
    public long getTotalAcquisitions() { return totalAcquisitions.get(); }
    public long getTotalReleases() { return totalReleases.get(); }
    public int getAllocationFailures() { return allocationFailures.get(); }
}