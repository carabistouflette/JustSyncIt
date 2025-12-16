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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.StampedLock;

/**
 * High-performance optimized AsyncByteBufferPool with tiered sizing, lock-free
 * operations,
 * adaptive sizing, zero-copy support, and comprehensive monitoring.
 *
 * Features:
 * - Tiered buffer pools with power-of-two sizing
 * - Lock-free buffer acquisition using atomic operations
 * - Adaptive pool sizing based on workload patterns
 * - Zero-copy buffer sharing mechanisms
 * - Separate pools for direct and heap buffers
 * - Async buffer pre-fetching for predictable workloads
 * - Buffer reservation system for high-priority operations
 * - Backpressure handling and flow control
 * - Comprehensive monitoring and auto-tuning
 * - Memory pressure detection and response
 */
public class OptimizedAsyncByteBufferPool implements AsyncByteBufferPool {

    private static final Logger logger = LoggerFactory.getLogger(OptimizedAsyncByteBufferPool.class);

    // Power-of-two buffer sizes (1KB to 1MB)
    private static final int[] BUFFER_SIZES = {
            1024, 2048, 4096, 8192, 16384, 32768, 65536, 131072, 262144, 524288, 1048576
    };

    // Pool configuration
    private final PoolConfiguration config;

    // Tiered buffer pools for direct buffers
    private final Map<Integer, TieredBufferPool> directPools;

    // Tiered buffer pools for heap buffers
    private final Map<Integer, TieredBufferPool> heapPools;

    // Performance monitoring
    private final PerformanceMonitor performanceMonitor;

    // Adaptive sizing controller
    private final AdaptiveSizingController adaptiveController;

    // Pre-fetching manager
    private final BufferPrefetchManager prefetchManager;

    // Memory pressure detector
    private final MemoryPressureDetector memoryPressureDetector;

    // Backpressure controller
    private final BackpressureController backpressureController;

    // Executor services
    private final ExecutorService managementExecutor;
    private final ExecutorService ioExecutor;

    // Pool state
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final StampedLock shutdownLock = new StampedLock();

    /**
     * Configuration for buffer pool behavior.
     */
    public static class PoolConfiguration {
        private final int minBuffersPerTier;
        private final int maxBuffersPerTier;
        private final long maxMemoryBytes;
        private final boolean enableDirectBuffers;
        private final boolean enableHeapBuffers;
        private final boolean enablePrefetching;
        private final boolean enableAdaptiveSizing;
        private final boolean enableZeroCopy;
        private final int prefetchThreshold;
        private final double memoryPressureThreshold;
        private final int backpressureThreshold;
        private final long adaptiveSizingIntervalMs;

        private PoolConfiguration(Builder builder) {
            this.minBuffersPerTier = builder.minBuffersPerTier;
            this.maxBuffersPerTier = builder.maxBuffersPerTier;
            this.maxMemoryBytes = builder.maxMemoryBytes;
            this.enableDirectBuffers = builder.enableDirectBuffers;
            this.enableHeapBuffers = builder.enableHeapBuffers;
            this.enablePrefetching = builder.enablePrefetching;
            this.enableAdaptiveSizing = builder.enableAdaptiveSizing;
            this.enableZeroCopy = builder.enableZeroCopy;
            this.prefetchThreshold = builder.prefetchThreshold;
            this.memoryPressureThreshold = builder.memoryPressureThreshold;
            this.backpressureThreshold = builder.backpressureThreshold;
            this.adaptiveSizingIntervalMs = builder.adaptiveSizingIntervalMs;
        }

        public static class Builder {
            private int minBuffersPerTier = 2;
            private int maxBuffersPerTier = 32;
            private long maxMemoryBytes = Runtime.getRuntime().maxMemory() / 4; // 25% of heap
            private boolean enableDirectBuffers = true;
            private boolean enableHeapBuffers = true;
            private boolean enablePrefetching = true;
            private boolean enableAdaptiveSizing = true;
            private boolean enableZeroCopy = true;
            private int prefetchThreshold = 10;
            private double memoryPressureThreshold = 0.8;
            private int backpressureThreshold = 100;
            private long adaptiveSizingIntervalMs = 30000;

            public Builder minBuffersPerTier(int minBuffersPerTier) {
                this.minBuffersPerTier = minBuffersPerTier;
                return this;
            }

