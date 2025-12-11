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

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Advanced performance optimizer for async directory scanning operations.
 * Implements prefetching, prediction algorithms, memory optimization,
 * and NUMA-aware scanning for optimal performance.
 */
public class AsyncDirectoryScanningOptimizer {

    private static final Logger logger = LoggerFactory.getLogger(AsyncDirectoryScanningOptimizer.class);

    /** Configuration for optimization strategies. */
    private final OptimizationConfig config;
    
    /** Thread pool manager for resource coordination. */
    private final ThreadPoolManager threadPoolManager;
    
    /** Performance monitor for metrics collection. */
    private final PerformanceMonitor performanceMonitor;
    
    /** Directory access pattern analyzer for prefetching. */
    private final DirectoryAccessPatternAnalyzer patternAnalyzer;
    
    /** Memory optimization manager. */
    private final MemoryOptimizationManager memoryManager;
    
    /** NUMA-aware scanning coordinator. */
    private final NumaAwareScanningCoordinator numaCoordinator;
    
    /** Prefetching manager for directory prediction. */
    private final DirectoryPrefetchingManager prefetchingManager;
    
    /** Statistics tracking. */
    private final OptimizationStats stats;

    /**
     * Configuration for optimization strategies.
     */
    public static class OptimizationConfig {
        private boolean enablePrefetching = true;
        private boolean enablePrediction = true;
        private boolean enableMemoryOptimization = true;
        private boolean enableNumaAwareness = true;
        private int prefetchDepth = 3;
        private int maxPrefetchDirectories = 10;
        private long memoryOptimizationThreshold = 100 * 1024 * 1024; // 100MB
        private int predictionWindowSize = 100;
        private double predictionConfidenceThreshold = 0.7;
        private boolean enableAdaptiveSizing = true;
        private int adaptiveSizingInterval = 30; // seconds

        // Builder pattern methods
        public OptimizationConfig withPrefetching(boolean enabled) {
            this.enablePrefetching = enabled;
            return this;
        }

        public OptimizationConfig withPrediction(boolean enabled) {
            this.enablePrediction = enabled;
            return this;
        }

        public OptimizationConfig withMemoryOptimization(boolean enabled) {
            this.enableMemoryOptimization = enabled;
            return this;
        }

        public OptimizationConfig withNumaAwareness(boolean enabled) {
            this.enableNumaAwareness = enabled;
            return this;
        }

        public OptimizationConfig withPrefetchDepth(int depth) {
            this.prefetchDepth = Math.max(1, depth);
            return this;
        }

        public OptimizationConfig withMaxPrefetchDirectories(int max) {
            this.maxPrefetchDirectories = Math.max(1, max);
            return this;
        }

        public OptimizationConfig withMemoryOptimizationThreshold(long threshold) {
            this.memoryOptimizationThreshold = Math.max(0, threshold);
            return this;
        }

        public OptimizationConfig withPredictionWindowSize(int size) {
            this.predictionWindowSize = Math.max(10, size);
            return this;
        }

        public OptimizationConfig withPredictionConfidenceThreshold(double threshold) {
            this.predictionConfidenceThreshold = Math.max(0.0, Math.min(1.0, threshold));
            return this;
        }

        public OptimizationConfig withAdaptiveSizing(boolean enabled) {
            this.enableAdaptiveSizing = enabled;
            return this;
        }

        public OptimizationConfig withAdaptiveSizingInterval(int interval) {
            this.adaptiveSizingInterval = Math.max(5, interval);
            return this;
        }

        // Getters
        public boolean isPrefetchingEnabled() { return enablePrefetching; }
        public boolean isPredictionEnabled() { return enablePrediction; }
        public boolean isMemoryOptimizationEnabled() { return enableMemoryOptimization; }
        public boolean isNumaAwarenessEnabled() { return enableNumaAwareness; }
        public int getPrefetchDepth() { return prefetchDepth; }
        public int getMaxPrefetchDirectories() { return maxPrefetchDirectories; }
        public long getMemoryOptimizationThreshold() { return memoryOptimizationThreshold; }
        public int getPredictionWindowSize() { return predictionWindowSize; }
        public double getPredictionConfidenceThreshold() { return predictionConfidenceThreshold; }
        public boolean isAdaptiveSizingEnabled() { return enableAdaptiveSizing; }
        public int getAdaptiveSizingInterval() { return adaptiveSizingInterval; }
    }

