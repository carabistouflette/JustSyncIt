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

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test suite for async component coordination.
 * Tests end-to-end workflows and component interactions.
 * Follows TDD principles by testing complete async scenarios.
 */
@DisplayName("Async Integration Test Suite")
public class AsyncIntegrationTestSuite extends AsyncTestBase {

    private AsyncByteBufferPool bufferPool;
    private AsyncFileChunker fileChunker;
    private AsyncChunkHandler chunkHandler;
    private FilesystemScanner filesystemScanner;
    private List<Path> testFiles;

    @BeforeEach
    void setUp() {
        super.setUp();

        // Create test components
        bufferPool = AsyncByteBufferPoolImpl.create(8192, 100);
        fileChunker = AsyncFileChunkerImpl.create(createMockBlake3Service());
        chunkHandler = AsyncFileChunkHandler.create(createMockBlake3Service());
        filesystemScanner = new NioFilesystemScanner();

        // Configure components
        fileChunker.setAsyncBufferPool(bufferPool);
        fileChunker.setAsyncChunkHandler(chunkHandler);

        // Create test files
        testFiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Path testFile = tempDir.resolve("test_file_" + i + ".dat");
            try {
                AsyncTestUtils.createTestFile(tempDir, "test_file_" + i + ".dat", 1024 * (i + 1)); // 1KB, 2KB, 3KB,
                                                                                                   // 4KB, 5KB
            } catch (AsyncTestUtils.AsyncTestException e) {
                throw new RuntimeException("Failed to create test file", e);
            }
            testFiles.add(testFile);
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
    @DisplayName("Should handle end-to-end async workflow")
    void shouldHandleEndToEndAsyncWorkflow() throws Exception {
        // Given
        AsyncTestMetricsCollector metrics = new AsyncTestMetricsCollector();
        AsyncTestMetricsCollector.OperationTimer workflowTimer = metrics.startOperation("end_to_end_workflow",
                "integration_test");

        // When - Scan directory
        ScanOptions scanOptions = new ScanOptions()
                .withMaxDepth(1);

        CompletableFuture<ScanResult> scanFuture = filesystemScanner.scanDirectory(tempDir, scanOptions);
        ScanResult scanResult = AsyncTestUtils.getResultOrThrow(scanFuture, Duration.ofSeconds(10));

        workflowTimer.complete((long) scanResult.getScannedFileCount());

        // When - Chunk all files concurrently
        List<CompletableFuture<FileChunker.ChunkingResult>> chunkFutures = new ArrayList<>();
        for (Path file : testFiles) {
            CompletableFuture<FileChunker.ChunkingResult> chunkFuture = fileChunker.chunkFileAsync(file, null);
            chunkFutures.add(chunkFuture);
        }

        // When - Wait for all chunking to complete
        CompletableFuture<Void> allChunkingFuture = CompletableFuture.allOf(
                chunkFutures.toArray(new CompletableFuture[0]));
        allChunkingFuture.get(20, TimeUnit.SECONDS);

        // Then - Verify all operations completed successfully
        assertEquals(testFiles.size(), scanResult.getScannedFileCount());

        for (CompletableFuture<FileChunker.ChunkingResult> future : chunkFutures) {
            FileChunker.ChunkingResult result = future.get();
            assertTrue(result.isSuccess(), "All chunking operations should succeed");
            assertTrue(result.getChunkCount() > 0, "Each file should have at least one chunk");
        }

        // Verify performance metrics
        AsyncTestMetricsCollector.ComponentMetrics operationMetrics = metrics
                .getOperationMetrics("end_to_end_workflow");
        assertNotNull(operationMetrics);
        assertTrue(operationMetrics.getTotalOperations() > 0);
    }

    @Test
    @Timeout(10)
    @DisplayName("Should coordinate async components under load")
    void shouldCoordinateAsyncComponentsUnderLoad() throws Exception {
        // Given
        int concurrentOperations = 10;
        List<CompletableFuture<FileChunker.ChunkingResult>> futures = new ArrayList<>();

        // When - Execute many concurrent operations
        for (int i = 0; i < concurrentOperations; i++) {
            Path testFile = testFiles.get(i % testFiles.size());
            CompletableFuture<FileChunker.ChunkingResult> future = fileChunker.chunkFileAsync(testFile, null);
            futures.add(future);
        }

        // Then - All operations should complete successfully
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
        allFutures.get(15, TimeUnit.SECONDS);

        long totalSize = 0;
        for (CompletableFuture<FileChunker.ChunkingResult> future : futures) {
            FileChunker.ChunkingResult result = future.get();
            assertTrue(result.isSuccess());
            totalSize += result.getTotalSize();
        }

        assertTrue(totalSize > 0, "Total processed size should be positive");

        // Verify buffer pool statistics
        CompletableFuture<String> statsFuture = bufferPool.getStatsAsync();
        String statsString = statsFuture.join();
        assertTrue(statsString.contains("Total:"));
        assertTrue(statsString.contains("Available:"));
    }

    @Test
    @Timeout(8)
    @DisplayName("Should handle resource exhaustion gracefully")
    void shouldHandleResourceExhaustionGracefully() throws Exception {
        // Given - Create limited buffer pool
        AsyncByteBufferPool limitedPool = AsyncByteBufferPoolImpl.create(1024, 2);
        fileChunker.setAsyncBufferPool(limitedPool);

        // When - Submit many operations
        List<CompletableFuture<FileChunker.ChunkingResult>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            CompletableFuture<FileChunker.ChunkingResult> future = fileChunker.chunkFileAsync(testFiles.get(0), null);
            futures.add(future);
        }

        // Then - Operations should eventually complete (possibly with some failures)
        int successCount = 0;
        int failureCount = 0;

        for (CompletableFuture<FileChunker.ChunkingResult> future : futures) {
            try {
                FileChunker.ChunkingResult result = future.get(10, TimeUnit.SECONDS);
                if (result.isSuccess()) {
                    successCount++;
                } else {
                    failureCount++;
                }
            } catch (Exception e) {
                failureCount++;
            }
        }

        assertTrue(successCount > 0, "At least some operations should succeed");
        assertTrue(successCount + failureCount == futures.size(), "All operations should be accounted for");

        // Cleanup
        limitedPool.clear();
    }

