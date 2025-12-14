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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concurrency test suite for async components.
 * Tests thread safety and behavior under concurrent access.
 * Follows TDD principles by testing concurrent scenarios.
 */
@DisplayName("Async Concurrency Test Suite")
public class AsyncConcurrencyTest extends AsyncTestBase {

    private AsyncByteBufferPool bufferPool;
    private AsyncFileChunker fileChunker;
    private AsyncChunkHandler chunkHandler;
    private List<Path> testFiles;

    @BeforeEach
    void setUp() {
        super.setUp();

        // Create test components
        bufferPool = AsyncByteBufferPoolImpl.create(32 * 1024, 20); // 32KB buffers, 20 max
        fileChunker = AsyncFileChunkerImpl.create(createMockBlake3Service());
        chunkHandler = AsyncFileChunkHandler.create(createMockBlake3Service());

        // Configure components
        fileChunker.setAsyncBufferPool(bufferPool);
        fileChunker.setAsyncChunkHandler(chunkHandler);
        fileChunker.setMaxConcurrentOperations(6);

        // Create test files
        testFiles = new ArrayList<>();
        try {
            for (int i = 0; i < 10; i++) {
                Path file = tempDir.resolve("concurrency_test_" + i + ".dat");
                AsyncTestUtils.createTestFile(tempDir, "concurrency_test_" + i + ".dat", (i + 1) * 1024);
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
    @Timeout(15)
    @DisplayName("Should handle concurrent buffer pool operations")
    void shouldHandleConcurrentBufferPoolOperations() throws Exception {
        // Given
        int threadCount = 8;
        int operationsPerThread = 50;
        AtomicInteger successfulOperations = new AtomicInteger(0);
        AtomicInteger failedOperations = new AtomicInteger(0);

        // When - Execute concurrent buffer operations
        List<CompletableFuture<Void>> threadFutures = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            CompletableFuture<Void> threadFuture = CompletableFuture.runAsync(() -> {
                for (int op = 0; op < operationsPerThread; op++) {
                    try {
                        // Acquire buffer
                        java.nio.ByteBuffer buffer = bufferPool.acquire(8192);
                        assertNotNull(buffer, "Buffer should not be null");
                        assertTrue(buffer.capacity() >= 8192, "Buffer should have sufficient capacity");

                        // Simulate work
                        Thread.sleep(1);

                        // Release buffer
                        bufferPool.release(buffer);
                        successfulOperations.incrementAndGet();

                    } catch (Exception e) {
                        failedOperations.incrementAndGet();
                        // Some failures are expected under high contention
                    }
                }
            });
            threadFutures.add(threadFuture);
        }

        // Then - All threads should complete
        AsyncTestUtils.waitForAll(Duration.ofSeconds(20), threadFutures);

        int totalOperations = successfulOperations.get() + failedOperations.get();
        assertEquals(threadCount * operationsPerThread, totalOperations,
                "All operations should be accounted for");

        // Most operations should succeed
        double successRate = (double) successfulOperations.get() / totalOperations;
        assertTrue(successRate >= 0.8,
                String.format("Success rate should be at least 80%%: %.2f", successRate));

        // Verify pool consistency
        CompletableFuture<String> statsFuture = bufferPool.getStatsAsync();
        String statsString = statsFuture.get(5, TimeUnit.SECONDS);
        assertNotNull(statsString);
        assertTrue(statsString.contains("Total:"), "Pool should maintain statistics");
    }

    @Test
    @Timeout(20)
    @DisplayName("Should handle concurrent file chunking operations")
    void shouldHandleConcurrentFileChunkingOperations() throws Exception {
        // Given
        int concurrentChunking = 6;
        int filesPerOperation = 3;
        AtomicInteger successfulChunking = new AtomicInteger(0);
        AtomicInteger failedChunking = new AtomicInteger(0);
        AtomicLong totalBytesProcessed = new AtomicLong(0);

        // When - Execute concurrent chunking operations
        List<CompletableFuture<FileChunker.ChunkingResult>> chunkingFutures = new ArrayList<>();

        for (int round = 0; round < 5; round++) {
            for (int i = 0; i < concurrentChunking; i++) {
                Path testFile = testFiles.get(i % testFiles.size());

                CompletableFuture<FileChunker.ChunkingResult> future = fileChunker.chunkFileAsync(testFile, null);

                future.whenComplete((result, throwable) -> {
                    if (throwable == null && result.isSuccess()) {
                        successfulChunking.incrementAndGet();
                        totalBytesProcessed.addAndGet(result.getTotalSize());
                    } else {
                        failedChunking.incrementAndGet();
                    }
                });

                chunkingFutures.add(future);
            }

            // Wait for this round to complete before starting next
            AsyncTestUtils.waitForAll(Duration.ofSeconds(15), chunkingFutures);
            chunkingFutures.clear();
        }

        // Then - Operations should complete with reasonable success rate
        int totalOperations = successfulChunking.get() + failedChunking.get();
        assertTrue(totalOperations > 0, "Some operations should complete");

        double successRate = (double) successfulChunking.get() / totalOperations;
        assertTrue(successRate >= 0.7,
                String.format("Success rate should be at least 70%%: %.2f", successRate));

        assertTrue(totalBytesProcessed.get() > 0, "Some bytes should be processed");

        System.out.printf("Concurrent chunking: %d successful, %d failed, %.2f MB processed%n",
                successfulChunking.get(), failedChunking.get(),
                totalBytesProcessed.get() / (1024.0 * 1024.0));
    }

    @Test
    @Timeout(15)
    @DisplayName("Should handle mixed concurrent operations")
    void shouldHandleMixedConcurrentOperations() throws Exception {
        // Given
        int bufferOperations = 20;
        int chunkingOperations = 10;
        int handlerOperations = 15;

        AtomicInteger bufferSuccess = new AtomicInteger(0);
        AtomicInteger chunkingSuccess = new AtomicInteger(0);
        AtomicInteger handlerSuccess = new AtomicInteger(0);

        List<CompletableFuture<?>> allFutures = new ArrayList<>();

        // When - Execute mixed concurrent operations

        // Buffer pool operations
        for (int i = 0; i < bufferOperations; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    java.nio.ByteBuffer buffer = bufferPool.acquire(4096);
                    Thread.sleep(2);
                    bufferPool.release(buffer);
                    bufferSuccess.incrementAndGet();
                } catch (Exception e) {
                    // Expected under contention
                }
            });
            allFutures.add(future);
        }

