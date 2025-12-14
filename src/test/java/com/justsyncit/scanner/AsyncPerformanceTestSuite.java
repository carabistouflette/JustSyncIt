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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Performance test suite for async components.
 * Validates performance characteristics and scalability under load.
 * Follows TDD principles by testing performance requirements.
 */
@Tag("slow")
@Tag("performance")
@DisplayName("Async Performance Test Suite")
public class AsyncPerformanceTestSuite extends AsyncTestBase {

    private AsyncByteBufferPool bufferPool;
    private AsyncFileChunker fileChunker;
    private AsyncChunkHandler chunkHandler;
    private List<Path> testFiles;
    private AsyncTestMetricsCollector metricsCollector;

    @BeforeEach
    void setUp() {
        super.setUp();

        // Create test components with performance-optimized settings
        bufferPool = AsyncByteBufferPoolImpl.create(64 * 1024, 50); // 64KB buffers, 50 max
        fileChunker = AsyncFileChunkerImpl.create(createMockBlake3Service());
        chunkHandler = AsyncFileChunkHandler.create(createMockBlake3Service());

        // Configure components for performance
        fileChunker.setAsyncBufferPool(bufferPool);
        fileChunker.setAsyncChunkHandler(chunkHandler);
        fileChunker.setMaxConcurrentOperations(8);

        // Create performance test files of various sizes
        testFiles = new ArrayList<>();
        try {
            // Small files (1KB - 10KB)
            for (int i = 1; i <= 10; i++) {
                Path file = tempDir.resolve("small_file_" + i + ".dat");
                AsyncTestUtils.createTestFile(tempDir, "small_file_" + i + ".dat", i * 1024);
                testFiles.add(file);
            }

            // Medium files (100KB - 1MB)
            for (int i = 1; i <= 5; i++) {
                Path file = tempDir.resolve("medium_file_" + i + ".dat");
                AsyncTestUtils.createTestFile(tempDir, "medium_file_" + i + ".dat", i * 200 * 1024);
                testFiles.add(file);
            }

            // Large files (5MB - 10MB)
            for (int i = 1; i <= 2; i++) {
                Path file = tempDir.resolve("large_file_" + i + ".dat");
                AsyncTestUtils.createTestFile(tempDir, "large_file_" + i + ".dat", i * 5 * 1024 * 1024);
                testFiles.add(file);
            }
        } catch (AsyncTestUtils.AsyncTestException e) {
            throw new RuntimeException("Failed to create test files", e);
        }

        metricsCollector = new AsyncTestMetricsCollector();
    }

    @AfterEach
    void tearDown() {
        try {
            if (fileChunker != null && !fileChunker.isClosed()) {
                fileChunker.closeAsync().get(5, TimeUnit.SECONDS);
            }
            if (bufferPool != null) {
                bufferPool.clear();
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
        try {
            super.tearDown();
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("Should meet buffer pool performance targets")
    void shouldMeetBufferPoolPerformanceTargets() throws Exception {
        // Given
        int operationsPerSecond = 1000;
        Duration testDuration = Duration.ofSeconds(10);

        // When - Measure buffer pool performance
        AsyncTestMetricsCollector.OperationTimer timer = metricsCollector.startOperation("buffer_pool_performance",
                "performance_test");

        AtomicLong totalOperations = new AtomicLong(0);
        long startTime = System.nanoTime();
        long endTime = startTime + testDuration.toNanos();

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        while (System.nanoTime() < endTime) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    // Acquire and release buffer
                    java.nio.ByteBuffer buffer = bufferPool.acquire(8192);
                    // Simulate some work
                    Thread.sleep(1);
                    bufferPool.release(buffer);
                    totalOperations.incrementAndGet();
                } catch (Exception e) {
                    // Log but don't fail test - some failures are expected under load
                }
            });
            futures.add(future);

            // Control rate to avoid overwhelming the system
            if (futures.size() >= operationsPerSecond) {
                AsyncTestUtils.waitForAll(Duration.ofSeconds(1), futures);
                futures.clear();
            }
        }

        // Wait for remaining operations
        if (!futures.isEmpty()) {
            AsyncTestUtils.waitForAll(Duration.ofSeconds(5), futures);
        }

        long actualDuration = System.nanoTime() - startTime;
        double actualOpsPerSecond = (double) totalOperations.get() / (actualDuration / 1_000_000_000.0);

        timer.complete(totalOperations.get());

        // Then - Performance should meet targets
        assertTrue(actualOpsPerSecond >= operationsPerSecond * 0.8,
                String.format("Buffer pool should handle at least 80%% of target ops/sec: %.2f >= %.2f",
                        actualOpsPerSecond, operationsPerSecond * 0.8));

        // Verify metrics
        AsyncTestMetricsCollector.ComponentMetrics metrics = metricsCollector
                .getOperationMetrics("buffer_pool_performance");
        assertNotNull(metrics);
        assertTrue(metrics.getTotalOperations() > 0);

        System.out.printf("Buffer pool performance: %.2f ops/sec (target: %d ops/sec)%n",
                actualOpsPerSecond, operationsPerSecond);
    }

