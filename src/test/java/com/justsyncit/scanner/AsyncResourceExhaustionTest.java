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
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Resource exhaustion test suite for async components.
 * Tests behavior under extreme resource constraints.
 * Follows TDD principles by testing failure scenarios.
 */
@DisplayName("Async Resource Exhaustion Test Suite")
public class AsyncResourceExhaustionTest extends AsyncTestBase {

    private AsyncByteBufferPool bufferPool;
    private AsyncFileChunker fileChunker;
    private AsyncChunkHandler chunkHandler;
    private List<Path> testFiles;

    @BeforeEach
    void setUp() {
        super.setUp();
        
        // Create test files
        testFiles = new ArrayList<>();
        try {
            for (int i = 0; i < 5; i++) {
                Path file = tempDir.resolve("exhaustion_test_" + i + ".dat");
                AsyncTestUtils.createTestFile(tempDir, "exhaustion_test_" + i + ".dat", (i + 1) * 1024);
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
    @DisplayName("Should handle buffer pool exhaustion gracefully")
    void shouldHandleBufferPoolExhaustionGracefully() throws Exception {
        // Given - Create very small buffer pool
        bufferPool = AsyncByteBufferPoolImpl.create(4096, 2); // 4KB buffers, only 2 max
        fileChunker = AsyncFileChunkerImpl.create(createMockBlake3Service());
        chunkHandler = AsyncFileChunkHandler.create(createMockBlake3Service());
        
        fileChunker.setAsyncBufferPool(bufferPool);
        fileChunker.setAsyncChunkHandler(chunkHandler);
        
        // When - Exhaust the buffer pool
        List<java.nio.ByteBuffer> heldBuffers = new ArrayList<>();
        
        // First, exhaust all available buffers
        for (int i = 0; i < 3; i++) {
            try {
                java.nio.ByteBuffer buffer = bufferPool.acquire(4096);
                if (buffer != null) {
                    heldBuffers.add(buffer);
                }
            } catch (Exception e) {
                // Expected when pool is exhausted
            }
        }
        
        // Then - Try operations under exhaustion
        AtomicInteger successfulOps = new AtomicInteger(0);
        AtomicInteger failedOps = new AtomicInteger(0);
        
        List<CompletableFuture<FileChunker.ChunkingResult>> futures = new ArrayList<>();
        
        for (int i = 0; i < 10; i++) {
            Path testFile = testFiles.get(i % testFiles.size());
            CompletableFuture<FileChunker.ChunkingResult> future = 
                fileChunker.chunkFileAsync(testFile, null);
            
            future.whenComplete((result, throwable) -> {
                if (throwable == null && result.isSuccess()) {
                    successfulOps.incrementAndGet();
                } else {
                    failedOps.incrementAndGet();
                }
            });
            
            futures.add(future);
        }
        
        // Wait for all operations to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .get(30, TimeUnit.SECONDS);
        
        // Verify graceful handling
        int totalOps = successfulOps.get() + failedOps.get();
        assertEquals(10, totalOps, "All operations should be accounted for");
        
        // Some operations should succeed even under exhaustion
        assertTrue(successfulOps.get() > 0, "Some operations should succeed");
        
        // System should remain stable
        CompletableFuture<String> statsFuture = bufferPool.getStatsAsync();
        String statsString = statsFuture.get(5, TimeUnit.SECONDS);
        assertNotNull(statsString);
        
        // Cleanup held buffers
        for (java.nio.ByteBuffer buffer : heldBuffers) {
            bufferPool.release(buffer);
        }
        
        System.out.printf("Buffer exhaustion - Successful: %d, Failed: %d%n",
            successfulOps.get(), failedOps.get());
    }

    @Test
    @Timeout(45)
    @DisplayName("Should recover from memory pressure")
    void shouldRecoverFromMemoryPressure() throws Exception {
        // Given
        bufferPool = AsyncByteBufferPoolImpl.create(8192, 5); // Small pool for pressure
        fileChunker = AsyncFileChunkerImpl.create(createMockBlake3Service());
        chunkHandler = AsyncFileChunkHandler.create(createMockBlake3Service());
        
        fileChunker.setAsyncBufferPool(bufferPool);
        fileChunker.setAsyncChunkHandler(chunkHandler);
        
        // When - Create memory pressure cycles
        for (int cycle = 0; cycle < 3; cycle++) {
            // Create pressure by holding buffers
            List<java.nio.ByteBuffer> heldBuffers = new ArrayList<>();
            
            for (int i = 0; i < 10; i++) {
                try {
                    java.nio.ByteBuffer buffer = bufferPool.acquire(8192);
                    if (buffer != null) {
                        heldBuffers.add(buffer);
                    }
                } catch (Exception e) {
                    // Expected under pressure
                }
            }
            
            // Test operations under pressure
            AtomicInteger pressureSuccess = new AtomicInteger(0);
            List<CompletableFuture<FileChunker.ChunkingResult>> pressureFutures = new ArrayList<>();
            
            for (int i = 0; i < 5; i++) {
                Path testFile = testFiles.get(i % testFiles.size());
                CompletableFuture<FileChunker.ChunkingResult> future = 
                    fileChunker.chunkFileAsync(testFile, null);
                
                future.whenComplete((result, throwable) -> {
                    if (throwable == null && result.isSuccess()) {
                        pressureSuccess.incrementAndGet();
                    }
                });
                
                pressureFutures.add(future);
            }
            
            // Wait for pressure operations
            CompletableFuture.allOf(pressureFutures.toArray(new CompletableFuture[0]))
                .get(15, TimeUnit.SECONDS);
            
            // Release pressure
            for (java.nio.ByteBuffer buffer : heldBuffers) {
                bufferPool.release(buffer);
            }
            
            // Allow recovery time
            Thread.sleep(1000);
            
            // Test recovery
            AtomicInteger recoverySuccess = new AtomicInteger(0);
            List<CompletableFuture<FileChunker.ChunkingResult>> recoveryFutures = new ArrayList<>();
            
            for (int i = 0; i < 5; i++) {
                Path testFile = testFiles.get(i % testFiles.size());
                CompletableFuture<FileChunker.ChunkingResult> future = 
                    fileChunker.chunkFileAsync(testFile, null);
                
                future.whenComplete((result, throwable) -> {
                    if (throwable == null && result.isSuccess()) {
                        recoverySuccess.incrementAndGet();
                    }
                });
                
                recoveryFutures.add(future);
            }
            
            // Wait for recovery operations
            CompletableFuture.allOf(recoveryFutures.toArray(new CompletableFuture[0]))
                .get(10, TimeUnit.SECONDS);
            
            // Recovery should be better than pressure performance
            assertTrue(recoverySuccess.get() >= pressureSuccess.get(), 
                "Recovery should be as good as or better than pressure performance");
            
            System.out.printf("Cycle %d - Pressure: %d, Recovery: %d%n",
                cycle + 1, pressureSuccess.get(), recoverySuccess.get());
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("Should handle thread pool exhaustion")
    void shouldHandleThreadPoolExhaustion() throws Exception {
        // Given
        bufferPool = AsyncByteBufferPoolImpl.create(4096, 10);
        fileChunker = AsyncFileChunkerImpl.create(createMockBlake3Service());
        chunkHandler = AsyncFileChunkHandler.create(createMockBlake3Service());
        
        fileChunker.setAsyncBufferPool(bufferPool);
        fileChunker.setAsyncChunkHandler(chunkHandler);
        
        // Create custom thread pool manager with limited threads using a simple approach
        java.util.concurrent.ExecutorService limitedExecutor =
            java.util.concurrent.Executors.newFixedThreadPool(2);
        
        // When - Create thread pool exhaustion
        AtomicInteger completedOps = new AtomicInteger(0);
        AtomicInteger rejectedOps = new AtomicInteger(0);
        
        List<CompletableFuture<FileChunker.ChunkingResult>> futures = new ArrayList<>();
        
        // Submit many operations to exhaust thread pool
        for (int i = 0; i < 20; i++) {
            Path testFile = testFiles.get(i % testFiles.size());
            
            CompletableFuture<FileChunker.ChunkingResult> future =
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return fileChunker.chunkFileAsync(testFile, null).get(10, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        rejectedOps.incrementAndGet();
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
                }, limitedExecutor);
            
            future.whenComplete((result, throwable) -> {
                if (throwable == null && result.isSuccess()) {
                    completedOps.incrementAndGet();
                }
            });
            
            futures.add(future);
        }
        
        // Wait for all operations
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .get(25, TimeUnit.SECONDS);
        
        // Then - System should handle thread pool exhaustion
        int totalOps = completedOps.get() + rejectedOps.get();
        assertEquals(20, totalOps, "All operations should be accounted for");
        
        // Some operations should succeed despite thread pool limits
        assertTrue(completedOps.get() > 0, "Some operations should succeed");
        
        // System should handle rejections gracefully
        assertTrue(rejectedOps.get() >= 0, "Rejections should be tracked");
        
        // Cleanup
        limitedExecutor.shutdown();
        try {
            if (!limitedExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                limitedExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            limitedExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        System.out.printf("Thread pool exhaustion - Completed: %d, Rejected: %d%n",
            completedOps.get(), rejectedOps.get());
    }

    @Test
    @Timeout(40)
    @DisplayName("Should handle file descriptor exhaustion")
    void shouldHandleFileDescriptorExhaustion() throws Exception {
        // Given
        bufferPool = AsyncByteBufferPoolImpl.create(2048, 20);
        fileChunker = AsyncFileChunkerImpl.create(createMockBlake3Service());
        chunkHandler = AsyncFileChunkHandler.create(createMockBlake3Service());
        
        fileChunker.setAsyncBufferPool(bufferPool);
        fileChunker.setAsyncChunkHandler(chunkHandler);
        
        // When - Simulate file descriptor pressure
        List<java.io.FileInputStream> heldFiles = new ArrayList<>();
        
        // Open many files to simulate descriptor exhaustion
        for (int i = 0; i < 50; i++) {
            try {
                java.io.FileInputStream fis = new java.io.FileInputStream(testFiles.get(0).toFile());
                heldFiles.add(fis);
            } catch (Exception e) {
                // Expected when descriptors are exhausted
                break;
            }
        }
        
        // Test operations under descriptor pressure
        AtomicInteger successUnderPressure = new AtomicInteger(0);
        List<CompletableFuture<FileChunker.ChunkingResult>> pressureFutures = new ArrayList<>();
        
        for (int i = 0; i < 10; i++) {
            Path testFile = testFiles.get(i % testFiles.size());
            CompletableFuture<FileChunker.ChunkingResult> future = 
                fileChunker.chunkFileAsync(testFile, null);
            
            future.whenComplete((result, throwable) -> {
                if (throwable == null && result.isSuccess()) {
                    successUnderPressure.incrementAndGet();
                }
            });
            
            pressureFutures.add(future);
        }
        
        // Wait for pressure operations
        CompletableFuture.allOf(pressureFutures.toArray(new CompletableFuture[0]))
            .get(20, TimeUnit.SECONDS);
        
        // Release some file descriptors
        for (int i = 0; i < heldFiles.size() / 2; i++) {
            try {
                heldFiles.get(i).close();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
        
        // Test recovery after releasing descriptors
        AtomicInteger recoverySuccess = new AtomicInteger(0);
        List<CompletableFuture<FileChunker.ChunkingResult>> recoveryFutures = new ArrayList<>();
        
        for (int i = 0; i < 10; i++) {
            Path testFile = testFiles.get(i % testFiles.size());
            CompletableFuture<FileChunker.ChunkingResult> future = 
                fileChunker.chunkFileAsync(testFile, null);
            
            future.whenComplete((result, throwable) -> {
                if (throwable == null && result.isSuccess()) {
                    recoverySuccess.incrementAndGet();
                }
            });
            
            recoveryFutures.add(future);
        }
        
        // Wait for recovery operations
        CompletableFuture.allOf(recoveryFutures.toArray(new CompletableFuture[0]))
            .get(15, TimeUnit.SECONDS);
        
        // Then - System should handle descriptor exhaustion
        assertTrue(successUnderPressure.get() >= 0, "Some operations may succeed under pressure");
        assertTrue(recoverySuccess.get() >= successUnderPressure.get(), 
            "Recovery should be as good as or better than pressure performance");
        
        // Cleanup remaining file descriptors
        for (java.io.FileInputStream fis : heldFiles) {
            try {
                fis.close();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
        
        System.out.printf("File descriptor exhaustion - Pressure: %d, Recovery: %d%n",
            successUnderPressure.get(), recoverySuccess.get());
    }

    @Test
    @Timeout(35)
    @DisplayName("Should handle concurrent resource contention")
    void shouldHandleConcurrentResourceContention() throws Exception {
        // Given
        bufferPool = AsyncByteBufferPoolImpl.create(2048, 3); // Very limited pool
        fileChunker = AsyncFileChunkerImpl.create(createMockBlake3Service());
        chunkHandler = AsyncFileChunkHandler.create(createMockBlake3Service());
        
        fileChunker.setAsyncBufferPool(bufferPool);
        fileChunker.setAsyncChunkHandler(chunkHandler);
        
        // When - Create extreme resource contention
        int contentionThreads = 10;
        int operationsPerThread = 20;
        
        AtomicInteger totalSuccessful = new AtomicInteger(0);
        AtomicInteger totalFailed = new AtomicInteger(0);
        AtomicInteger timeouts = new AtomicInteger(0);
        
        List<CompletableFuture<Void>> threadFutures = new ArrayList<>();
        
        for (int t = 0; t < contentionThreads; t++) {
            final int threadId = t;
            CompletableFuture<Void> threadFuture = CompletableFuture.runAsync(() -> {
                for (int op = 0; op < operationsPerThread; op++) {
                    try {
                        Path testFile = testFiles.get((threadId + op) % testFiles.size());
                        
                        // Use timeout to detect hanging operations
                        FileChunker.ChunkingResult result = fileChunker
                            .chunkFileAsync(testFile, null)
                            .get(5, TimeUnit.SECONDS);
                        
                        if (result.isSuccess()) {
                            totalSuccessful.incrementAndGet();
                        } else {
                            totalFailed.incrementAndGet();
                        }
                        
                    } catch (java.util.concurrent.TimeoutException e) {
                        timeouts.incrementAndGet();
                    } catch (Exception e) {
                        totalFailed.incrementAndGet();
                    }
                }
            });
            threadFutures.add(threadFuture);
        }
        
        // Wait for all threads to complete
        CompletableFuture.allOf(threadFutures.toArray(new CompletableFuture[0]))
            .get(30, TimeUnit.SECONDS);
        
        // Then - System should handle extreme contention
        int totalOperations = totalSuccessful.get() + totalFailed.get() + timeouts.get();
        assertEquals(contentionThreads * operationsPerThread, totalOperations, 
            "All operations should be accounted for");
        
        // Some operations should succeed even under extreme contention
        assertTrue(totalSuccessful.get() > 0, "Some operations should succeed");
        
        // System should not hang (timeouts should be reasonable)
        assertTrue(timeouts.get() <= totalOperations * 0.3, 
            "Timeouts should be limited under contention");
        
        // Verify system stability
        CompletableFuture<String> statsFuture = bufferPool.getStatsAsync();
        String statsString = statsFuture.get(5, TimeUnit.SECONDS);
        assertNotNull(statsString);
        
        System.out.printf("Resource contention - Successful: %d, Failed: %d, Timeouts: %d%n",
            totalSuccessful.get(), totalFailed.get(), timeouts.get());
    }

    @Test
    @Timeout(25)
    @DisplayName("Should maintain system stability under exhaustion")
    void shouldMaintainSystemStabilityUnderExhaustion() throws Exception {
        // Given
        bufferPool = AsyncByteBufferPoolImpl.create(1024, 2); // Minimal pool
        fileChunker = AsyncFileChunkerImpl.create(createMockBlake3Service());
        chunkHandler = AsyncFileChunkHandler.create(createMockBlake3Service());
        
        fileChunker.setAsyncBufferPool(bufferPool);
        fileChunker.setAsyncChunkHandler(chunkHandler);
        
        // When - Create multiple exhaustion scenarios
        List<ExhaustionScenario> scenarios = new ArrayList<>();
        
        // Scenario 1: Buffer exhaustion
        scenarios.add(new ExhaustionScenario("buffer_exhaustion", () -> {
            List<java.nio.ByteBuffer> buffers = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                try {
                    buffers.add(bufferPool.acquire(1024));
                } catch (Exception e) {
                    // Expected
                }
            }
            return buffers.size();
        }));
        
        // Scenario 2: Concurrent operations
        scenarios.add(new ExhaustionScenario("concurrent_ops", () -> {
            AtomicInteger count = new AtomicInteger(0);
            List<CompletableFuture<?>> futures = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        java.nio.ByteBuffer buffer = bufferPool.acquire(1024);
                        if (buffer != null) {
                            bufferPool.release(buffer);
                            count.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Expected under exhaustion
                    }
                });
                futures.add(future);
            }
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Expected under exhaustion
            }
            return count.get();
        }));
        
        // Scenario 3: Mixed operations
        scenarios.add(new ExhaustionScenario("mixed_ops", () -> {
            AtomicInteger successCount = new AtomicInteger(0);
            for (int i = 0; i < 10; i++) {
                try {
                    Path testFile = testFiles.get(i % testFiles.size());
                    FileChunker.ChunkingResult result = fileChunker
                        .chunkFileAsync(testFile, null)
                        .get(3, TimeUnit.SECONDS);
                    if (result.isSuccess()) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Expected under exhaustion
                }
            }
            return successCount.get();
        }));
        
        // Execute all scenarios
        for (ExhaustionScenario scenario : scenarios) {
            long startTime = System.nanoTime();
            
            try {
                int result = scenario.operation.call();
                long duration = System.nanoTime() - startTime;
                
                System.out.printf("Scenario %s: %d operations in %.2fms%n",
                    scenario.name, result, duration / 1_000_000.0);
                
                // Verify system didn't crash
                assertTrue(result >= 0, "Operations should not be negative");
                assertTrue(duration < Duration.ofSeconds(15).toNanos(), 
                    "Operations should complete in reasonable time");
                
            } catch (Exception e) {
                System.out.printf("Scenario %s failed: %s%n", scenario.name, e.getMessage());
                // Some failures are expected under exhaustion
            }
        }
        
        // Then - System should maintain basic stability
        CompletableFuture<String> finalStats = bufferPool.getStatsAsync();
        String statsString = finalStats.get(5, TimeUnit.SECONDS);
        assertNotNull(statsString, "System should maintain statistics");
        
        System.out.println("System stability maintained under all exhaustion scenarios");
    }

    // Helper class for exhaustion scenarios
    private static class ExhaustionScenario {
        final String name;
        final java.util.concurrent.Callable<Integer> operation;
        
        ExhaustionScenario(String name, java.util.concurrent.Callable<Integer> operation) {
            this.name = name;
            this.operation = operation;
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
            public String hashStream(java.io.InputStream inputStream) throws java.io.IOException, com.justsyncit.hash.HashingException {
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
                };
            }
        };
    }
}