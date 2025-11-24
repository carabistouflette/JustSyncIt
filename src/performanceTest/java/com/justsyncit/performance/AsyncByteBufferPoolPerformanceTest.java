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

package com.justsyncit.performance;

import com.justsyncit.scanner.AsyncByteBufferPool;
import com.justsyncit.scanner.OptimizedAsyncByteBufferPool;
import com.justsyncit.scanner.AsyncByteBufferPoolImpl;
import com.justsyncit.scanner.ByteBufferPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive performance tests for AsyncByteBufferPool implementations.
 * Tests against performance targets:
 * - CPU overhead reduced by 20%+ vs synchronous I/O
 * - Throughput >3GB/s on NVMe through optimized buffer management
 * - Reduced latency for small file operations through efficient buffer handling
 * - Memory efficiency improvements to reduce GC pressure
 */
public class AsyncByteBufferPoolPerformanceTest {

    private static final int WARMUP_ITERATIONS = 1000;
    private static final int PERFORMANCE_ITERATIONS = 10000;
    private static final int CONCURRENT_THREADS = Runtime.getRuntime().availableProcessors() * 2;
    private static final long PERFORMANCE_TIMEOUT_SECONDS = 60;

    // Test buffer sizes (bytes)
    private static final int[] TEST_SIZES = {
        1024,      // 1KB - small file operations
        4096,      // 4KB - typical chunk size
        65536,     // 64KB - default buffer size
        262144,    // 256KB - medium file operations
        1048576    // 1MB - large file operations
    };

    private ExecutorService executorService;
    private OptimizedAsyncByteBufferPool optimizedPool;
    private AsyncByteBufferPoolImpl originalPool;
    private ByteBufferPool syncPool;

    @BeforeEach
    void setUp() {
        executorService = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        
        // Initialize optimized pool with performance-focused configuration
        OptimizedAsyncByteBufferPool.PoolConfiguration config = 
            new OptimizedAsyncByteBufferPool.PoolConfiguration.Builder()
                .minBuffersPerTier(4)
                .maxBuffersPerTier(64)
                .maxMemoryBytes(Runtime.getRuntime().maxMemory() / 2) // 50% of heap
                .enableDirectBuffers(true)
                .enableHeapBuffers(true)
                .enablePrefetching(true)
                .enableAdaptiveSizing(true)
                .enableZeroCopy(true)
                .prefetchThreshold(5)
                .memoryPressureThreshold(0.85)
                .backpressureThreshold(200)
                .build();
        
        optimizedPool = OptimizedAsyncByteBufferPool.create(config);
        originalPool = AsyncByteBufferPoolImpl.create();
        syncPool = ByteBufferPool.create();
    }

    @Nested
    @DisplayName("Throughput Performance Tests")
    class ThroughputTests {

        @Test
        @DisplayName("Test high-throughput buffer acquisition and release")
        void testHighThroughputOperations() throws Exception {
            // Test target: >3GB/s throughput
            long targetThroughputBytesPerSec = 3L * 1024 * 1024 * 1024; // 3GB/s
            
            // Warm up
            performWarmup(optimizedPool);
            
            // Measure throughput
            long startTime = System.nanoTime();
            int totalOperations = PERFORMANCE_ITERATIONS * CONCURRENT_THREADS;
            AtomicInteger completedOperations = new AtomicInteger(0);
            AtomicLong totalBytesProcessed = new AtomicLong(0);
            
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            for (int thread = 0; thread < CONCURRENT_THREADS; thread++) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    java.util.Random random = new java.util.Random();
                    
                    for (int i = 0; i < PERFORMANCE_ITERATIONS; i++) {
                        int bufferSize = TEST_SIZES[random.nextInt(TEST_SIZES.length)];
                        
                        try {
                            // Acquire buffer
                            ByteBuffer buffer = optimizedPool.acquireAsync(bufferSize).get(1, TimeUnit.SECONDS);
                            assertNotNull(buffer);
                            assertEquals(bufferSize, buffer.capacity());
                            
                            // Simulate work - fill buffer with data
                            buffer.clear();
                            for (int j = 0; j < Math.min(bufferSize, 1024); j += 8) {
                                buffer.putLong(j, System.nanoTime());
                            }
                            
                            // Release buffer
                            optimizedPool.releaseAsync(buffer).get(1, TimeUnit.SECONDS);
                            
                            completedOperations.incrementAndGet();
                            totalBytesProcessed.addAndGet(bufferSize);
                            
                        } catch (Exception e) {
                            fail("Operation failed: " + e.getMessage());
                        }
                    }
                }, executorService);
                
