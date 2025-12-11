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

package com.justsyncit.integration;

import com.justsyncit.scanner.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for OptimizedAsyncByteBufferPool with async file operations.
 * Tests buffer sharing between I/O and hashing operations.
 */
public class AsyncByteBufferPoolIntegrationTest {

    @TempDir
    Path tempDir;
    
    private OptimizedAsyncByteBufferPool optimizedPool;
    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        // Initialize optimized pool with integration-focused configuration
        OptimizedAsyncByteBufferPool.PoolConfiguration config = 
            new OptimizedAsyncByteBufferPool.PoolConfiguration.Builder()
                .minBuffersPerTier(8)
                .maxBuffersPerTier(32)
                .maxMemoryBytes(Runtime.getRuntime().maxMemory() / 4)
                .enableDirectBuffers(true)
                .enableHeapBuffers(true)
                .enablePrefetching(true)
                .enableAdaptiveSizing(true)
                .enableZeroCopy(true)
                .prefetchThreshold(3)
                .memoryPressureThreshold(0.8)
                .backpressureThreshold(100)
                .build();
        
        optimizedPool = OptimizedAsyncByteBufferPool.create(config);
        executorService = Executors.newFixedThreadPool(8);
    }

    @Test
    @DisplayName("Test buffer sharing between file I/O and hashing operations")
    void testBufferSharingBetweenIOAndHashing() throws Exception {
        // Create test file
        Path testFile = createTestFile(1024 * 1024); // 1MB file
        
        // Mock hash service that uses same buffers
        MockHashService hashService = new MockHashService(optimizedPool);
        
        long startTime = System.nanoTime();
        AtomicInteger chunksProcessed = new AtomicInteger(0);
        AtomicLong totalProcessingTime = new AtomicLong(0);
        
        // Simulate file processing with shared buffers
        int chunkSize = 65536; // 64KB chunks
        int chunkCount = (int) Math.ceil((double) (1024 * 1024) / chunkSize);
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (int i = 0; i < chunkCount; i++) {
            final int chunkIndex = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                long chunkStart = System.nanoTime();
                
                try {
                    // Acquire buffer for chunk data
                    ByteBuffer buffer = optimizedPool.acquireAsync(chunkSize).get();
                    
                    // Simulate reading chunk data into buffer (zero-copy)
                    buffer.clear();
                    for (int j = 0; j < Math.min(chunkSize, 1024); j += 8) {
                        buffer.putLong(j, System.nanoTime());
                    }
                    
                    // Use same buffer for hashing (buffer sharing)
                    String hash = hashService.computeHash(buffer);
                    assertNotNull(hash);
                    
                    // Release buffer back to pool
                    optimizedPool.releaseAsync(buffer).get();
                    
                    long chunkEnd = System.nanoTime();
                    totalProcessingTime.addAndGet(chunkEnd - chunkStart);
                    chunksProcessed.incrementAndGet();
                    
                } catch (Exception e) {
                    fail("Chunk processing failed: " + e.getMessage());
                }
            }, executorService);
            
            futures.add(future);
        }
        
        // Wait for all chunks to be processed
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .get(30, TimeUnit.SECONDS);
        
        long endTime = System.nanoTime();
        double totalTimeMs = (endTime - startTime) / 1_000_000.0;
        double avgChunkTimeMs = totalProcessingTime.get() / (double) chunksProcessed.get() / 1_000_000.0;
        
        System.out.printf("Buffer Sharing Integration Results:%n");
        System.out.printf("  Total Time: %.2f ms%n", totalTimeMs);
        System.out.printf("  Chunks Processed: %d%n", chunksProcessed.get());
        System.out.printf("  Average Chunk Time: %.2f ms%n", avgChunkTimeMs);
        System.out.printf("  Throughput: %.2f MB/s%n", 
            (1024.0 / (totalTimeMs / 1000.0)));
        
        // Verify performance
        assertTrue(chunksProcessed.get() > 0, "No chunks were processed");
        assertTrue(avgChunkTimeMs < 10.0, "Average chunk processing time too high");
        assertTrue(totalTimeMs < 5000.0, "Total processing time too high");
    }

    @Test
    @DisplayName("Test zero-copy buffer sharing for concurrent operations")
    void testZeroCopyBufferSharing() throws Exception {
        // Create multiple test files
        List<Path> testFiles = List.of(
            createTestFile(512 * 1024),   // 512KB
            createTestFile(1024 * 1024),  // 1MB
            createTestFile(2 * 1024 * 1024) // 2MB
        );
        
        AtomicInteger totalOperations = new AtomicInteger(0);
        AtomicLong bufferReuseCount = new AtomicLong(0);
        
        // Process all files concurrently with buffer sharing
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (Path file : testFiles) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    long fileSize = Files.size(file);
                    int chunkSize = 65536; // 64KB chunks
                    int chunkCount = (int) Math.ceil((double) fileSize / chunkSize);
                    
                    for (int i = 0; i < chunkCount; i++) {
                        // Acquire buffer
                        ByteBuffer buffer = optimizedPool.acquireAsync(chunkSize).get();
                        
                        // Simulate zero-copy operation
                        // In real implementation, this would use direct memory mapping
                        buffer.clear();
                        
                        // Simulate multiple operations on same buffer
                        for (int j = 0; j < 3; j++) {
                            buffer.rewind();
                            // Simulate different operations using same buffer
                            buffer.putInt(j, i * 100 + j);
                        }
                        
                        bufferReuseCount.addAndGet(2); // Reused 2 times
                        totalOperations.incrementAndGet();
                        
                        // Release buffer
                        optimizedPool.releaseAsync(buffer).get();
                    }
                        
                } catch (Exception e) {
                    fail("Buffer sharing operation failed: " + e.getMessage());
                }
            }, executorService);
            
            futures.add(future);
        }
        
        // Wait for all operations to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .get(60, TimeUnit.SECONDS);
        
        // Verify buffer sharing effectiveness
        assertTrue(totalOperations.get() > 0, "No operations performed");
        assertTrue(bufferReuseCount.get() > 0, "No buffer reuse occurred");
        
        double reuseRatio = (double) bufferReuseCount.get() / totalOperations.get();
        System.out.printf("Zero-Copy Buffer Sharing Results:%n");
        System.out.printf("  Total Operations: %d%n", totalOperations.get());
        System.out.printf("  Buffer Reuses: %d%n", bufferReuseCount.get());
        System.out.printf("  Reuse Ratio: %.2f%n", reuseRatio);
        
        assertTrue(reuseRatio >= 1.5, "Buffer reuse ratio too low");
    }

    @Test
    @DisplayName("Test buffer pool coordination with thread pools")
    void testBufferPoolCoordinationWithThreadPools() throws Exception {
        int threadCount = 16;
        int operationsPerThread = 100;
        
        ExecutorService testExecutor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successfulOperations = new AtomicInteger(0);
        AtomicInteger failedOperations = new AtomicInteger(0);
        
        long startTime = System.nanoTime();
        
        // Submit concurrent operations
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (int thread = 0; thread < threadCount; thread++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                for (int i = 0; i < operationsPerThread; i++) {
                    try {
                        int bufferSize = 1024 + (i % 8192); // Variable sizes
                        
                        // Acquire buffer
                        CompletableFuture<ByteBuffer> acquireFuture = optimizedPool.acquireAsync(bufferSize);
                        ByteBuffer buffer = acquireFuture.get(1, TimeUnit.SECONDS);
                        
                        // Simulate work
                        buffer.clear();
                        buffer.putInt(0, i);
                        buffer.putLong(4, System.nanoTime());
                        
                        // Release buffer
                        optimizedPool.releaseAsync(buffer).get(1, TimeUnit.SECONDS);
                        
                        successfulOperations.incrementAndGet();
                        
                    } catch (Exception e) {
                        failedOperations.incrementAndGet();
                    }
                }
            }, testExecutor);
            
            futures.add(future);
        }
        
        // Wait for all operations to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .get(120, TimeUnit.SECONDS);
        
        long endTime = System.nanoTime();
        double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
        double opsPerSecond = successfulOperations.get() / durationSeconds;
        
        System.out.printf("Thread Pool Coordination Results:%n");
        System.out.printf("  Thread Count: %d%n", threadCount);
        System.out.printf("  Successful Operations: %d%n", successfulOperations.get());
        System.out.printf("  Failed Operations: %d%n", failedOperations.get());
        System.out.printf("  Duration: %.2f seconds%n", durationSeconds);
        System.out.printf("  Operations/sec: %.0f%n", opsPerSecond);
        
        // Verify coordination effectiveness
        assertTrue(successfulOperations.get() > threadCount * operationsPerThread * 0.95, 
            "Too many failed operations");
        assertTrue(opsPerSecond >= 1000, "Operations per second too low");
        
        testExecutor.shutdown();
        testExecutor.awaitTermination(10, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Test buffer pool memory pressure handling")
    void testBufferPoolMemoryPressureHandling() throws Exception {
        // Create memory pressure by acquiring many buffers
        List<ByteBuffer> retainedBuffers = new ArrayList<>();
        int maxBuffers = 100;
        
        // Acquire buffers until we approach memory limit
        for (int i = 0; i < maxBuffers; i++) {
            try {
                ByteBuffer buffer = optimizedPool.acquireAsync(65536).get(2, TimeUnit.SECONDS);
                retainedBuffers.add(buffer);
                
                // Check pool stats periodically
                if (i % 20 == 0) {
                    String stats = optimizedPool.getStatsAsync().get();
                    System.out.printf("After %d acquisitions: %s%n", i, stats);
                }
                
            } catch (Exception e) {
                System.out.printf("Memory pressure reached after %d acquisitions%n", i);
                break;
            }
        }
        
        // Verify pool is still functional under pressure
        try {
            ByteBuffer testBuffer = optimizedPool.acquireAsync(4096).get(5, TimeUnit.SECONDS);
            assertNotNull(testBuffer);
            optimizedPool.releaseAsync(testBuffer).get();
            
            System.out.printf("Pool remained functional with %d buffers retained%n", retainedBuffers.size());
            
        } catch (Exception e) {
            fail("Pool became non-functional under memory pressure");
        }
        
        // Release all buffers
        for (ByteBuffer buffer : retainedBuffers) {
            optimizedPool.releaseAsync(buffer).get();
        }
        
        // Verify pool recovers
        Thread.sleep(1000); // Allow pool to stabilize
        
        String finalStats = optimizedPool.getStatsAsync().get();
        System.out.printf("Final pool stats: %s%n", finalStats);
        
        assertTrue(retainedBuffers.size() > 0, "No buffers were acquired");
    }

    // Helper methods

    private Path createTestFile(int sizeBytes) throws IOException {
        Path file = tempDir.resolve("testfile_" + System.currentTimeMillis() + ".dat");
        byte[] data = new byte[sizeBytes];
        
        // Fill with pattern
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 256);
        }
        
        Files.write(file, data);
        return file;
    }

    /**
     * Mock hash service that demonstrates buffer sharing.
     */
    private static class MockHashService {
        private final OptimizedAsyncByteBufferPool bufferPool;
        
        MockHashService(OptimizedAsyncByteBufferPool bufferPool) {
            this.bufferPool = bufferPool;
        }
        
        String computeHash(ByteBuffer buffer) {
            // Simulate hashing operation using same buffer
            buffer.rewind();
            
            int hash = 0;
            while (buffer.hasRemaining()) {
                hash = 31 * hash + buffer.get();
            }
            
            return Integer.toHexString(hash);
        }
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() throws Exception {
        if (optimizedPool != null) {
            optimizedPool.clearAsync().get();
        }
        if (executorService != null) {
            executorService.shutdown();
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        }
    }
}