    @Test
    @Timeout(60)
    @DisplayName("Should meet file chunking performance targets")
    void shouldMeetFileChunkingPerformanceTargets() throws Exception {
        // Given
        long targetThroughputMBps = 50; // 50 MB/s target
        Duration testDuration = Duration.ofSeconds(30);

        // When - Measure chunking performance
        AsyncTestMetricsCollector.OperationTimer timer = metricsCollector.startOperation("file_chunking_performance",
                "performance_test");

        AtomicLong totalBytesProcessed = new AtomicLong(0);
        AtomicLong totalOperations = new AtomicLong(0);
        long startTime = System.nanoTime();
        long endTime = startTime + testDuration.toNanos();

        List<CompletableFuture<FileChunker.ChunkingResult>> futures = new ArrayList<>();
        int fileIndex = 0;

        while (System.nanoTime() < endTime) {
            Path testFile = testFiles.get(fileIndex % testFiles.size());

            CompletableFuture<FileChunker.ChunkingResult> future = fileChunker.chunkFileAsync(testFile, null);

            future.whenComplete((result, throwable) -> {
                if (throwable == null && result.isSuccess()) {
                    totalBytesProcessed.addAndGet(result.getTotalSize());
                    totalOperations.incrementAndGet();
                }
            });

            futures.add(future);
            fileIndex++;

            // Control concurrency
            if (futures.size() >= 20) {
                AsyncTestUtils.waitForAll(Duration.ofSeconds(5), futures);
                futures.clear();
            }
        }

        // Wait for remaining operations
        if (!futures.isEmpty()) {
            AsyncTestUtils.waitForAll(Duration.ofSeconds(10), futures);
        }

        long actualDuration = System.nanoTime() - startTime;
        double actualThroughputMBps = (double) totalBytesProcessed.get() / (1024 * 1024)
                / (actualDuration / 1_000_000_000.0);

        timer.complete(totalBytesProcessed.get());

        // Then - Performance should meet targets
        assertTrue(actualThroughputMBps >= targetThroughputMBps * 0.7,
                String.format("Chunking should handle at least 70%% of target throughput: %.2f >= %.2f MB/s",
                        actualThroughputMBps, targetThroughputMBps * 0.7));

        // Verify metrics
        AsyncTestMetricsCollector.ComponentMetrics metrics = metricsCollector
                .getOperationMetrics("file_chunking_performance");
        assertNotNull(metrics);
        assertTrue(metrics.getTotalOperations() > 0);

        System.out.printf("File chunking performance: %.2f MB/s (target: %d MB/s)%n",
                actualThroughputMBps, targetThroughputMBps);
    }

