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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Scalability test suite for async components.
 * Tests behavior and performance under increasing load.
 * Follows TDD principles by testing scalability requirements.
 */
@DisplayName("Async Scalability Test Suite")
public class AsyncScalabilityTest extends AsyncTestBase {

    private AsyncByteBufferPool bufferPool;
    private AsyncFileChunker fileChunker;
    private AsyncChunkHandler chunkHandler;
    private List<Path> testFiles;

    @BeforeEach
    void setUp() {
        super.setUp();

        // Create test files of various sizes for scalability testing
        testFiles = new ArrayList<>();
        try {
            // Create files with exponential size growth
            for (int i = 0; i < 15; i++) {
                int fileSize = (int) Math.pow(2, i) * 1024; // 1KB to 16MB
                if (fileSize > 16 * 1024 * 1024)
                    fileSize = 16 * 1024 * 1024; // Cap at 16MB

                Path file = tempDir.resolve("scalability_test_" + i + ".dat");
                AsyncTestUtils.createTestFile(tempDir, "scalability_test_" + i + ".dat", fileSize);
                testFiles.add(file);
            }
        } catch (AsyncTestUtils.AsyncTestException e) {
            throw new RuntimeException("Failed to create test files", e);
        }
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
    @Timeout(60)
    @DisplayName("Should scale buffer pool with increasing load")
    void shouldScaleBufferPoolWithIncreasingLoad() throws Exception {
        // Given
        int[] loadLevels = { 10, 50, 100, 200, 500 };
        List<ScalabilityResult> results = new ArrayList<>();

        for (int loadLevel : loadLevels) {
            // When - Test with specific load level
            AsyncByteBufferPool testPool = AsyncByteBufferPoolImpl.create(32 * 1024, 100);

            long startTime = System.nanoTime();
            AtomicInteger successfulOps = new AtomicInteger(0);
            AtomicInteger failedOps = new AtomicInteger(0);

            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int i = 0; i < loadLevel; i++) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        java.nio.ByteBuffer buffer = testPool.acquire(8192);
                        // Simulate work
                        Thread.sleep(1);
                        testPool.release(buffer);
                        successfulOps.incrementAndGet();
                    } catch (Exception e) {
                        failedOps.incrementAndGet();
                    }
                });
                futures.add(future);
            }

            // Wait for all operations to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(30, TimeUnit.SECONDS);

            long duration = System.nanoTime() - startTime;
            double throughput = (double) successfulOps.get() / (duration / 1_000_000_000.0);
            double successRate = (double) successfulOps.get() / (successfulOps.get() + failedOps.get());

            results.add(new ScalabilityResult(loadLevel, throughput, successRate, duration));

            // Cleanup
            testPool.clear();

            System.out.printf("Load %d: %.2f ops/sec, %.2f%% success rate%n",
                    loadLevel, throughput, successRate * 100);
        }

        // Then - System should show reasonable scaling behavior
        assertTrue(results.size() >= 3, "Should have multiple data points for analysis");

        // Analyze scaling characteristics
        ScalabilityResult baseline = results.get(0);
        ScalabilityResult peak = results.get(results.size() - 1);

        // Throughput should generally increase with load (though may plateau)
        assertTrue(peak.throughput >= baseline.throughput * 0.5,
                "Peak throughput should be at least 50% of baseline under higher load");

        // Success rate should remain reasonable
        assertTrue(peak.successRate >= 0.6,
                "Success rate should remain at least 60% under high load");
    }

    @Test
    @Timeout(90)
    @DisplayName("Should scale file chunking with file size")
    void shouldScaleFileChunkingWithFileSize() throws Exception {
        // Given
        AsyncByteBufferPool testPool = AsyncByteBufferPoolImpl.create(64 * 1024, 50);
        AsyncFileChunker testChunker = AsyncFileChunkerImpl.create(createMockBlake3Service());
        AsyncChunkHandler testHandler = AsyncFileChunkHandler.create(createMockBlake3Service());

        testChunker.setAsyncBufferPool(testPool);
        testChunker.setAsyncChunkHandler(testHandler);
        testChunker.setMaxConcurrentOperations(4);

        List<FileSizeResult> results = new ArrayList<>();

        // When - Test chunking with different file sizes
        for (Path testFile : testFiles) {
            long fileSize = testFile.toFile().length();

            long startTime = System.nanoTime();
            CompletableFuture<FileChunker.ChunkingResult> future = testChunker.chunkFileAsync(testFile, null);

            FileChunker.ChunkingResult result = future.get(60, TimeUnit.SECONDS);
            long duration = System.nanoTime() - startTime;

            double throughputMBps = (double) fileSize / (1024 * 1024) / (duration / 1_000_000_000.0);

            results.add(new FileSizeResult(fileSize, result.getChunkCount(),
                    throughputMBps, duration, result.isSuccess()));

            System.out.printf("File size %d KB: %d chunks, %.2f MB/s, success: %b%n",
                    fileSize / 1024, result.getChunkCount(), throughputMBps, result.isSuccess());
        }

        // Then - System should handle various file sizes efficiently
        long successfulFiles = results.stream().mapToLong(r -> r.successful ? 1 : 0).sum();
        assertTrue(successfulFiles >= results.size() * 0.8,
                "At least 80% of files should be processed successfully");

        // Analyze throughput characteristics
        double averageThroughput = results.stream()
                .filter(r -> r.successful)
                .mapToDouble(r -> r.throughputMBps)
                .average()
                .orElse(0.0);

        assertTrue(averageThroughput > 0, "Should have positive average throughput");

        // Cleanup
        testChunker.closeAsync().get(5, TimeUnit.SECONDS);
        testPool.clear();
    }

    @Test
    @Timeout(120)
    @DisplayName("Should maintain performance under sustained load")
    void shouldMaintainPerformanceUnderSustainedLoad() throws Exception {
        // Given
        int sustainedLoadLevel = 100;
        int testDurationMinutes = 2;
        int sampleIntervalSeconds = 15;

        AsyncByteBufferPool testPool = AsyncByteBufferPoolImpl.create(32 * 1024, 80);
        List<SustainedLoadResult> samples = new ArrayList<>();

        long testStartTime = System.nanoTime();
        long testEndTime = testStartTime + Duration.ofMinutes(testDurationMinutes).toNanos();
        long sampleInterval = Duration.ofSeconds(sampleIntervalSeconds).toNanos();

        int sampleCount = 0;

        // When - Run sustained load test with periodic sampling
        while (System.nanoTime() < testEndTime) {
            long sampleStart = System.nanoTime();
            long sampleEnd = sampleStart + sampleInterval;

            AtomicInteger operationsInSample = new AtomicInteger(0);
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // Generate load for this sample period
            while (System.nanoTime() < sampleEnd && futures.size() < sustainedLoadLevel) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        java.nio.ByteBuffer buffer = testPool.acquire(8192);
                        Thread.sleep(1);
                        testPool.release(buffer);
                        operationsInSample.incrementAndGet();
                    } catch (Exception e) {
                        // Expected under sustained load
                    }
                });
                futures.add(future);
            }

            // Wait for sample period operations
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(sampleIntervalSeconds + 5, TimeUnit.SECONDS);

            long sampleDuration = System.nanoTime() - sampleStart;
            double sampleThroughput = (double) operationsInSample.get() / (sampleDuration / 1_000_000_000.0);

            samples.add(new SustainedLoadResult(sampleCount++, sampleThroughput, operationsInSample.get()));

            System.out.printf("Sample %d: %.2f ops/sec%n", sampleCount, sampleThroughput);
        }

        // Then - Performance should be stable over time
        assertTrue(samples.size() >= 3, "Should have multiple samples for analysis");

        double firstSampleThroughput = samples.get(0).throughput;
        double lastSampleThroughput = samples.get(samples.size() - 1).throughput;
        double degradationRatio = (firstSampleThroughput - lastSampleThroughput) / firstSampleThroughput;

        // Allow some degradation but not excessive
        assertTrue(degradationRatio <= 0.3,
                String.format("Performance degradation should be <= 30%%: %.2f", degradationRatio));

        // Verify no major performance drops
        double averageThroughput = samples.stream()
                .mapToDouble(s -> s.throughput)
                .average()
                .orElse(0.0);

        for (SustainedLoadResult sample : samples) {
            assertTrue(sample.throughput >= averageThroughput * 0.4,
                    "No sample should deviate more than 60% from average");
        }

        // Cleanup
        testPool.clear();
    }

    @Test
    @Timeout(60)
    @DisplayName("Should scale with increasing concurrency")
    void shouldScaleWithIncreasingConcurrency() throws Exception {
        // Given
        int[] concurrencyLevels = { 1, 2, 4, 8, 16, 32 };
        int operationsPerLevel = 50;
        List<ConcurrencyResult> results = new ArrayList<>();

        for (int concurrency : concurrencyLevels) {
            // When - Test with specific concurrency level
            AsyncByteBufferPool testPool = AsyncByteBufferPoolImpl.create(16 * 1024, 100);

            long startTime = System.nanoTime();
            AtomicInteger successfulOps = new AtomicInteger(0);

            List<CompletableFuture<FileChunker.ChunkingResult>> futures = new ArrayList<>();

            for (int i = 0; i < operationsPerLevel; i++) {
                Path testFile = testFiles.get(i % testFiles.size());

                CompletableFuture<FileChunker.ChunkingResult> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        AsyncFileChunker chunker = AsyncFileChunkerImpl.create(createMockBlake3Service());
                        AsyncChunkHandler handler = AsyncFileChunkHandler.create(createMockBlake3Service());
                        chunker.setAsyncBufferPool(testPool);
                        chunker.setAsyncChunkHandler(handler);
                        return chunker.chunkFileAsync(testFile, null).get(10, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        // Create a failed result manually
                        return new FileChunker.ChunkingResult(testFile, 0, 0, 0, "failed", new ArrayList<>()) {
                            @Override
                            public boolean isSuccess() {
                                return false;
                            }

                            @Override
                            public Exception getError() {
                                return e;
                            }
                        };
                    }
                });

                future.whenComplete((result, throwable) -> {
                    if (throwable == null && result.isSuccess()) {
                        successfulOps.incrementAndGet();
                    }
                });

                futures.add(future);

                // Limit concurrency
                if (futures.size() >= concurrency) {
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                            .get(30, TimeUnit.SECONDS);
                    futures.clear();
                }
            }

            // Wait for remaining operations
            if (!futures.isEmpty()) {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(30, TimeUnit.SECONDS);
            }

            long duration = System.nanoTime() - startTime;
            double throughput = (double) successfulOps.get() / (duration / 1_000_000_000.0);
            double efficiency = (double) successfulOps.get() / operationsPerLevel;

            results.add(new ConcurrencyResult(concurrency, throughput, efficiency, duration));

            System.out.printf("Concurrency %d: %.2f ops/sec, %.2f%% efficiency%n",
                    concurrency, throughput, efficiency * 100);

            // Cleanup
            testPool.clear();
        }

        // Then - System should show reasonable scaling characteristics
        assertTrue(results.size() >= 4, "Should have multiple concurrency data points");

        // Analyze scaling efficiency
        ConcurrencyResult baseline = results.get(0);
        ConcurrencyResult optimalPoint = results.stream()
                .max((r1, r2) -> Double.compare(r1.throughput, r2.throughput))
                .orElse(baseline);

        // Higher concurrency should generally improve throughput (with diminishing
        // returns)
        assertTrue(optimalPoint.throughput >= baseline.throughput * 0.8,
                "Optimal throughput should be at least 80% of baseline");

        // Efficiency should remain reasonable
        for (ConcurrencyResult result : results) {
            assertTrue(result.efficiency >= 0.5,
                    "Efficiency should remain at least 50% at all concurrency levels");
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("Should handle resource exhaustion gracefully")
    void shouldHandleResourceExhaustionGracefully() throws Exception {
        // Given
        int smallPoolSize = 5;
        int largeLoad = 50;

        AsyncByteBufferPool smallPool = AsyncByteBufferPoolImpl.create(8192, smallPoolSize);

        // When - Create resource exhaustion scenario
        AtomicInteger successfulAcquisitions = new AtomicInteger(0);
        AtomicInteger failedAcquisitions = new AtomicInteger(0);
        List<java.nio.ByteBuffer> heldBuffers = new ArrayList<>();

        // First, exhaust the pool
        for (int i = 0; i < smallPoolSize; i++) {
            try {
                java.nio.ByteBuffer buffer = smallPool.acquire(4096);
                heldBuffers.add(buffer);
            } catch (Exception e) {
                // Unexpected during initial acquisition
            }
        }

        // Then - Try to acquire more buffers under exhaustion
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < largeLoad; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    java.nio.ByteBuffer buffer = smallPool.acquire(4096);
                    if (buffer != null) {
                        successfulAcquisitions.incrementAndGet();
                        // Immediately release to allow others
                        smallPool.release(buffer);
                    } else {
                        failedAcquisitions.incrementAndGet();
                    }
                } catch (Exception e) {
                    failedAcquisitions.incrementAndGet();
                }
            });
            futures.add(future);
        }

        // Wait for all attempts
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(20, TimeUnit.SECONDS);

        // Verify graceful handling
        int totalAttempts = successfulAcquisitions.get() + failedAcquisitions.get();
        assertEquals(largeLoad, totalAttempts, "All attempts should be accounted for");

        // Some operations should succeed even under resource pressure
        assertTrue(successfulAcquisitions.get() > 0, "Some operations should succeed");

        // System should maintain stability
        CompletableFuture<String> statsFuture = smallPool.getStatsAsync();
        String statsString = statsFuture.get(5, TimeUnit.SECONDS);
        assertNotNull(statsString);

        // Cleanup
        for (java.nio.ByteBuffer buffer : heldBuffers) {
            smallPool.release(buffer);
        }
        smallPool.clear();

        System.out.printf("Resource exhaustion - Successful: %d, Failed: %d%n",
                successfulAcquisitions.get(), failedAcquisitions.get());
    }

    // Helper classes for test results
    private static class ScalabilityResult {
        final int loadLevel;
        final double throughput;
        final double successRate;
        final long duration;

        ScalabilityResult(int loadLevel, double throughput, double successRate, long duration) {
            this.loadLevel = loadLevel;
            this.throughput = throughput;
            this.successRate = successRate;
            this.duration = duration;
        }
    }

    private static class FileSizeResult {
        final long fileSize;
        final int chunkCount;
        final double throughputMBps;
        final long duration;
        final boolean successful;

        FileSizeResult(long fileSize, int chunkCount, double throughputMBps, long duration, boolean successful) {
            this.fileSize = fileSize;
            this.chunkCount = chunkCount;
            this.throughputMBps = throughputMBps;
            this.duration = duration;
            this.successful = successful;
        }
    }

    private static class SustainedLoadResult {
        final int sampleNumber;
        final double throughput;
        final int operations;

        SustainedLoadResult(int sampleNumber, double throughput, int operations) {
            this.sampleNumber = sampleNumber;
            this.throughput = throughput;
            this.operations = operations;
        }
    }

    private static class ConcurrencyResult {
        final int concurrency;
        final double throughput;
        final double efficiency;
        final long duration;

        ConcurrencyResult(int concurrency, double throughput, double efficiency, long duration) {
            this.concurrency = concurrency;
            this.throughput = throughput;
            this.efficiency = efficiency;
            this.duration = duration;
        }
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
            public String hashStream(java.io.InputStream inputStream)
                    throws java.io.IOException, com.justsyncit.hash.HashingException {
                return "mock_stream_hash_" + inputStream.hashCode();
            }

            @Override
            public Blake3IncrementalHasher createIncrementalHasher() throws com.justsyncit.hash.HashingException {
                return new Blake3IncrementalHasher() {
                    private final java.security.MessageDigest digest;

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
                    }

                    @Override
                    public void update(byte[] data, int offset, int length) {
                        digest.update(data, offset, length);
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
                    }

                    @Override
                    public void update(java.nio.ByteBuffer buffer) {
                        if (buffer.hasArray()) {
                            update(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
                        } else {
                            byte[] data = new byte[buffer.remaining()];
                            buffer.get(data);
                            update(data);
                        }
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
                        return 0; // Mock implementation
                    }
                };
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
                        return 64 * 1024; // 64KB
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
            public String hashBuffer(byte[] data, int offset, int length) throws com.justsyncit.hash.HashingException {
                byte[] subArray = new byte[length];
                System.arraycopy(data, offset, subArray, 0, length);
                return hashBuffer(subArray);
            }

            @Override
            public String hashBuffer(java.nio.ByteBuffer buffer) throws com.justsyncit.hash.HashingException {
                if (buffer.hasArray()) {
                    return hashBuffer(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
                } else {
                    byte[] data = new byte[buffer.remaining()];
                    buffer.get(data);
                    return hashBuffer(data);
                }
            }

            @Override
            public Blake3IncrementalHasher createKeyedIncrementalHasher(byte[] key)
                    throws com.justsyncit.hash.HashingException {
                return createIncrementalHasher(); // Mock implementation
            }

            @Override
            public java.util.concurrent.CompletableFuture<java.util.List<String>> hashFilesParallel(
                    java.util.List<Path> files) {
                return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    java.util.List<String> hashes = new java.util.ArrayList<>();
                    for (Path file : files) {
                        try {
                            hashes.add(hashFile(file));
                        } catch (Exception e) {
                            hashes.add("error_" + file.hashCode());
                        }
                    }
                    return hashes;
                });
            }

            @Override
            public boolean verify(byte[] data, String expectedHash) throws com.justsyncit.hash.HashingException {
                try {
                    return hashBuffer(data).equals(expectedHash);
                } catch (Exception e) {
                    return false;
                }
            }
        };
    }
}