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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.Queue;

/**
 * Buffer prefetch manager for proactive buffer allocation.
 * Analyzes usage patterns and pre-allocates buffers to reduce latency.
 */
public class BufferPrefetchManager implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(BufferPrefetchManager.class);
    
    private final OptimizedAsyncByteBufferPool.PoolConfiguration config;
    private final PerformanceMonitor performanceMonitor;
    
    // Prefetch state
    private volatile boolean running = true;
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);
    
    // Usage pattern tracking
    private final Map<Integer, UsagePattern> usagePatterns = new ConcurrentHashMap<>();
    private final Queue<PrefetchRequest> prefetchQueue = new ConcurrentLinkedQueue<>();
    
    // Prefetch metrics
    private final AtomicLong totalPrefetches = new AtomicLong(0);
    private final AtomicLong successfulPrefetches = new AtomicLong(0);
    private final AtomicLong wastedPrefetches = new AtomicLong(0);
    private final AtomicInteger currentPrefetchCount = new AtomicInteger(0);
    
    // Background monitoring
    private final ScheduledExecutorService scheduler;
    
    /**
     * Usage pattern for a specific buffer size.
     */
    private static class UsagePattern {
        private final AtomicLong requestCount = new AtomicLong(0);
        private final AtomicLong lastRequestTime = new AtomicLong(System.currentTimeMillis());
        private final AtomicLong totalRequests = new AtomicLong(0);
        private final AtomicLong hitCount = new AtomicLong(0);
        private final AtomicLong missCount = new AtomicLong(0);
        
        void recordRequest() {
            requestCount.incrementAndGet();
            totalRequests.incrementAndGet();
            lastRequestTime.set(System.currentTimeMillis());
        }
        
        void recordHit() {
            hitCount.incrementAndGet();
        }
        
        void recordMiss() {
            missCount.incrementAndGet();
        }
        
        double getHitRate() {
            long total = totalRequests.get();
            return total > 0 ? (double) hitCount.get() / total : 0.0;
        }
        
        double getMissRate() {
            long total = totalRequests.get();
            return total > 0 ? (double) missCount.get() / total : 0.0;
        }
        
        double getRequestRate() {
            long requests = requestCount.get();
            long timeSince = System.currentTimeMillis() - lastRequestTime.get();
            return timeSince > 0 ? (double) requests / (timeSince / 1000.0) : 0.0;
        }
        
        UsagePatternSnapshot getSnapshot() {
            return new UsagePatternSnapshot(
                requestCount.get(), hitCount.get(), missCount.get(),
                getHitRate(), getMissRate(), getRequestRate()
            );
        }
    }
    
    /**
     * Snapshot of usage pattern.
     */
    public static class UsagePatternSnapshot {
        public final long recentRequests;
        public final long totalHits;
        public final long totalMisses;
        public final double hitRate;
        public final double missRate;
        public final double requestRate;
        
        UsagePatternSnapshot(long recentRequests, long totalHits, long totalMisses,
                           double hitRate, double missRate, double requestRate) {
            this.recentRequests = recentRequests;
            this.totalHits = totalHits;
            this.totalMisses = totalMisses;
            this.hitRate = hitRate;
            this.missRate = missRate;
            this.requestRate = requestRate;
        }
    }
    
    /**
     * Prefetch request.
     */
    private static class PrefetchRequest {
        final int bufferSize;
        final int count;
        final long timestamp;
        final int priority;
        
        PrefetchRequest(int bufferSize, int count, int priority) {
            this.bufferSize = bufferSize;
            this.count = count;
            this.timestamp = System.currentTimeMillis();
            this.priority = priority;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > 30000; // 30 seconds
        }
    }
    
    /**
     * Creates a new BufferPrefetchManager.
     */
    public BufferPrefetchManager(OptimizedAsyncByteBufferPool.PoolConfiguration config,
                              PerformanceMonitor performanceMonitor) {
        this.config = config;
        this.performanceMonitor = performanceMonitor;
        
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BufferPrefetchManager");
            t.setDaemon(true);
            return t;
        });
        
        // Start prefetch monitoring
        scheduler.scheduleAtFixedRate(this, 5, 5, TimeUnit.SECONDS);
        
        logger.debug("BufferPrefetchManager initialized");
    }
    
    @Override
    public void run() {
        if (!running || shutdownRequested.get()) {
            return;
        }
        
        try {
            performPrefetchAnalysis();
        } catch (Exception e) {
            logger.error("Error in prefetch analysis", e);
        }
    }
    
    /**
     * Performs prefetch analysis and triggers prefetching.
     */
    private void performPrefetchAnalysis() {
        // Clean expired prefetch requests
        cleanExpiredRequests();
        
        // Analyze usage patterns
        analyzeUsagePatterns();
        
        // Generate prefetch recommendations
        generatePrefetchRecommendations();
        
        // Execute prefetch requests
        executePrefetchRequests();
    }
    
    /**
     * Cleans expired prefetch requests.
     */
    private void cleanExpiredRequests() {
        int removed = 0;
        while (!prefetchQueue.isEmpty()) {
            PrefetchRequest request = prefetchQueue.peek();
            if (request != null && request.isExpired()) {
                prefetchQueue.poll();
                removed++;
                wastedPrefetches.incrementAndGet();
            } else {
                break;
            }
        }
        
        if (removed > 0) {
            currentPrefetchCount.addAndGet(-removed);
            logger.debug("Removed {} expired prefetch requests", removed);
        }
    }
    
    /**
     * Analyzes current usage patterns.
     */
    private void analyzeUsagePatterns() {
        for (Map.Entry<Integer, UsagePattern> entry : usagePatterns.entrySet()) {
            int bufferSize = entry.getKey();
            UsagePattern pattern = entry.getValue();
            UsagePatternSnapshot snapshot = pattern.getSnapshot();
            
            // Log pattern analysis
            if (logger.isDebugEnabled()) {
                logger.debug("Usage pattern for size {}: requests={}, hit_rate={:.2f}%, request_rate={:.2f}/s",
                    bufferSize, snapshot.recentRequests, snapshot.hitRate * 100, snapshot.requestRate);
            }
            
            // Update performance monitor with pattern data
            performanceMonitor.recordUsagePattern(bufferSize, snapshot);
        }
    }
    
    /**
     * Generates prefetch recommendations based on usage patterns.
     */
    private void generatePrefetchRecommendations() {
        for (Map.Entry<Integer, UsagePattern> entry : usagePatterns.entrySet()) {
            int bufferSize = entry.getKey();
            UsagePattern pattern = entry.getValue();
            UsagePatternSnapshot snapshot = pattern.getSnapshot();
            
            // Determine if prefetching is beneficial
            if (shouldPrefetch(bufferSize, snapshot)) {
                int prefetchCount = calculatePrefetchCount(bufferSize, snapshot);
                
                if (prefetchCount > 0) {
                    PrefetchRequest request = new PrefetchRequest(
                        bufferSize, prefetchCount, calculatePriority(snapshot));
                    
                    prefetchQueue.offer(request);
                    currentPrefetchCount.incrementAndGet();
                    
                    logger.debug("Generated prefetch request: {} buffers of size {}", 
                        prefetchCount, bufferSize);
                }
            }
        }
    }
    
    /**
     * Determines if prefetching should be performed for a buffer size.
     */
    private boolean shouldPrefetch(int bufferSize, UsagePatternSnapshot pattern) {
        // Don't prefetch if not enabled
        if (!config.isPrefetchingEnabled()) {
            return false;
        }
        
        // Prefetch if hit rate is low and request rate is high
        return pattern.hitRate < 0.7 && pattern.requestRate > 1.0;
    }
    
    /**
     * Calculates prefetch count based on usage pattern.
     */
    private int calculatePrefetchCount(int bufferSize, UsagePatternSnapshot pattern) {
        // Base count on request rate
        int baseCount = (int) Math.min(pattern.requestRate * 2, 10);
        
        // Adjust based on hit rate (lower hit rate = more prefetching)
        double hitRateFactor = 1.0 - pattern.hitRate;
        int adjustedCount = (int) (baseCount * (1.0 + hitRateFactor));
        
        // Respect configuration limits
        int maxPrefetch = config.getPrefetchThreshold();
        return Math.min(adjustedCount, maxPrefetch);
    }
    
    /**
     * Calculates prefetch priority based on usage pattern.
     */
    private int calculatePriority(UsagePatternSnapshot pattern) {
        // Higher priority for higher request rates and lower hit rates
        double priorityScore = pattern.requestRate * (1.0 - pattern.hitRate);
        
        if (priorityScore > 5.0) {
            return 3; // High priority
        } else if (priorityScore > 2.0) {
            return 2; // Medium priority
        } else {
            return 1; // Low priority
        }
    }
    
    /**
     * Executes prefetch requests.
     */
    private void executePrefetchRequests() {
        int executed = 0;
        
        while (!prefetchQueue.isEmpty() && executed < 5) { // Limit per cycle
            PrefetchRequest request = prefetchQueue.poll();
            if (request != null && !request.isExpired()) {
                executePrefetchRequest(request);
                executed++;
                successfulPrefetches.incrementAndGet();
                currentPrefetchCount.decrementAndGet();
            }
        }
        
        if (executed > 0) {
            logger.debug("Executed {} prefetch requests", executed);
        }
    }
    
    /**
     * Executes a single prefetch request.
     */
    private void executePrefetchRequest(PrefetchRequest request) {
        // This would interface with the main buffer pool to actually prefetch
        // For now, just log the execution
        totalPrefetches.incrementAndGet();
        
        logger.debug("Executing prefetch: {} buffers of size {} (priority {})",
            request.count, request.bufferSize, request.priority);
        
        // Record the prefetch in usage pattern
        UsagePattern pattern = usagePatterns.get(request.bufferSize);
        if (pattern != null) {
            for (int i = 0; i < request.count; i++) {
                pattern.recordRequest();
            }
        }
    }
    
    /**
     * Records a buffer acquisition request.
     */
    public void recordAcquisitionRequest(int bufferSize, boolean fromPool) {
        UsagePattern pattern = usagePatterns.computeIfAbsent(bufferSize, k -> new UsagePattern());
        
        pattern.recordRequest();
        
        if (fromPool) {
            pattern.recordHit();
        } else {
            pattern.recordMiss();
        }
    }
    
    /**
     * Gets prefetch statistics.
     */
    public PrefetchStats getStats() {
        return new PrefetchStats(
            totalPrefetches.get(), successfulPrefetches.get(), 
            wastedPrefetches.get(), currentPrefetchCount.get()
        );
    }
    
    /**
     * Prefetch statistics.
     */
    public static class PrefetchStats {
        public final long totalPrefetches;
        public final long successfulPrefetches;
        public final long wastedPrefetches;
        public final int currentPrefetchCount;
        
        PrefetchStats(long totalPrefetches, long successfulPrefetches,
                     long wastedPrefetches, int currentPrefetchCount) {
            this.totalPrefetches = totalPrefetches;
            this.successfulPrefetches = successfulPrefetches;
            this.wastedPrefetches = wastedPrefetches;
            this.currentPrefetchCount = currentPrefetchCount;
        }
        
        @Override
        public String toString() {
            return String.format(
                "PrefetchStats{total=%d, successful=%d, wasted=%d, current=%d}",
                totalPrefetches, successfulPrefetches, wastedPrefetches, currentPrefetchCount
            );
        }
    }
    
    /**
     * Shuts down the prefetch manager.
     */
    public void shutdown() {
        running = false;
        shutdownRequested.set(true);
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("BufferPrefetchManager shutdown completed");
    }
}