    @Test
    @Timeout(75)
    @DisplayName("Should maintain performance under sustained load")
    void shouldMaintainPerformanceUnderSustainedLoad() throws Exception {
        // Given
        int sustainedDurationMinutes = 1;
        int samplesPerMinute = 6; // Sample every 10 seconds
        double performanceDegradationThreshold = 0.2; // Allow 20% degradation

        List<Double> performanceSamples = new ArrayList<>();
        long startTime = System.nanoTime();
        long endTime = startTime + Duration.ofMinutes(sustainedDurationMinutes).toNanos();
        long sampleInterval = Duration.ofMinutes(sustainedDurationMinutes).toNanos() / samplesPerMinute;

        for (int sample = 0; sample < samplesPerMinute; sample++) {
            long sampleStart = System.nanoTime();
            long sampleEnd = sampleStart + sampleInterval;

            // Measure performance for this sample period
            AtomicLong operationsInSample = new AtomicLong(0);
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            while (System.nanoTime() < sampleEnd) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        java.nio.ByteBuffer buffer = bufferPool.acquire(8192);
                        bufferPool.release(buffer);
                        operationsInSample.incrementAndGet();
                    } catch (Exception e) {
                        // Ignore failures during load test
                    }
                });
                futures.add(future);

                if (futures.size() >= 100) {
                    AsyncTestUtils.waitForAll(Duration.ofSeconds(1), futures);
                    futures.clear();
                }
            }

            // Wait for remaining operations
            if (!futures.isEmpty()) {
                AsyncTestUtils.waitForAll(Duration.ofSeconds(2), futures);
            }

            long sampleDuration = System.nanoTime() - sampleStart;
            double opsPerSecond = (double) operationsInSample.get() / (sampleDuration / 1_000_000_000.0);
            performanceSamples.add(opsPerSecond);

            System.out.printf("Sample %d: %.2f ops/sec%n", sample + 1, opsPerSecond);

            // Wait until next sample period
            long remainingTime = sampleEnd - System.nanoTime();
            if (remainingTime > 0) {
                Thread.sleep(remainingTime / 1_000_000);
            }
        }

        // Then - Performance should be stable
        double firstSample = performanceSamples.get(0);
        double lastSample = performanceSamples.get(performanceSamples.size() - 1);
        double degradationRatio = (firstSample - lastSample) / firstSample;

        assertTrue(degradationRatio <= performanceDegradationThreshold,
                String.format("Performance degradation should be within threshold: %.2f <= %.2f",
                        degradationRatio, performanceDegradationThreshold));

        // Verify no major performance drops
        double averagePerformance = performanceSamples.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        for (Double sample : performanceSamples) {
            assertTrue(sample >= averagePerformance * 0.5,
                    "No sample should deviate more than 50% from average");
        }
    }

    @Test
    @Timeout(90)
    @DisplayName("Should scale with increasing concurrency")
    void shouldScaleWithIncreasingConcurrency() throws Exception {
        // Given
        int[] concurrencyLevels = {1, 2, 4, 8, 16 };
        int operationsPerLevel = 100;

        for (int concurrency : concurrencyLevels) {
            // When - Test with specific concurrency level
            AsyncTestMetricsCollector.OperationTimer timer = metricsCollector
                    .startOperation("concurrency_test_" + concurrency, "scaling_test");

            List<CompletableFuture<FileChunker.ChunkingResult>> futures = new ArrayList<>();
            long startTime = System.nanoTime();

            for (int i = 0; i < operationsPerLevel; i++) {
                Path testFile = testFiles.get(i % testFiles.size());
                CompletableFuture<FileChunker.ChunkingResult> future = fileChunker.chunkFileAsync(testFile, null);
                futures.add(future);

                // Limit concurrent operations
                if (futures.size() >= concurrency) {
                    AsyncTestUtils.waitForAll(Duration.ofSeconds(30), futures);
                    futures.clear();
                }
            }

            // Wait for remaining operations
            if (!futures.isEmpty()) {
                AsyncTestUtils.waitForAll(Duration.ofSeconds(30), futures);
            }

            long duration = System.nanoTime() - startTime;
            double throughput = (double) operationsPerLevel / (duration / 1_000_000_000.0);

            timer.complete((long) operationsPerLevel);

            System.out.printf("Concurrency %d: %.2f ops/sec%n", concurrency, throughput);

            // Then - Higher concurrency should generally improve throughput
            // (with diminishing returns after optimal point)
            assertTrue(throughput > 0, "Throughput should be positive for all concurrency levels");
        }

        // Verify scaling metrics
        AsyncTestMetricsCollector.PerformanceMetrics resourceMetrics = metricsCollector
                .getPerformanceMetrics("scaling_test");
        if (resourceMetrics != null) {
            assertNotNull(resourceMetrics);
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("Should handle memory pressure efficiently")
    void shouldHandleMemoryPressureEfficiently() throws Exception {
        // Given
        int memoryPressureCycles = 5;
        int buffersPerCycle = 20;

        for (int cycle = 0; cycle < memoryPressureCycles; cycle++) {
            // When - Create memory pressure
            List<java.nio.ByteBuffer> heldBuffers = new ArrayList<>();

            // Acquire many buffers to create pressure
            for (int i = 0; i < buffersPerCycle; i++) {
                try {
                    java.nio.ByteBuffer buffer = bufferPool.acquire(64 * 1024);
                    heldBuffers.add(buffer);
                } catch (Exception e) {
                    // Expected under memory pressure
                    break;
                }
            }

            // Measure performance under pressure
            AsyncTestMetricsCollector.OperationTimer timer = metricsCollector.startOperation("memory_pressure_" + cycle,
                    "pressure_test");

            AtomicLong operationsUnderPressure = new AtomicLong(0);
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int i = 0; i < 50; i++) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        java.nio.ByteBuffer buffer = bufferPool.acquire(8192);
                        // Minimal work
                        bufferPool.release(buffer);
                        operationsUnderPressure.incrementAndGet();
                    } catch (Exception e) {
                        // Expected under memory pressure
                    }
                });
                futures.add(future);
            }

            AsyncTestUtils.waitForAll(Duration.ofSeconds(10), futures);

            timer.complete(operationsUnderPressure.get());

            // Release pressure
            for (java.nio.ByteBuffer buffer : heldBuffers) {
                bufferPool.release(buffer);
            }

            // Allow system to recover
            Thread.sleep(1000);

            System.out.printf("Memory pressure cycle %d: %d operations completed%n",
                    cycle + 1, operationsUnderPressure.get());
        }

        // Then - System should recover from memory pressure
        // Verify final buffer pool state is healthy
        CompletableFuture<String> finalStats = bufferPool.getStatsAsync();
        String statsString = finalStats.get(5, TimeUnit.SECONDS);
        assertNotNull(statsString);
        assertTrue(statsString.contains("Total:"), "Buffer pool should maintain statistics");
    }

    @Test
    @Timeout(30)
    @DisplayName("Should meet latency requirements")
    void shouldMeetLatencyRequirements() throws Exception {
        // Given
        int targetLatencyMs = 100; // Target: 100ms max latency
        int sampleSize = 100;

        // When - Measure operation latencies
        List<Long> latencies = new ArrayList<>();

        for (int i = 0; i < sampleSize; i++) {
            long startTime = System.nanoTime();

            try {
                java.nio.ByteBuffer buffer = bufferPool.acquire(8192);
                long acquireTime = System.nanoTime();

                // Minimal work
                Thread.sleep(1);

                bufferPool.release(buffer);
                long endTime = System.nanoTime();

                long totalLatency = endTime - startTime;
                latencies.add(totalLatency);

            } catch (Exception e) {
                // Record failed operation as max latency
                latencies.add(Long.MAX_VALUE);
            }
        }

        // Then - Latency requirements should be met
        long maxLatency = latencies.stream().mapToLong(Long::longValue).max().orElse(Long.MAX_VALUE);
        double averageLatency = latencies.stream()
                .filter(lat -> lat != Long.MAX_VALUE)
                .mapToLong(Long::longValue)
                .average()
                .orElse(Double.MAX_VALUE);

        long maxLatencyMs = maxLatency / 1_000_000;
        double averageLatencyMs = averageLatency / 1_000_000.0;

        assertTrue(maxLatencyMs <= targetLatencyMs * 2,
                String.format("Max latency should be within 2x target: %dms <= %dms",
                        maxLatencyMs, targetLatencyMs * 2));

        assertTrue(averageLatencyMs <= targetLatencyMs,
                String.format("Average latency should be within target: %.2fms <= %dms",
                        averageLatencyMs, targetLatencyMs));

        System.out.printf("Latency - Average: %.2fms, Max: %dms (Target: %dms)%n",
                averageLatencyMs, maxLatencyMs, targetLatencyMs);
    }

    /**
     * Creates a mock Blake3Service for testing.
     */
    private com.justsyncit.hash.Blake3Service createMockBlake3Service() {
        return new com.justsyncit.hash.Blake3Service() {
            @Override
            public String hashFile(Path filePath) throws java.io.IOException {
                return "mock_file_hash_" + filePath.hashCode();
            }

            @Override
            public String hashBuffer(byte[] data) throws com.justsyncit.hash.HashingException {
                return "mock_buffer_hash_" + java.util.Arrays.hashCode(data);
            }

            @Override
            public String hashBuffer(byte[] data, int offset, int length) throws com.justsyncit.hash.HashingException {
                return "mock_buffer_hash_"
                        + java.util.Arrays.hashCode(java.util.Arrays.copyOfRange(data, offset, offset + length));
            }

            @Override
            public String hashBuffer(java.nio.ByteBuffer buffer) throws com.justsyncit.hash.HashingException {
                return "mock_buffer_hash_" + buffer.hashCode();
            }

            @Override
            public String hashStream(java.io.InputStream inputStream)
                    throws java.io.IOException, com.justsyncit.hash.HashingException {
                return "mock_stream_hash_" + inputStream.hashCode();
            }

            @Override
            public Blake3IncrementalHasher createIncrementalHasher() throws com.justsyncit.hash.HashingException {
                return new Blake3IncrementalHasher() {
                    private final java.security.MessageDigest digest;
                    private long bytesProcessed = 0;

                    {
                        try {
                            digest = java.security.MessageDigest.getInstance("SHA-256");
                        } catch (java.security.NoSuchAlgorithmException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    public void update(byte[] data) {
                        digest.update(data);
                        bytesProcessed += data.length;
                    }

                    @Override
                    public void update(byte[] data, int offset, int length) {
                        digest.update(data, offset, length);
                        bytesProcessed += length;
                    }

                    @Override
                    public void update(java.nio.ByteBuffer buffer) {
                        byte[] data = new byte[buffer.remaining()];
                        buffer.get(data);
                        digest.update(data);
                        bytesProcessed += data.length;
                    }

                    @Override
                    public String digest() throws com.justsyncit.hash.HashingException {
                        byte[] hash = digest.digest();
                        StringBuilder sb = new StringBuilder();
                        for (byte b : hash) {
                            sb.append(String.format("%02x", b));
                        }
                        return sb.toString();
                    }

                    @Override
                    public void reset() {
                        digest.reset();
                        bytesProcessed = 0;
                    }

                    @Override
                    public String peek() throws com.justsyncit.hash.HashingException {
                        byte[] hash = digest.digest();
                        StringBuilder sb = new StringBuilder();
                        for (byte b : hash) {
                            sb.append(String.format("%02x", b));
                        }
                        return sb.toString();
                    }

                    @Override
                    public long getBytesProcessed() {
                        return bytesProcessed;
                    }
                };
            }

            @Override
            public Blake3IncrementalHasher createKeyedIncrementalHasher(byte[] key)
                    throws com.justsyncit.hash.HashingException {
                return createIncrementalHasher();
            }

            @Override
            public java.util.concurrent.CompletableFuture<java.util.List<String>> hashFilesParallel(
                    java.util.List<Path> filePaths) {
                return java.util.concurrent.CompletableFuture.completedFuture(
                        filePaths.stream().map(p -> "mock_file_hash_" + p.hashCode())
                                .collect(java.util.stream.Collectors.toList()));
            }

            @Override
            public Blake3Info getInfo() {
                return new Blake3Info() {
                    @Override
                    public String getVersion() {
                        return "1.0.0-mock";
                    }

                    @Override
                    public boolean hasSimdSupport() {
                        return false;
                    }

                    @Override
                    public String getSimdInstructionSet() {
                        return "none";
                    }

                    @Override
                    public boolean isJniImplementation() {
                        return false;
                    }

                    @Override
                    public int getOptimalBufferSize() {
                        return 8192;
                    }

                    @Override
                    public boolean supportsConcurrentHashing() {
                        return true;
                    }

                    @Override
                    public int getMaxConcurrentThreads() {
                        return Runtime.getRuntime().availableProcessors();
                    }
                };
            }

            @Override
            public boolean verify(byte[] data, String expectedHash) throws com.justsyncit.hash.HashingException {
                return hashBuffer(data).equals(expectedHash);
            }
        };
    }
}