        // File chunking operations
        for (int i = 0; i < chunkingOperations; i++) {
            Path testFile = testFiles.get(i % testFiles.size());
            CompletableFuture<FileChunker.ChunkingResult> future = fileChunker.chunkFileAsync(testFile, null);

            future.whenComplete((result, throwable) -> {
                if (throwable == null && result.isSuccess()) {
                    chunkingSuccess.incrementAndGet();
                }
            });
            allFutures.add(future);
        }

        // Chunk handler operations
        java.nio.ByteBuffer[] testChunks = new java.nio.ByteBuffer[3];
        for (int i = 0; i < testChunks.length; i++) {
            testChunks[i] = java.nio.ByteBuffer.allocate(1024);
            testChunks[i].put(("test chunk data " + i).getBytes());
            testChunks[i].flip();
        }

        for (int i = 0; i < handlerOperations; i++) {
            Path testFile = testFiles.get(0);
            CompletableFuture<String[]> future = chunkHandler.processChunksAsync(testChunks, testFile);

            future.whenComplete((result, throwable) -> {
                if (throwable == null && result != null) {
                    handlerSuccess.incrementAndGet();
                }
            });
            allFutures.add(future);
        }

        // Then - All operations should complete
        AsyncTestUtils.waitForAll(Duration.ofSeconds(25), allFutures);

        // Verify results
        assertTrue(bufferSuccess.get() > 0, "Some buffer operations should succeed");
        assertTrue(chunkingSuccess.get() > 0, "Some chunking operations should succeed");
        assertTrue(handlerSuccess.get() > 0, "Some handler operations should succeed");