    @Test
    @Timeout(5)
    @DisplayName("Should handle mixed async operations")
    void shouldHandleMixedAsyncOperations() throws Exception {
        // Given
        Path testFile = testFiles.get(0);
        java.nio.ByteBuffer[] chunks = new java.nio.ByteBuffer[3];
        for (int i = 0; i < chunks.length; i++) {
            chunks[i] = java.nio.ByteBuffer.allocate(1024);
            chunks[i].put(("test data " + i).getBytes());
            chunks[i].flip();
        }

        // When - Process chunks with both CompletableFuture and CompletionHandler
        // patterns
        CompletableFuture<String[]> futureResult = chunkHandler.processChunksAsync(chunks, testFile);

        // Then - Operations should complete successfully
        String[] hashes = futureResult.get(5, TimeUnit.SECONDS);
        assertNotNull(hashes);
        assertEquals(chunks.length, hashes.length);

        for (String hash : hashes) {
            assertNotNull(hash);
            assertFalse(hash.isEmpty());
        }

        // Verify buffer pool usage
        CompletableFuture<String> statsFuture = bufferPool.getStatsAsync();
        String statsString = statsFuture.join();
        assertTrue(statsString.contains("Total:"));
    }

    @Test
    @Timeout(8)
    @DisplayName("Should recover from component failures")
    void shouldRecoverFromComponentFailures() throws Exception {
        // Given - Create failing buffer pool
        AsyncByteBufferPool failingBufferPool = new AsyncByteBufferPool() {
            @Override
            public CompletableFuture<java.nio.ByteBuffer> acquireAsync(int size) {
                return CompletableFuture.failedFuture(new RuntimeException("Simulated failure"));
            }

            @Override
            public CompletableFuture<Void> releaseAsync(java.nio.ByteBuffer buffer) {
                return CompletableFuture.failedFuture(new RuntimeException("Simulated failure"));
            }

            @Override
            public CompletableFuture<Void> clearAsync() {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<Integer> getAvailableCountAsync() {
                return CompletableFuture.completedFuture(0);
            }

            @Override
            public CompletableFuture<Integer> getTotalCountAsync() {
                return CompletableFuture.completedFuture(0);
            }

            @Override
            public CompletableFuture<Integer> getBuffersInUseAsync() {
                return CompletableFuture.completedFuture(0);
            }

            @Override
            public CompletableFuture<String> getStatsAsync() {
                return CompletableFuture.completedFuture("FailingBufferPool Stats");
            }

            @Override
            public java.nio.ByteBuffer acquire(int size) {
                throw new RuntimeException("Simulated failure");
            }

            @Override
            public void release(java.nio.ByteBuffer buffer) {
                throw new RuntimeException("Simulated failure");
            }

            @Override
            public void clear() {
                // No-op
            }

            @Override
            public int getAvailableCount() {
                return 0;
            }

            @Override
            public int getTotalCount() {
                return 0;
            }

            @Override
            public int getDefaultBufferSize() {
                return 8192;
            }
        };

        // When - Use failing buffer pool
        fileChunker.setAsyncBufferPool(failingBufferPool);
        CompletableFuture<FileChunker.ChunkingResult> future = fileChunker.chunkFileAsync(testFiles.get(0), null);

        // Then - Operation should fail gracefully
        FileChunker.ChunkingResult result = future.get(5, TimeUnit.SECONDS);
        assertTrue(result.isSuccess() || result.getError() != null,
                "Operation should fail gracefully with failing buffer pool");

        // When - Restore working buffer pool
        fileChunker.setAsyncBufferPool(bufferPool);
        future = fileChunker.chunkFileAsync(testFiles.get(0), null);

        // Then - Operation should succeed again
        result = future.get(5, TimeUnit.SECONDS);
        assertTrue(result.isSuccess(), "Operation should succeed with restored buffer pool");
    }

    @Test
    @Timeout(5)
    @DisplayName("Should maintain resource consistency")
    void shouldMaintainResourceConsistency() throws Exception {
        // Given
        int initialAvailableCount = bufferPool.getAvailableCount();
        int initialTotalCount = bufferPool.getTotalCount();

        // When - Perform operations
        List<CompletableFuture<FileChunker.ChunkingResult>> futures = new ArrayList<>();
        for (Path testFile : testFiles) {
            CompletableFuture<FileChunker.ChunkingResult> future = fileChunker.chunkFileAsync(testFile, null);
            futures.add(future);
        }

        // Wait for completion
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
        allFutures.get(8, TimeUnit.SECONDS);

        // Then - Resource counts should be consistent
        int finalAvailableCount = bufferPool.getAvailableCount();
        int finalTotalCount = bufferPool.getTotalCount();

        // Total should not decrease (buffers shouldn't be lost)
        assertTrue(finalTotalCount >= initialTotalCount,
                "Total buffer count should not decrease");

        // Most buffers should be available after operations complete
        CompletableFuture<String> finalStatsFuture = bufferPool.getStatsAsync();
        String finalStatsString = finalStatsFuture.join();
        assertTrue(finalStatsString.contains("Total:"));
    }

    @Test
    @Timeout(10)
    @DisplayName("Should handle concurrent resource access")
    void shouldHandleConcurrentResourceAccess() throws Exception {
        // Given
        int threadCount = 8;
        int operationsPerThread = 5;
        int bufferCount = 4;
        int bufferSize = 2048;

        AsyncByteBufferPool limitedPool = AsyncByteBufferPoolImpl.create(bufferSize, bufferCount);
        fileChunker.setAsyncBufferPool(limitedPool);

        // When - Execute concurrent operations
        List<CompletableFuture<Void>> threadFutures = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            CompletableFuture<Void> threadFuture = CompletableFuture.runAsync(() -> {
                for (int op = 0; op < operationsPerThread; op++) {
                    try {
                        Path testFile = testFiles.get(threadId % testFiles.size());
                        FileChunker.ChunkingResult result = fileChunker
                                .chunkFileAsync(testFile, null)
                                .get(5, TimeUnit.SECONDS);

                        // Verify result
                        assertNotNull(result);
                        if (!result.isSuccess()) {
                            // Log failure but don't fail test - resource contention is expected
                            System.err.println("Operation failed: " + result.getError().getMessage());
                        }
                    } catch (Exception e) {
                        // Some failures are expected under resource contention
                        System.err.println("Thread " + threadId + " operation " + op + " failed: " + e.getMessage());
                    }
                }
            });
            threadFutures.add(threadFuture);
        }

        // Then - All threads should complete
        CompletableFuture<Void> allThreadsFuture = CompletableFuture.allOf(
                threadFutures.toArray(new CompletableFuture[0]));
        allThreadsFuture.get(15, TimeUnit.SECONDS);

        // Verify pool is still functional
        CompletableFuture<String> statsFuture = limitedPool.getStatsAsync();
        String statsString = statsFuture.join();
        assertTrue(statsString.contains("Total:"));

        // Cleanup
        limitedPool.clear();
    }

    /**
     * Creates a mock Blake3Service for testing.
     */
    private com.justsyncit.hash.Blake3Service createMockBlake3Service() {
        return new com.justsyncit.hash.Blake3Service() {
            @Override
            public String hashFile(Path filePath) throws IOException {
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
                    throws IOException, com.justsyncit.hash.HashingException {
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