    /**
     * Creates a new AsyncDirectoryScanningOptimizer with default configuration.
     *
     * @param threadPoolManager thread pool manager for resource coordination
     * @param performanceMonitor performance monitor for metrics collection
     */
    public AsyncDirectoryScanningOptimizer(ThreadPoolManager threadPoolManager, 
                                        PerformanceMonitor performanceMonitor) {
        this(new OptimizationConfig(), threadPoolManager, performanceMonitor);
    }

    /**
     * Creates a new AsyncDirectoryScanningOptimizer with custom configuration.
     *
     * @param config optimization configuration
     * @param threadPoolManager thread pool manager for resource coordination
     * @param performanceMonitor performance monitor for metrics collection
     */
    public AsyncDirectoryScanningOptimizer(OptimizationConfig config,
                                         ThreadPoolManager threadPoolManager,
                                         PerformanceMonitor performanceMonitor) {
        this.config = config;
        this.threadPoolManager = threadPoolManager;
        this.performanceMonitor = performanceMonitor;
        this.stats = new OptimizationStats();
        
        // Initialize optimization components
        this.patternAnalyzer = new DirectoryAccessPatternAnalyzer(config);
        this.memoryManager = new MemoryOptimizationManager(config, performanceMonitor);
        this.numaCoordinator = new NumaAwareScanningCoordinator(config, threadPoolManager);
        this.prefetchingManager = new DirectoryPrefetchingManager(config, patternAnalyzer);
        
        logger.info("AsyncDirectoryScanningOptimizer initialized with config: prefetching={}, prediction={}, memoryOpt={}, numa={}",
            config.isPrefetchingEnabled(), config.isPredictionEnabled(), 
            config.isMemoryOptimizationEnabled(), config.isNumaAwarenessEnabled());
    }

    /**
     * Optimizes directory scanning performance for a given path.
     *
     * @param directory directory to optimize scanning for
     * @param scanOptions original scanning options
     * @return optimized scanning options
     */
    public CompletableFuture<AsyncScanOptions> optimizeDirectoryScanning(Path directory, AsyncScanOptions scanOptions) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Optimizing directory scanning for: {}", directory);
                
                AsyncScanOptions optimizedOptions = new AsyncScanOptions(scanOptions);
                
                // Apply prefetching optimization
                if (config.isPrefetchingEnabled()) {
                    optimizedOptions = prefetchingManager.applyPrefetchingOptimization(directory, optimizedOptions);
                }
                
                // Apply memory optimization
                if (config.isMemoryOptimizationEnabled()) {
                    optimizedOptions = memoryManager.applyMemoryOptimization(directory, optimizedOptions);
                }
                
                // Apply NUMA-aware optimization
                if (config.isNumaAwarenessEnabled()) {
                    optimizedOptions = numaCoordinator.applyNumaOptimization(directory, optimizedOptions);
                }
                
                // Apply adaptive sizing
                if (config.isAdaptiveSizingEnabled()) {
                    optimizedOptions = applyAdaptiveSizing(directory, optimizedOptions);
                }
                
                stats.optimizationRequests.incrementAndGet();
                logger.debug("Directory scanning optimization completed for: {}", directory);
                