        System.out.printf("Mixed operations - Buffer: %d, Chunking: %d, Handler: %d%n",
                bufferSuccess.get(), chunkingSuccess.get(), handlerSuccess.get());
    }

    @Test
    @Timeout(10)
    @DisplayName("Should maintain thread safety under high contention")
    void shouldMaintainThreadSafetyUnderHighContention() throws Exception {
        // Given
        int contentionThreads = 12;
        int contentionOperations = 100;
        AtomicInteger dataCorruptionErrors = new AtomicInteger(0);
        AtomicInteger illegalStateErrors = new AtomicInteger(0);

        // When - Create high contention scenario
        List<CompletableFuture<Void>> contentionFutures = new ArrayList<>();

        for (int t = 0; t < contentionThreads; t++) {
            final int threadId = t;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                for (int op = 0; op < contentionOperations; op++) {
                    try {
                        // Rapid acquire/release cycles to create contention
                        for (int cycle = 0; cycle < 5; cycle++) {
                            java.nio.ByteBuffer buffer = bufferPool.acquire(2048);

                            // Validate buffer state
                            if (buffer == null || buffer.capacity() < 2048) {
                                dataCorruptionErrors.incrementAndGet();
                                break;
                            }

                            // Write test data
                            buffer.putInt(threadId * 1000 + op * 10 + cycle);
                            buffer.putInt(op);

                            // Read back and validate
                            buffer.flip();
                            int writtenThreadId = buffer.getInt();
                            int writtenOpId = buffer.getInt();

                            if (writtenThreadId != threadId * 1000 + op * 10 + cycle
                                    || writtenOpId != op) {
                                dataCorruptionErrors.incrementAndGet();
                            }

                            buffer.clear();
                            bufferPool.release(buffer);
                        }
                    } catch (IllegalStateException e) {
                        illegalStateErrors.incrementAndGet();
                    } catch (Exception e) {
                        // Other exceptions are expected under high contention
                    }
                }
            });
            contentionFutures.add(future);
        }

        // Then - System should maintain thread safety
        AsyncTestUtils.waitForAll(Duration.ofSeconds(15), contentionFutures);

        // Verify no data corruption
        assertEquals(0, dataCorruptionErrors.get(),
                "No data corruption should occur under contention");

        // Some illegal state exceptions are expected under high contention
        System.out.printf("Contention test - Data corruption: %d, Illegal state: %d%n",
                dataCorruptionErrors.get(), illegalStateErrors.get());
    }

    @Test
    @Timeout(12)
    @DisplayName("Should handle concurrent resource allocation and deallocation")
    void shouldHandleConcurrentResourceAllocationAndDeallocation() throws Exception {
        // Given
        int allocationThreads = 6;
        int deallocationThreads = 4;
        AtomicInteger allocatedBuffers = new AtomicInteger(0);
        AtomicInteger deallocatedBuffers = new AtomicInteger(0);
        List<java.nio.ByteBuffer> sharedBuffers = new ArrayList<>();

        // When - Execute concurrent allocation and deallocation
        List<CompletableFuture<Void>> allocationFutures = new ArrayList<>();

        // Allocation threads
        for (int t = 0; t < allocationThreads; t++) {
            final int threadId = t;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                for (int i = 0; i < 20; i++) {
                    try {
                        java.nio.ByteBuffer buffer = bufferPool.acquire(8192);
                        if (buffer != null) {
                            // Mark buffer with thread ID
                            buffer.putInt(threadId);
                            buffer.flip();

                            synchronized (sharedBuffers) {
                                sharedBuffers.add(buffer);
                                allocatedBuffers.incrementAndGet();
                            }
                        }
                        Thread.sleep(10);
                    } catch (Exception e) {
                        // Expected under resource pressure
                    }
                }
            });
            allocationFutures.add(future);
        }

        // Deallocation threads
        for (int t = 0; t < deallocationThreads; t++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                for (int i = 0; i < 30; i++) {
                    try {
                        java.nio.ByteBuffer buffer;
                        synchronized (sharedBuffers) {
                            if (!sharedBuffers.isEmpty()) {
                                buffer = sharedBuffers.remove(0);
                            } else {
                                buffer = null;
                            }
                        }

                        if (buffer != null) {
                            // Validate and release
                            buffer.clear();
                            bufferPool.release(buffer);
                            deallocatedBuffers.incrementAndGet();
                        }

                        Thread.sleep(15);
                    } catch (Exception e) {
                        // Expected under concurrent access
                    }
                }
            });
            allocationFutures.add(future);
        }

        // Then - Operations should complete safely
        AsyncTestUtils.waitForAll(Duration.ofSeconds(20), allocationFutures);

        // Verify resource management
        assertTrue(allocatedBuffers.get() > 0, "Some buffers should be allocated");
        assertTrue(deallocatedBuffers.get() > 0, "Some buffers should be deallocated");

        // Clean up remaining buffers
        synchronized (sharedBuffers) {
            for (java.nio.ByteBuffer buffer : sharedBuffers) {
                try {
                    bufferPool.release(buffer);
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
            sharedBuffers.clear();
        }

        System.out.printf("Resource management - Allocated: %d, Deallocated: %d%n",
                allocatedBuffers.get(), deallocatedBuffers.get());
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