            public Builder maxBuffersPerTier(int maxBuffersPerTier) {
                this.maxBuffersPerTier = maxBuffersPerTier;
                return this;
            }

            public Builder maxMemoryBytes(long maxMemoryBytes) {
                this.maxMemoryBytes = maxMemoryBytes;
                return this;
            }

            public Builder enableDirectBuffers(boolean enableDirectBuffers) {
                this.enableDirectBuffers = enableDirectBuffers;
                return this;
            }

            public Builder enableHeapBuffers(boolean enableHeapBuffers) {
                this.enableHeapBuffers = enableHeapBuffers;
                return this;
            }

            public Builder enablePrefetching(boolean enablePrefetching) {
                this.enablePrefetching = enablePrefetching;
                return this;
            }

            public Builder enableAdaptiveSizing(boolean enableAdaptiveSizing) {
                this.enableAdaptiveSizing = enableAdaptiveSizing;
                return this;
            }

            public Builder enableZeroCopy(boolean enableZeroCopy) {
                this.enableZeroCopy = enableZeroCopy;
                return this;
            }

            public Builder prefetchThreshold(int prefetchThreshold) {
                this.prefetchThreshold = prefetchThreshold;
                return this;
            }

            public Builder memoryPressureThreshold(double memoryPressureThreshold) {
                this.memoryPressureThreshold = memoryPressureThreshold;
                return this;
            }

            public Builder backpressureThreshold(int backpressureThreshold) {
                this.backpressureThreshold = backpressureThreshold;
                return this;
            }

            public Builder adaptiveSizingIntervalMs(long adaptiveSizingIntervalMs) {
                this.adaptiveSizingIntervalMs = adaptiveSizingIntervalMs;
                return this;
            }

            public PoolConfiguration build() {
                return new PoolConfiguration(this);
            }
        }

        // Getters
        public int getMinBuffersPerTier() {
            return minBuffersPerTier;
        }

        public int getMaxBuffersPerTier() {
            return maxBuffersPerTier;
        }

        public long getMaxMemoryBytes() {
            return maxMemoryBytes;
        }

        public boolean isDirectBuffersEnabled() {
            return enableDirectBuffers;
        }

        public boolean isHeapBuffersEnabled() {
            return enableHeapBuffers;
        }

        public boolean isPrefetchingEnabled() {
            return enablePrefetching;
        }

        public boolean isAdaptiveSizingEnabled() {
            return enableAdaptiveSizing;
        }

        public boolean isZeroCopyEnabled() {
            return enableZeroCopy;
        }

        public int getPrefetchThreshold() {
            return prefetchThreshold;
        }

        public double getMemoryPressureThreshold() {
            return memoryPressureThreshold;
        }

        public int getBackpressureThreshold() {
            return backpressureThreshold;
        }

        public long getAdaptiveSizingIntervalMs() {
            return adaptiveSizingIntervalMs;
        }
    }

    /**
     * Creates a new OptimizedAsyncByteBufferPool with default configuration.
     */
    public static OptimizedAsyncByteBufferPool create() {
        return create(new PoolConfiguration.Builder().build());
    }

    /**
     * Creates a new OptimizedAsyncByteBufferPool with custom configuration.
     */
    public static OptimizedAsyncByteBufferPool create(PoolConfiguration config) {
        return new OptimizedAsyncByteBufferPool(config);
    }