                return optimizedOptions;
                
            } catch (Exception e) {
                logger.error("Failed to optimize directory scanning for: {}", directory, e);
                stats.optimizationErrors.incrementAndGet();
                return scanOptions; // Return original options on error
            }
        }, threadPoolManager.getManagementThreadPool());
    }

    /**
     * Applies adaptive sizing based on current performance metrics.
     */
    private AsyncScanOptions applyAdaptiveSizing(Path directory, AsyncScanOptions options) {
        try {
            // Get current performance metrics from PerformanceMonitor
            double cpuUtilization = getCpuUtilization();
            long memoryUsage = performanceMonitor.getCurrentMemoryUsage();
            
            AsyncScanOptions resultOptions = options;
            
            // Calculate optimal thread count based on system load and scan performance
            int optimalThreads = calculateOptimalThreadCount(cpuUtilization);
            if (optimalThreads != resultOptions.getParallelism()) {
                resultOptions = resultOptions.withParallelism(optimalThreads);
                stats.adaptiveSizingAdjustments.incrementAndGet();
                logger.debug("Adjusted thread count to {} for directory: {}", optimalThreads, directory);
            }
            
            // Calculate optimal buffer size based on memory pressure
            int optimalBufferSize = calculateOptimalBufferSize(memoryUsage);
            if (optimalBufferSize != resultOptions.getBatchSize()) {
                resultOptions = resultOptions.withBatchSize(optimalBufferSize);
                stats.adaptiveSizingAdjustments.incrementAndGet();
                logger.debug("Adjusted batch size to {} for directory: {}", optimalBufferSize, directory);
            }
            
            return resultOptions;
            
        } catch (Exception e) {
            logger.warn("Failed to apply adaptive sizing for directory: {}", directory, e);
            return options;
        }
    }

    /**
     * Calculates optimal thread count based on CPU utilization.
     */
    private int calculateOptimalThreadCount(double cpuUtilization) {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        
        if (cpuUtilization > 0.8) {
            // High CPU usage - reduce threads
            return Math.max(1, availableProcessors / 2);
        } else if (cpuUtilization < 0.5) {
            // Low CPU usage - can use more threads
            return Math.min(availableProcessors * 2, availableProcessors * 4);
        } else {
            // Moderate CPU usage - use available processors
            return availableProcessors;
        }
    }

    /**
     * Calculates optimal buffer size based on memory pressure.
     */
    private int calculateOptimalBufferSize(long memoryUsage) {
        long totalMemory = Runtime.getRuntime().totalMemory();
        double memoryPressure = (double) memoryUsage / totalMemory;
        
        int defaultBufferSize = 64; // Default batch size
        
        if (memoryPressure > 0.8) {
            // High memory pressure - use smaller buffers
            return Math.max(10, defaultBufferSize / 2);
        } else if (memoryPressure < 0.3) {
            // Low memory pressure - can use larger buffers
            return Math.min(200, defaultBufferSize * 2);
        } else {
            // Moderate memory pressure - use default
            return defaultBufferSize;
        }
    }

    /**
     * Gets current CPU utilization estimate.
     */
    private double getCpuUtilization() {
        // Simple CPU utilization estimation based on system load
        // In a real implementation, this would use proper OS-specific metrics
        double failureRate = performanceMonitor.getFailureRate();
        double avgAcquisitionTime = performanceMonitor.getAverageAcquisitionTime();
        
        // Estimate CPU utilization based on performance metrics
        if (failureRate > 0.1 || avgAcquisitionTime > 1000000) { // > 1ms
            return 0.9; // High utilization
        } else if (failureRate < 0.01 && avgAcquisitionTime < 100000) { // < 0.1ms
            return 0.3; // Low utilization
        } else {
            return 0.6; // Moderate utilization
        }
    }

    /**
     * Predicts next directories to be accessed based on access patterns.
     *
     * @param currentDirectory current directory being scanned
     * @return list of predicted directories
     */
    public CompletableFuture<List<Path>> predictNextDirectories(Path currentDirectory) {
        if (!config.isPredictionEnabled()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return patternAnalyzer.predictNextDirectories(currentDirectory, config.getPredictionWindowSize());
            } catch (Exception e) {
                logger.warn("Failed to predict next directories for: {}", currentDirectory, e);
                return new ArrayList<>();
            }
        }, threadPoolManager.getManagementThreadPool());
    }

    /**
     * Prefetches directory metadata for predicted directories.
     *
     * @param directories list of directories to prefetch
     * @return CompletableFuture that completes when prefetching is done
     */
    public CompletableFuture<Void> prefetchDirectoryMetadata(List<Path> directories) {
        if (!config.isPrefetchingEnabled() || directories.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        return CompletableFuture.runAsync(() -> {
            try {
                List<CompletableFuture<Void>> prefetchFutures = directories.stream()
                    .limit(config.getMaxPrefetchDirectories())
                    .map(directory -> prefetchingManager.prefetchDirectoryMetadata(directory))
                    .collect(Collectors.toList());
                
                CompletableFuture.allOf(prefetchFutures.toArray(new CompletableFuture[0]))
                    .thenRun(() -> {
                        stats.prefetchedDirectories.addAndGet(directories.size());
                        logger.debug("Prefetched metadata for {} directories", directories.size());
                    })
                    .exceptionally(throwable -> {
                        logger.warn("Failed to prefetch directory metadata", throwable);
                        stats.prefetchErrors.incrementAndGet();
                        return null;
                    });
                    
            } catch (Exception e) {
                logger.error("Failed to start directory prefetching", e);
                stats.prefetchErrors.incrementAndGet();
            }
        }, threadPoolManager.getIoThreadPool());
    }

    /**
     * Optimizes memory usage for directory scanning operations.
     *
     * @return CompletableFuture that completes when memory optimization is done
     */
    public CompletableFuture<Void> optimizeMemoryUsage() {
        if (!config.isMemoryOptimizationEnabled()) {
            return CompletableFuture.completedFuture(null);
        }
        
        return CompletableFuture.runAsync(() -> {
            try {
                memoryManager.optimizeMemoryUsage();
                stats.memoryOptimizations.incrementAndGet();
                logger.debug("Memory optimization completed");
            } catch (Exception e) {
                logger.error("Failed to optimize memory usage", e);
                stats.memoryOptimizationErrors.incrementAndGet();
            }
        }, threadPoolManager.getManagementThreadPool());
    }

    /**
     * Gets optimization statistics.
     *
     * @return optimization statistics
     */
    public OptimizationStats getStats() {
        return new OptimizationStats(stats);
    }

    /**
     * Statistics for optimization operations.
     */
    public static class OptimizationStats {
        public final AtomicLong optimizationRequests = new AtomicLong(0);
        public final AtomicLong optimizationErrors = new AtomicLong(0);
        public final AtomicLong adaptiveSizingAdjustments = new AtomicLong(0);
        public final AtomicLong prefetchedDirectories = new AtomicLong(0);
        public final AtomicLong prefetchErrors = new AtomicLong(0);
        public final AtomicLong memoryOptimizations = new AtomicLong(0);
        public final AtomicLong memoryOptimizationErrors = new AtomicLong(0);

        public OptimizationStats() {}

        public OptimizationStats(OptimizationStats other) {
            this.optimizationRequests.set(other.optimizationRequests.get());
            this.optimizationErrors.set(other.optimizationErrors.get());
            this.adaptiveSizingAdjustments.set(other.adaptiveSizingAdjustments.get());
            this.prefetchedDirectories.set(other.prefetchedDirectories.get());
            this.prefetchErrors.set(other.prefetchErrors.get());
            this.memoryOptimizations.set(other.memoryOptimizations.get());
            this.memoryOptimizationErrors.set(other.memoryOptimizationErrors.get());
        }

        @Override
        public String toString() {
            return String.format(
                "OptimizationStats{requests=%d, errors=%d, adaptiveAdjustments=%d, prefetched=%d, prefetchErrors=%d, memOpt=%d, memOptErrors=%d}",
                optimizationRequests.get(), optimizationErrors.get(), adaptiveSizingAdjustments.get(),
                prefetchedDirectories.get(), prefetchErrors.get(), memoryOptimizations.get(), memoryOptimizationErrors.get()
            );
        }
    }

    /**
     * Directory access pattern analyzer for prefetching and prediction.
     */
    private static class DirectoryAccessPatternAnalyzer {
        private final OptimizationConfig config;
        private final Map<Path, AccessPattern> accessPatterns = new ConcurrentHashMap<>();

        public DirectoryAccessPatternAnalyzer(OptimizationConfig config) {
            this.config = config;
        }

        public List<Path> predictNextDirectories(Path currentDirectory, int windowSize) {
            // Simple prediction based on recent access patterns
            AccessPattern pattern = accessPatterns.get(currentDirectory);
            if (pattern == null) {
                return new ArrayList<>();
            }
            
            return pattern.getRecentAccesses()
                .stream()
                .limit(windowSize)
                .collect(Collectors.toList());
        }

        public void recordAccess(Path directory) {
            accessPatterns.computeIfAbsent(directory, k -> new AccessPattern())
                .recordAccess();
        }

        private static class AccessPattern {
            private final List<Path> recentAccesses = new ArrayList<>();
            private final AtomicLong accessCount = new AtomicLong(0);

            public void recordAccess() {
                accessCount.incrementAndGet();
            }

            public List<Path> getRecentAccesses() {
                return new ArrayList<>(recentAccesses);
            }

            public long getAccessCount() {
                return accessCount.get();
            }
        }
    }

    /**
     * Memory optimization manager for directory scanning.
     */
    private static class MemoryOptimizationManager {
        private final OptimizationConfig config;
        private final PerformanceMonitor performanceMonitor;

        public MemoryOptimizationManager(OptimizationConfig config, PerformanceMonitor performanceMonitor) {
            this.config = config;
            this.performanceMonitor = performanceMonitor;
        }

        public AsyncScanOptions applyMemoryOptimization(Path directory, AsyncScanOptions options) {
            // Apply memory-specific optimizations based on current memory pressure
            long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            
            if (usedMemory > config.getMemoryOptimizationThreshold()) {
                // High memory usage - reduce buffer sizes and concurrent operations
                return options.withBatchSize(Math.max(10, options.getBatchSize() / 2))
                           .withParallelism(Math.max(1, options.getParallelism() / 2));
            }
            
            return options;
        }

        public void optimizeMemoryUsage() {
            // Trigger garbage collection and optimize memory usage
            System.gc();
            
            // Additional memory optimization logic can be added here
            // such as clearing caches, reducing buffer pools, etc.
        }
    }

    /**
     * NUMA-aware scanning coordinator for multi-socket systems.
     */
    private static class NumaAwareScanningCoordinator {
        private final OptimizationConfig config;
        private final ThreadPoolManager threadPoolManager;

        public NumaAwareScanningCoordinator(OptimizationConfig config, ThreadPoolManager threadPoolManager) {
            this.config = config;
            this.threadPoolManager = threadPoolManager;
        }

        public AsyncScanOptions applyNumaOptimization(Path directory, AsyncScanOptions options) {
            // NUMA-aware optimization logic
            // This would typically involve binding threads to specific NUMA nodes
            // and optimizing memory allocation for NUMA topology
            
            // For now, we'll just ensure thread count is optimal for NUMA
            int availableProcessors = Runtime.getRuntime().availableProcessors();
            if (availableProcessors > 8) {
                // Likely a multi-socket system - optimize for NUMA
                return options.withParallelism(Math.min(options.getParallelism(), availableProcessors / 2));
            }
            
            return options;
        }
    }

    /**
     * Directory prefetching manager for predictive caching.
     */
    private static class DirectoryPrefetchingManager {
        private final OptimizationConfig config;
        private final DirectoryAccessPatternAnalyzer patternAnalyzer;
        private final Map<Path, PrefetchCache> prefetchCache = new ConcurrentHashMap<>();

        public DirectoryPrefetchingManager(OptimizationConfig config, DirectoryAccessPatternAnalyzer patternAnalyzer) {
            this.config = config;
            this.patternAnalyzer = patternAnalyzer;
        }

        public AsyncScanOptions applyPrefetchingOptimization(Path directory, AsyncScanOptions options) {
            // Enable prefetching if supported
            return options.withPrefetchingEnabled(true)
                       .withPrefetchDepth(config.getPrefetchDepth());
        }

        public CompletableFuture<Void> prefetchDirectoryMetadata(Path directory) {
            return CompletableFuture.runAsync(() -> {
                try {
                    // Simulate prefetching directory metadata
                    // In a real implementation, this would read directory contents
                    // and cache them for faster access
                    
                    PrefetchCache cache = prefetchCache.computeIfAbsent(directory, k -> new PrefetchCache());
                    cache.updateLastPrefetch();
                    
                    logger.debug("Prefetched metadata for directory: {}", directory);
                } catch (Exception e) {
                    logger.warn("Failed to prefetch metadata for directory: {}", directory, e);
                }
            });
        }

        private static class PrefetchCache {
            private volatile long lastPrefetchTime = 0;

            public void updateLastPrefetch() {
                this.lastPrefetchTime = System.currentTimeMillis();
            }

            public long getLastPrefetchTime() {
                return lastPrefetchTime;
            }
        }
    }
}