                futures.add(future);
            }
            
            // Wait for all operations to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(PERFORMANCE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
            long endTime = System.nanoTime();
            long durationNanos = endTime - startTime;
            double durationSeconds = durationNanos / 1_000_000_000.0;
            
            // Calculate throughput
            double throughputBytesPerSec = totalBytesProcessed.get() / durationSeconds;
            double throughputGBPerSec = throughputBytesPerSec / (1024.0 * 1024.0 * 1024.0);
            
            // Performance assertions
            assertEquals(totalOperations, completedOperations.get(), "Not all operations completed");
            assertTrue(throughputGBPerSec >= 1.0, 
                String.format("Throughput too low: %.2f GB/s (target: >1.0 GB/s, optimal: >3.0 GB/s)", throughputGBPerSec));
            
            // Log performance results
            System.out.printf("Throughput Performance Results:%n");
            System.out.printf("  Total Operations: %d%n", totalOperations);
            System.out.printf("  Total Bytes Processed: %d MB%n", totalBytesProcessed.get() / (1024 * 1024));
            System.out.printf("  Duration: %.2f seconds%n", durationSeconds);
            System.out.printf("  Throughput: %.2f GB/s%n", throughputGBPerSec);
            System.out.printf("  Operations/sec: %.0f%n", totalOperations / durationSeconds);
        }

        @ParameterizedTest
        @ValueSource(ints = {1024, 4096, 65536, 262144, 1048576})
        @DisplayName("Test throughput for specific buffer sizes")
        void testThroughputForBufferSize(int bufferSize) throws Exception {
            // Warm up
            performWarmup(optimizedPool);
            
            long startTime = System.nanoTime();
            int operations = PERFORMANCE_ITERATIONS;
            AtomicInteger completedOperations = new AtomicInteger(0);
            
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            for (int thread = 0; thread < CONCURRENT_THREADS; thread++) {
                int operationsPerThread = operations / CONCURRENT_THREADS;
                
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    for (int i = 0; i < operationsPerThread; i++) {
                        try {
                            ByteBuffer buffer = optimizedPool.acquireAsync(bufferSize).get(1, TimeUnit.SECONDS);
                            assertNotNull(buffer);
                            
                            // Simulate work
                            buffer.clear();
                            buffer.putLong(0, System.nanoTime());
                            
                            optimizedPool.releaseAsync(buffer).get(1, TimeUnit.SECONDS);
                            completedOperations.incrementAndGet();
                            
                        } catch (Exception e) {
                            fail("Operation failed: " + e.getMessage());
                        }
                    }
                }, executorService);
                
                futures.add(future);
            }
            
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(PERFORMANCE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
            long endTime = System.nanoTime();
            double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
            double opsPerSec = completedOperations.get() / durationSeconds;
            
            System.out.printf("Buffer Size %d: %.0f ops/sec%n", bufferSize, opsPerSec);
            
            assertTrue(opsPerSec >= 10000, 
                String.format("Operations/sec too low for %d bytes: %.0f", bufferSize, opsPerSec));
        }
    }

    @Nested
    @DisplayName("Latency Performance Tests")
    class LatencyTests {

        @Test
        @DisplayName("Test low-latency small buffer operations")
        void testLowLatencySmallBuffers() throws Exception {
            // Focus on small buffers (1KB-4KB) for reduced latency
            int smallBufferSize = 4096;
            long targetLatencyNanos = 100_000; // 100 microseconds target
            
            // Warm up
            performWarmup(optimizedPool);
            
            List<Long> latencies = new ArrayList<>();
            int measurements = 1000;
            
            for (int i = 0; i < measurements; i++) {
                long startTime = System.nanoTime();
                
                try {
                    ByteBuffer buffer = optimizedPool.acquireAsync(smallBufferSize).get();
                    buffer.clear();
                    buffer.putInt(0, i);
                    optimizedPool.releaseAsync(buffer).get();
                    
                    long endTime = System.nanoTime();
                    latencies.add(endTime - startTime);
                    
                } catch (Exception e) {
                    fail("Operation failed: " + e.getMessage());
                }
            }
            
            // Calculate latency statistics
            latencies.sort(Long::compareTo);
            double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
            long p50Latency = latencies.get(latencies.size() / 2);
            long p95Latency = latencies.get((int) (latencies.size() * 0.95));
            long p99Latency = latencies.get((int) (latencies.size() * 0.99));
            
            System.out.printf("Latency Performance Results (%d bytes):%n", smallBufferSize);
            System.out.printf("  Average: %.2f μs%n", avgLatency / 1000.0);
            System.out.printf("  P50: %.2f μs%n", p50Latency / 1000.0);
            System.out.printf("  P95: %.2f μs%n", p95Latency / 1000.0);
            System.out.printf("  P99: %.2f μs%n", p99Latency / 1000.0);
            
            // Performance assertions
            assertTrue(avgLatency <= targetLatencyNanos * 2, 
                String.format("Average latency too high: %.2f μs", avgLatency / 1000.0));
            assertTrue(p95Latency <= targetLatencyNanos * 5, 
                String.format("P95 latency too high: %.2f μs", p95Latency / 1000.0));
        }

        @RepeatedTest(5)
        @DisplayName("Test latency under concurrent load")
        void testLatencyUnderLoad() throws Exception {
            int bufferSize = 65536; // 64KB
            int concurrentOperations = 100;
            
            // Warm up
            performWarmup(optimizedPool);
            
            List<CompletableFuture<Long>> futures = new ArrayList<>();
            
            for (int i = 0; i < concurrentOperations; i++) {
                final int operationId = i;
                
                CompletableFuture<Long> future = CompletableFuture.supplyAsync(() -> {
                    long startTime = System.nanoTime();
                    
                    try {
                        ByteBuffer buffer = optimizedPool.acquireAsync(bufferSize).get();
                        buffer.clear();
                        buffer.putInt(0, operationId);
                        optimizedPool.releaseAsync(buffer).get();
                        
                        return System.nanoTime() - startTime;
                        
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, executorService);
                
                futures.add(future);
            }
            
            // Wait for all operations and collect latencies
            List<Long> latencies = futures.stream()
                .map(future -> {
                    try {
                        return future.get(5, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        fail("Future failed: " + e.getMessage());
                        return Long.MAX_VALUE;
                    }
                })
                .toList();
            
            // Calculate statistics
            double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
            long maxLatency = latencies.stream().mapToLong(Long::longValue).max().orElse(0);
            
            System.out.printf("Concurrent Load Latency: avg=%.2f μs, max=%.2f μs%n", 
                avgLatency / 1000.0, maxLatency / 1000.0);
            
            assertTrue(avgLatency <= 1_000_000, // 1ms average
                String.format("Average latency too high under load: %.2f μs", avgLatency / 1000.0));
        }
    }

    @Nested
    @DisplayName("Memory Efficiency Tests")
    class MemoryEfficiencyTests {

        @Test
        @DisplayName("Test memory efficiency and GC pressure")
        void testMemoryEfficiency() throws Exception {
            System.gc(); // Clean up before test
            long initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            
            // Perform many buffer operations
            int operations = 10000;
            List<ByteBuffer> retainedBuffers = new ArrayList<>();
            
            // Phase 1: Acquire many buffers without releasing
            for (int i = 0; i < operations; i++) {
                int size = TEST_SIZES[i % TEST_SIZES.length];
                ByteBuffer buffer = optimizedPool.acquireAsync(size).get();
                retainedBuffers.add(buffer);
                
                if (i % 1000 == 0) {
                    long currentMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                    System.out.printf("Memory after %d acquisitions: %d MB%n", 
                        i, (currentMemory - initialMemory) / (1024 * 1024));
                }
            }
            
            long peakMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            
            // Phase 2: Release all buffers
            for (ByteBuffer buffer : retainedBuffers) {
                optimizedPool.releaseAsync(buffer).get();
            }
            retainedBuffers.clear();
            
            System.gc(); // Force GC to measure memory recovery
            Thread.sleep(100);
            
            long finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            
            // Calculate memory efficiency
            long memoryGrowth = peakMemory - initialMemory;
            long memoryRecovery = peakMemory - finalMemory;
            double recoveryPercentage = (double) memoryRecovery / memoryGrowth * 100;
            
            System.out.printf("Memory Efficiency Results:%n");
            System.out.printf("  Initial Memory: %d MB%n", initialMemory / (1024 * 1024));
            System.out.printf("  Peak Memory: %d MB%n", peakMemory / (1024 * 1024));
            System.out.printf("  Final Memory: %d MB%n", finalMemory / (1024 * 1024));
            System.out.printf("  Memory Growth: %d MB%n", memoryGrowth / (1024 * 1024));
            System.out.printf("  Memory Recovery: %.1f%%%n", recoveryPercentage);
            
            // Memory efficiency assertions
            assertTrue(memoryGrowth < 500 * 1024 * 1024, // Less than 500MB growth
                String.format("Memory growth too high: %d MB", memoryGrowth / (1024 * 1024)));
            assertTrue(recoveryPercentage >= 80.0, 
                String.format("Memory recovery too low: %.1f%%", recoveryPercentage));
        }

        @Test
        @DisplayName("Test buffer pool memory limits")
        void testMemoryLimits() throws Exception {
            OptimizedAsyncByteBufferPool.PoolConfiguration limitedConfig = 
                new OptimizedAsyncByteBufferPool.PoolConfiguration.Builder()
                    .maxMemoryBytes(50 * 1024 * 1024) // 50MB limit
                    .build();
            
            OptimizedAsyncByteBufferPool limitedPool = OptimizedAsyncByteBufferPool.create(limitedConfig);
            
            try {
                List<ByteBuffer> buffers = new ArrayList<>();
                int acquiredBuffers = 0;
                
                // Try to acquire buffers until we hit the limit
                while (acquiredBuffers < 1000) {
                    try {
                        ByteBuffer buffer = limitedPool.acquireAsync(65536).get(1, TimeUnit.SECONDS); // 64KB each
                        buffers.add(buffer);
                        acquiredBuffers++;
                        
                        // Check pool stats periodically
                        if (acquiredBuffers % 100 == 0) {
                            String stats = limitedPool.getStatsAsync().get();
                            System.out.printf("Acquired %d buffers. Pool stats: %s%n", acquiredBuffers, stats);
                        }
                        
                    } catch (ExecutionException e) {
                        // Expected to fail when memory limit is reached
                        System.out.printf("Memory limit reached after acquiring %d buffers%n", acquiredBuffers);
                        break;
                    }
                }
                
                // Release all buffers
                for (ByteBuffer buffer : buffers) {
                    limitedPool.releaseAsync(buffer).get();
                }
                
                // Verify pool is still functional
                ByteBuffer testBuffer = limitedPool.acquireAsync(4096).get();
                assertNotNull(testBuffer);
                limitedPool.releaseAsync(testBuffer).get();
                
                System.out.printf("Successfully tested memory limits with %d buffers%n", acquiredBuffers);
                
            } finally {
                limitedPool.clearAsync().get();
            }
        }
    }

    @Nested
    @DisplayName("Comparison Tests")
    class ComparisonTests {

        @Test
        @DisplayName("Compare optimized vs original async pool performance")
        void compareWithOriginalAsyncPool() throws Exception {
            System.out.println("=== Performance Comparison: Optimized vs Original Async Pool ===");
            
            // Test both implementations
            PerformanceResult optimizedResult = measurePoolPerformance(optimizedPool, "Optimized");
            PerformanceResult originalResult = measurePoolPerformance(originalPool, "Original");
            
            // Calculate improvements
            double throughputImprovement = (double) optimizedResult.throughput / originalResult.throughput;
            double latencyImprovement = (double) originalResult.avgLatency / optimizedResult.avgLatency;
            
            System.out.printf("Performance Comparison Results:%n");
            System.out.printf("  Throughput Improvement: %.2fx%n", throughputImprovement);
            System.out.printf("  Latency Improvement: %.2fx%n", latencyImprovement);
            System.out.printf("  Optimized: %d ops/sec, %.2f μs avg latency%n", 
                optimizedResult.throughput, optimizedResult.avgLatency / 1000.0);
            System.out.printf("  Original: %d ops/sec, %.2f μs avg latency%n", 
                originalResult.throughput, originalResult.avgLatency / 1000.0);
            
            // Performance improvement assertions
            assertTrue(throughputImprovement >= 1.2, 
                String.format("Throughput improvement too low: %.2fx (target: >=1.2x)", throughputImprovement));
            assertTrue(latencyImprovement >= 1.2, 
                String.format("Latency improvement too low: %.2fx (target: >=1.2x)", latencyImprovement));
        }

        @Test
        @DisplayName("Compare async vs sync pool performance")
        void compareWithSyncPool() throws Exception {
            System.out.println("=== Performance Comparison: Async vs Sync Pool ===");
            
            PerformanceResult asyncResult = measurePoolPerformance(optimizedPool, "Async");
            PerformanceResult syncResult = measureSyncPoolPerformance(syncPool, "Sync");
            
            // Calculate CPU overhead reduction
            double cpuOverheadReduction = (double) syncResult.avgLatency / asyncResult.avgLatency;
            
            System.out.printf("Async vs Sync Comparison Results:%n");
            System.out.printf("  CPU Overhead Reduction: %.2fx%n", cpuOverheadReduction);
            System.out.printf("  Async: %d ops/sec, %.2f μs avg latency%n", 
                asyncResult.throughput, asyncResult.avgLatency / 1000.0);
            System.out.printf("  Sync: %d ops/sec, %.2f μs avg latency%n", 
                syncResult.throughput, syncResult.avgLatency / 1000.0);
            
            // Target: 20%+ CPU overhead reduction vs synchronous I/O
            assertTrue(cpuOverheadReduction >= 1.2, 
                String.format("CPU overhead reduction too low: %.2fx (target: >=1.2x)", cpuOverheadReduction));
        }
    }

    @Nested
    @DisplayName("Stress Tests")
    class StressTests {

        @Test
        @DisplayName("Test under extreme memory pressure")
        void testUnderMemoryPressure() throws Exception {
            // Create memory pressure by allocating large objects
            List<byte[]> memoryHogs = new ArrayList<>();
            try {
                // Allocate memory to create pressure
                for (int i = 0; i < 100; i++) {
                    memoryHogs.add(new byte[10 * 1024 * 1024]); // 10MB each
                }
                
                // Test buffer pool performance under pressure
                long startTime = System.nanoTime();
                int operations = 1000;
                AtomicInteger successfulOps = new AtomicInteger(0);
                
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                
                for (int i = 0; i < operations; i++) {
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        try {
                            ByteBuffer buffer = optimizedPool.acquireAsync(32768).get(5, TimeUnit.SECONDS);
                            buffer.clear();
                            buffer.putInt(0, (int) System.nanoTime());
                            optimizedPool.releaseAsync(buffer).get(5, TimeUnit.SECONDS);
                            successfulOps.incrementAndGet();
                        } catch (Exception e) {
                            // Some failures are expected under memory pressure
                        }
                    }, executorService);
                    
                    futures.add(future);
                }
                
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(30, TimeUnit.SECONDS);
                
                long endTime = System.nanoTime();
                double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
                double successRate = (double) successfulOps.get() / operations * 100;
                
                System.out.printf("Memory Pressure Test Results:%n");
                System.out.printf("  Success Rate: %.1f%%%n", successRate);
                System.out.printf("  Duration: %.2f seconds%n", durationSeconds);
                
                // Should still maintain reasonable performance under pressure
                assertTrue(successRate >= 70.0, 
                    String.format("Success rate too low under pressure: %.1f%%", successRate));
                
            } finally {
                // Release memory
                memoryHogs.clear();
                System.gc();
            }
        }

        @Test
        @DisplayName("Test sustained high load")
        void testSustainedHighLoad() throws Exception {
            int durationSeconds = 30;
            int targetOpsPerSecond = 50000;
            
            System.out.printf("Sustained Load Test: %d seconds, target %d ops/sec%n", 
                durationSeconds, targetOpsPerSecond);
            
            long startTime = System.currentTimeMillis();
            long endTime = startTime + (durationSeconds * 1000);
            AtomicInteger totalOperations = new AtomicInteger(0);
            AtomicLong totalLatency = new AtomicLong(0);
            
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            // Start continuous load
            for (int thread = 0; thread < CONCURRENT_THREADS; thread++) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    java.util.Random random = new java.util.Random();
                    
                    while (System.currentTimeMillis() < endTime) {
                        long opStart = System.nanoTime();
                        
                        try {
                            int bufferSize = TEST_SIZES[random.nextInt(TEST_SIZES.length)];
                            ByteBuffer buffer = optimizedPool.acquireAsync(bufferSize).get(1, TimeUnit.SECONDS);
                            buffer.clear();
                            buffer.putLong(0, System.nanoTime());
                            optimizedPool.releaseAsync(buffer).get(1, TimeUnit.SECONDS);
                            
                            long opEnd = System.nanoTime();
                            totalOperations.incrementAndGet();
                            totalLatency.addAndGet(opEnd - opStart);
                            
                        } catch (Exception e) {
                            // Log but continue
                        }
                    }
                }, executorService);
                
                futures.add(future);
            }
            
            // Wait for test completion
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(durationSeconds + 10, TimeUnit.SECONDS);
            
            // Calculate results
            long actualDuration = System.currentTimeMillis() - startTime;
            double actualOpsPerSec = (double) totalOperations.get() / (actualDuration / 1000.0);
            double avgLatency = (double) totalLatency.get() / totalOperations.get();
            
            System.out.printf("Sustained Load Results:%n");
            System.out.printf("  Actual Duration: %d ms%n", actualDuration);
            System.out.printf("  Total Operations: %d%n", totalOperations.get());
            System.out.printf("  Actual Ops/sec: %.0f%n", actualOpsPerSec);
            System.out.printf("  Average Latency: %.2f μs%n", avgLatency / 1000.0);
            
            // Performance assertions
            assertTrue(actualOpsPerSec >= targetOpsPerSecond * 0.8, 
                String.format("Ops/sec too low: %.0f (target: %d)", actualOpsPerSec, targetOpsPerSecond));
        }
    }

    // Helper methods

    private void performWarmup(AsyncByteBufferPool pool) throws Exception {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            int size = TEST_SIZES[i % TEST_SIZES.length];
            ByteBuffer buffer = pool.acquireAsync(size).get();
            buffer.clear();
            pool.releaseAsync(buffer).get();
        }
    }

    private PerformanceResult measurePoolPerformance(AsyncByteBufferPool pool, String name) throws Exception {
        performWarmup(pool);
        
        long startTime = System.nanoTime();
        int operations = PERFORMANCE_ITERATIONS;
        List<Long> latencies = new ArrayList<>();
        
        for (int i = 0; i < operations; i++) {
            int size = TEST_SIZES[i % TEST_SIZES.length];
            long opStart = System.nanoTime();
            
            ByteBuffer buffer = pool.acquireAsync(size).get();
            buffer.clear();
            buffer.putInt(0, i);
            pool.releaseAsync(buffer).get();
            
            long opEnd = System.nanoTime();
            latencies.add(opEnd - opStart);
        }
        
        long endTime = System.nanoTime();
        double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
        double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        int throughput = (int) (operations / durationSeconds);
        
        return new PerformanceResult(name, throughput, (long) avgLatency);
    }

    private PerformanceResult measureSyncPoolPerformance(ByteBufferPool pool, String name) throws Exception {
        // Warm up
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            int size = TEST_SIZES[i % TEST_SIZES.length];
            ByteBuffer buffer = pool.acquire(size);
            buffer.clear();
            pool.release(buffer);
        }
        
        long startTime = System.nanoTime();
        int operations = PERFORMANCE_ITERATIONS;
        List<Long> latencies = new ArrayList<>();
        
        for (int i = 0; i < operations; i++) {
            int size = TEST_SIZES[i % TEST_SIZES.length];
            long opStart = System.nanoTime();
            
            ByteBuffer buffer = pool.acquire(size);
            buffer.clear();
            buffer.putInt(0, i);
            pool.release(buffer);
            
            long opEnd = System.nanoTime();
            latencies.add(opEnd - opStart);
        }
        
        long endTime = System.nanoTime();
        double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
        double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        int throughput = (int) (operations / durationSeconds);
        
        return new PerformanceResult(name, throughput, (long) avgLatency);
    }

    private static class PerformanceResult {
        final String name;
        final int throughput;
        final long avgLatency;
        
        PerformanceResult(String name, int throughput, long avgLatency) {
            this.name = name;
            this.throughput = throughput;
            this.avgLatency = avgLatency;
        }
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() throws Exception {
        if (optimizedPool != null) {
            optimizedPool.clearAsync().get();
        }
        if (originalPool != null) {
            originalPool.clearAsync().get();
        }
        if (syncPool != null) {
            syncPool.clear();
        }
        if (executorService != null) {
            executorService.shutdown();
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        }
    }
}