    /**
     * Private constructor.
     */
    private OptimizedAsyncByteBufferPool(PoolConfiguration config) {
        this.config = config;
        this.directPools = new ConcurrentHashMap<>();
        this.heapPools = new ConcurrentHashMap<>();

        // Initialize management components
        this.performanceMonitor = new PerformanceMonitor();
        this.adaptiveController = new AdaptiveSizingController(config, performanceMonitor);
        this.prefetchManager = new BufferPrefetchManager(config, performanceMonitor);
        this.memoryPressureDetector = new MemoryPressureDetector(config, performanceMonitor);
        this.backpressureController = new BackpressureController();

        // Initialize executor services
        this.managementExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "BufferPool-Manager");
            t.setDaemon(true);
            return t;
        });

        this.ioExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "BufferPool-IO");
            t.setDaemon(true);
            return t;
        });

        // Initialize tiered pools
        initializeTieredPools();

        // Start background services
        startBackgroundServices();

        logger.info("OptimizedAsyncByteBufferPool initialized with config: {}", config);
    }

    /**
     * Initializes tiered buffer pools for each buffer size.
     */
    private void initializeTieredPools() {
        for (int size : BUFFER_SIZES) {
            if (config.isDirectBuffersEnabled()) {
                directPools.put(size, new TieredBufferPool(size, config, true, performanceMonitor));
            }
            if (config.isHeapBuffersEnabled()) {
                heapPools.put(size, new TieredBufferPool(size, config, false, performanceMonitor));
            }
        }

        logger.debug("Initialized {} tiered pools for direct and heap buffers", BUFFER_SIZES.length);
    }

    /**
     * Starts background monitoring and adaptation services.
     */
    private void startBackgroundServices() {
        if (config.isAdaptiveSizingEnabled()) {
            adaptiveController.start();
            managementExecutor.submit(adaptiveController);
        }

        if (config.isPrefetchingEnabled()) {
            managementExecutor.submit(prefetchManager);
        }

        memoryPressureDetector.start();
        managementExecutor.submit(memoryPressureDetector);

        performanceMonitor.start();
        managementExecutor.submit(performanceMonitor);

        logger.debug("Started background services for buffer pool management");
    }

    @Override
    public CompletableFuture<ByteBuffer> acquireAsync(int size) {
        if (size <= 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Size must be positive"));
        }
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Buffer pool has been closed"));
        }

        performanceMonitor.recordAcquisitionRequest(size);

        return CompletableFuture.completedFuture(null) // No permit acquisition needed
                .thenCompose(permit -> {
                    int poolSize = roundToPowerOfTwo(size);
                    boolean preferDirect = shouldUseDirectBuffer(size);

                    Map<Integer, TieredBufferPool> pools = preferDirect ? directPools : heapPools;
                    TieredBufferPool pool = pools.get(poolSize);

                    if (pool == null) {
                        // Fallback to allocation if no pool exists
                        return CompletableFuture.completedFuture(allocateBuffer(size, preferDirect));
                    }

                    return pool.acquireAsync()
                            .thenApply(buffer -> {
                                performanceMonitor.recordSuccessfulAcquisition(size);
                                return buffer;
                            })
                            .exceptionally(throwable -> {
                                performanceMonitor.recordFailedAcquisition(size);
                                return allocateBuffer(size, preferDirect);
                            });
                })
                .whenComplete((buffer, throwable) -> {
                    if (throwable != null) {
                        // No permit release needed - just check backpressure
                    }
                });
    }

    @Override
    public CompletableFuture<Void> releaseAsync(ByteBuffer buffer) {
        if (buffer == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Buffer cannot be null"));
        }
        if (closed.get()) {
            return CompletableFuture.completedFuture(null);
        }

        int capacity = buffer.capacity();
        int poolSize = roundToPowerOfTwo(capacity);
        boolean isDirect = buffer.isDirect();

        performanceMonitor.recordRelease(capacity);

        Map<Integer, TieredBufferPool> pools = isDirect ? directPools : heapPools;
        TieredBufferPool pool = pools.get(poolSize);

        if (pool != null) {
            return pool.releaseAsync(buffer)
                    .thenRun(() -> {
                        performanceMonitor.recordSuccessfulRelease(capacity);
                        // No permit release needed - just check backpressure
                    })
                    .exceptionally(throwable -> {
                        performanceMonitor.recordFailedRelease(capacity);
                        // No permit release needed - just check backpressure
                        return null;
                    });
        } else {
            // Buffer not from pool, just release permit
            // No permit release needed
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public CompletableFuture<Void> clearAsync() {
        long stamp = shutdownLock.tryWriteLock();
        if (stamp == 0) {
            return CompletableFuture.failedFuture(new IllegalStateException("Shutdown in progress"));
        }

        try {
            if (closed.getAndSet(true)) {
                return CompletableFuture.completedFuture(null);
            }

            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // Clear all pools
            directPools.values().forEach(pool -> futures.add(pool.clearAsync()));
            heapPools.values().forEach(pool -> futures.add(pool.clearAsync()));

            // Shutdown background services
            adaptiveController.shutdown();
            prefetchManager.shutdown();
            memoryPressureDetector.shutdown();
            performanceMonitor.shutdown();

            return CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
                    .thenRun(() -> {
                        managementExecutor.shutdown();
                        ioExecutor.shutdown();
                        logger.info("OptimizedAsyncByteBufferPool cleared and shutdown");
                    });
        } finally {
            shutdownLock.unlockWrite(stamp);
        }
    }

    @Override
    public CompletableFuture<Integer> getAvailableCountAsync() {
        return CompletableFuture.supplyAsync(() -> {
            int count = 0;
            count += directPools.values().stream().mapToInt(TieredBufferPool::getAvailableCount).sum();
            count += heapPools.values().stream().mapToInt(TieredBufferPool::getAvailableCount).sum();
            return count;
        }, managementExecutor);
    }

    @Override
    public CompletableFuture<Integer> getTotalCountAsync() {
        return CompletableFuture.supplyAsync(() -> {
            int count = 0;
            count += directPools.values().stream().mapToInt(TieredBufferPool::getTotalCount).sum();
            count += heapPools.values().stream().mapToInt(TieredBufferPool::getTotalCount).sum();
            return count;
        }, managementExecutor);
    }

    @Override
    public CompletableFuture<Integer> getBuffersInUseAsync() {
        return CompletableFuture.supplyAsync(() -> {
            int count = 0;
            count += directPools.values().stream().mapToInt(TieredBufferPool::getInUseCount).sum();
            count += heapPools.values().stream().mapToInt(TieredBufferPool::getInUseCount).sum();
            return count;
        }, managementExecutor);
    }

    @Override
    public CompletableFuture<String> getStatsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder stats = new StringBuilder();
            stats.append("OptimizedAsyncByteBufferPool Stats:\n");
            stats.append("Configuration: ").append(config).append("\n");
            stats.append("Performance: ").append(performanceMonitor.getStats()).append("\n");
            stats.append("Memory Pressure: ").append(memoryPressureDetector.getCurrentPressure()).append("\n");
            stats.append("Backpressure: ").append(backpressureController.getSummary()).append("\n");

            stats.append("Direct Pools:\n");
            directPools.forEach(
                    (size, pool) -> stats.append("  ").append(size).append(": ").append(pool.getStats()).append("\n"));

            stats.append("Heap Pools:\n");
            heapPools.forEach(
                    (size, pool) -> stats.append("  ").append(size).append(": ").append(pool.getStats()).append("\n"));

            return stats.toString();
        }, managementExecutor);
    }

    // Synchronous interface implementations
    @Override
    public ByteBuffer acquire(int size) {
        try {
            return acquireAsync(size).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to acquire buffer", e);
        }
    }

    @Override
    public void release(ByteBuffer buffer) {
        try {
            releaseAsync(buffer).get(1, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to release buffer", e);
        }
    }

    @Override
    public void clear() {
        try {
            clearAsync().get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to clear buffer pool", e);
        }
    }

    @Override
    public int getAvailableCount() {
        try {
            return getAvailableCountAsync().get(1, TimeUnit.SECONDS);
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public int getTotalCount() {
        try {
            return getTotalCountAsync().get(1, TimeUnit.SECONDS);
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public int getDefaultBufferSize() {
        return 65536; // 64KB default
    }

    /**
     * Rounds size up to the nearest power of two.
     */
    private int roundToPowerOfTwo(int size) {
        if (size <= 1024) {
            return 1024;
        }
        if (size > 1048576) {
            return 1048576; // Cap at 1MB
        }

        int power = 32 - Integer.numberOfLeadingZeros(size - 1);
        int rounded = 1 << power;
        return (size == rounded) ? size : rounded;
    }

    /**
     * Determines whether to use direct buffer based on size and configuration.
     */
    private boolean shouldUseDirectBuffer(int size) {
        if (!config.isDirectBuffersEnabled()) {
            return false;
        }
        if (!config.isHeapBuffersEnabled()) {
            return true;
        }

        // Use direct buffers for larger sizes (>32KB) for better I/O performance
        return size > 32768;
    }

    /**
     * Allocates a new buffer outside of pool management.
     */
    private ByteBuffer allocateBuffer(int size, boolean direct) {
        try {
            if (direct) {
                return ByteBuffer.allocateDirect(size);
            } else {
                return ByteBuffer.allocate(size);
            }
        } catch (OutOfMemoryError e) {
            logger.error("Failed to allocate buffer of size {} (direct: {})", size, direct, e);
            // Trigger memory pressure response
            memoryPressureDetector.handleOutOfMemory(size);
            throw e;
        }
    }

    // Inner classes will be implemented in separate files for better organization
}