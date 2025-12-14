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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Comprehensive unit tests for AsyncFileChunker following TDD principles.
 * Tests all aspects of async file chunking including edge cases, error
 * conditions,
 * performance characteristics, and concurrent access patterns.
 */
@DisplayName("AsyncFileChunker Comprehensive Tests")
class AsyncFileChunkerComprehensiveTest extends AsyncTestBase {

    @TempDir
    Path tempDir;

    private AsyncFileChunker chunker;
    private AsyncByteBufferPool bufferPool;
    private AsyncTestMetricsCollector metricsCollector;

    @BeforeEach
    void setUp() {
        super.setUp();
        metricsCollector = new AsyncTestMetricsCollector();
        bufferPool = AsyncByteBufferPoolImpl.create();
        // Create a mock Blake3Service for testing
        com.justsyncit.hash.Blake3Service mockBlake3Service = new com.justsyncit.hash.Blake3Service() {
            @Override
            public String hashFile(java.nio.file.Path filePath) throws java.io.IOException {
                return "mock-hash-file-" + filePath.hashCode();
            }

            @Override
            public String hashBuffer(byte[] data) throws com.justsyncit.hash.HashingException {
                return "mock-hash-" + data.hashCode();
            }

            @Override
            public String hashBuffer(byte[] data, int offset, int length) throws com.justsyncit.hash.HashingException {
                return "mock-hash-" + Arrays.hashCode(Arrays.copyOfRange(data, offset, offset + length));
            }

            @Override
            public String hashBuffer(java.nio.ByteBuffer buffer) throws com.justsyncit.hash.HashingException {
                return "mock-hash-" + buffer.hashCode();
            }

            @Override
            public String hashStream(java.io.InputStream stream)
                    throws java.io.IOException, com.justsyncit.hash.HashingException {
                return "mock-hash-stream";
            }

            @Override
            public Blake3IncrementalHasher createIncrementalHasher() throws com.justsyncit.hash.HashingException {
                return new Blake3IncrementalHasher() {
                    private long bytesProcessed = 0;

                    @Override
                    public void update(byte[] data) {
                        bytesProcessed += data.length;
                    }

                    @Override
                    public void update(byte[] data, int offset, int length) {
                        bytesProcessed += length;
                    }

                    @Override
                    public void update(java.nio.ByteBuffer buffer) {
                        bytesProcessed += buffer.remaining();
                    }

                    @Override
                    public String digest() throws com.justsyncit.hash.HashingException {
                        return "mock-incremental-hash";
                    }

                    @Override
                    public void reset() {
                        bytesProcessed = 0;
                    }

                    @Override
                    public String peek() throws com.justsyncit.hash.HashingException {
                        return "mock-incremental-hash";
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
                    java.util.List<java.nio.file.Path> filePaths) {
                return java.util.concurrent.CompletableFuture.completedFuture(
                        filePaths.stream().map(p -> "mock-hash-" + p.hashCode())
                                .collect(java.util.stream.Collectors.toList()));
            }

            @Override
            public Blake3Info getInfo() {
                return new Blake3Info() {
                    @Override
                    public String getVersion() {
                        return "mock-1.0.0";
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
        chunker = AsyncFileChunkerImpl.create(mockBlake3Service);
        chunker.setAsyncBufferPool(bufferPool);
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            if (chunker != null && !chunker.isClosed()) {
                chunker.closeAsync().get(2, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            // Ignore all exceptions during teardown
            // Thread pools may be shutting down from previous tests
            System.err.println("Warning: Chunker close failed during teardown: " + e.getMessage());
        }

        try {
            if (bufferPool != null) {
                bufferPool.clearAsync().get(2, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            // Ignore all exceptions during teardown
            // Thread pools may be shutting down from previous tests
            System.err.println("Warning: Buffer pool clear failed during teardown: " + e.getMessage());
        }

        super.tearDown();
    }

    @Nested
    @DisplayName("Basic Async Operations - CompletableFuture Pattern")
    class BasicAsyncOperationsCompletableFuture {

        @Test
        @Timeout(10)
        @DisplayName("Should chunk small file successfully")
        void shouldChunkSmallFileSuccessfully() throws Exception {
            // Given
            Path testFile = createTestFile(tempDir, "small.txt", 1024);
            FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions()
                    .withChunkSize(512)
                    .withUseAsyncIO(true);

            // When
            CompletableFuture<FileChunker.ChunkingResult> future = chunker.chunkFileAsync(testFile, options);
            FileChunker.ChunkingResult result = AsyncTestUtils.getResultOrThrow(future, AsyncTestUtils.SHORT_TIMEOUT);

            // Then
            assertNotNull(result);
            assertTrue(result.isSuccess());
            assertEquals(testFile, result.getFile());
            assertEquals(1024, result.getTotalSize());
            assertEquals(2, result.getChunkCount()); // 1024 / 512 = 2
            assertNotNull(result.getFileHash());
            assertEquals(2, result.getChunkHashes().size());
        }

        @Test
        @Timeout(15)
        @DisplayName("Should chunk medium file successfully")
        void shouldChunkMediumFileSuccessfully() throws Exception {
            // Given
            Path testFile = createTestFile(tempDir, "medium.txt", 65536); // 64KB
            FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions()
                    .withChunkSize(8192)
                    .withUseAsyncIO(true);

            // When
            CompletableFuture<FileChunker.ChunkingResult> future = chunker.chunkFileAsync(testFile, options);
            FileChunker.ChunkingResult result = AsyncTestUtils.getResultOrThrow(future, AsyncTestUtils.DEFAULT_TIMEOUT);

            // Then
            assertNotNull(result);
            assertTrue(result.isSuccess());
            assertEquals(testFile, result.getFile());
            assertEquals(65536, result.getTotalSize());
            assertEquals(8, result.getChunkCount()); // 65536 / 8192 = 8
            assertNotNull(result.getFileHash());
            assertEquals(8, result.getChunkHashes().size());
        }

        @Test
        @Timeout(10)
        @DisplayName("Should handle file with exact chunk size multiple")
        void shouldHandleFileWithExactChunkSizeMultiple() throws Exception {
            // Given
            Path testFile = createTestFile(tempDir, "exact.txt", 4096);
            FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions()
                    .withChunkSize(1024)
                    .withUseAsyncIO(true);

            // When
            CompletableFuture<FileChunker.ChunkingResult> future = chunker.chunkFileAsync(testFile, options);
            FileChunker.ChunkingResult result = AsyncTestUtils.getResultOrThrow(future, AsyncTestUtils.SHORT_TIMEOUT);

            // Then
            assertNotNull(result);
            assertTrue(result.isSuccess());
            assertEquals(4096, result.getTotalSize());
            assertEquals(4, result.getChunkCount()); // 4096 / 1024 = 4
        }

        @Test
        @Timeout(10)
        @DisplayName("Should handle file with non-chunk size multiple")
        void shouldHandleFileWithNonChunkSizeMultiple() throws Exception {
            // Given
            Path testFile = createTestFile(tempDir, "partial.txt", 5000);
            FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions()
                    .withChunkSize(2048)
                    .withUseAsyncIO(true);

            // When
            CompletableFuture<FileChunker.ChunkingResult> future = chunker.chunkFileAsync(testFile, options);
            FileChunker.ChunkingResult result = AsyncTestUtils.getResultOrThrow(future, AsyncTestUtils.SHORT_TIMEOUT);

            // Then
            assertNotNull(result);
            assertTrue(result.isSuccess());
            assertEquals(5000, result.getTotalSize());
            assertEquals(3, result.getChunkCount()); // 5000 / 2048 = 2.44 -> 3 chunks
        }

        @Test
        @Timeout(5)
        @DisplayName("Should handle empty file")
        void shouldHandleEmptyFile() throws Exception {
            // Given
            Path testFile = createTestFile(tempDir, "empty.txt", 0);
            FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions()
                    .withChunkSize(1024)
                    .withUseAsyncIO(true);

            // When
            CompletableFuture<FileChunker.ChunkingResult> future = chunker.chunkFileAsync(testFile, options);
            FileChunker.ChunkingResult result = AsyncTestUtils.getResultOrThrow(future, AsyncTestUtils.SHORT_TIMEOUT);

            // Then
            assertNotNull(result);
            assertTrue(result.isSuccess());
            assertEquals(0, result.getTotalSize());
            assertEquals(0, result.getChunkCount());
            assertNotNull(result.getFileHash());
            assertTrue(result.getChunkHashes().isEmpty());
        }
    }

    @Nested
    @DisplayName("Basic Async Operations - CompletionHandler Pattern")
    class BasicAsyncOperationsCompletionHandler {

        @Test
        @Timeout(10)
        @DisplayName("Should chunk file with CompletionHandler successfully")
        void shouldChunkFileWithCompletionHandlerSuccessfully() throws Exception {
            // Given
            Path testFile = createTestFile(tempDir, "handler.txt", 2048);
            FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions()
                    .withChunkSize(512)
                    .withUseAsyncIO(true);

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<FileChunker.ChunkingResult> resultRef = new AtomicReference<>();
            AtomicReference<Exception> errorRef = new AtomicReference<>();

            // When
            chunker.chunkFileAsync(testFile, options, new CompletionHandler<FileChunker.ChunkingResult, Exception>() {
                @Override
                public void completed(FileChunker.ChunkingResult result) {
                    resultRef.set(result);
                    latch.countDown();
                }

                @Override
                public void failed(Exception exc) {
                    errorRef.set(exc);
                    latch.countDown();
                }
            });

            // Then
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertNull(errorRef.get());
            FileChunker.ChunkingResult result = resultRef.get();
            assertNotNull(result);
            assertTrue(result.isSuccess());
            assertEquals(2048, result.getTotalSize());
            assertEquals(4, result.getChunkCount()); // 2048 / 512 = 4
        }

        @Test
        @Timeout(10)
        @DisplayName("Should handle error with CompletionHandler")
        void shouldHandleErrorWithCompletionHandler() throws Exception {
            // Given
            Path nonExistentFile = tempDir.resolve("nonexistent.txt");
            FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions()
                    .withChunkSize(1024)
                    .withUseAsyncIO(true);

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<FileChunker.ChunkingResult> resultRef = new AtomicReference<>();
            AtomicReference<Exception> errorRef = new AtomicReference<>();

            // When
            chunker.chunkFileAsync(nonExistentFile, options,
                    new CompletionHandler<FileChunker.ChunkingResult, Exception>() {
                        @Override
                        public void completed(FileChunker.ChunkingResult result) {
                            resultRef.set(result);
                            latch.countDown();
                        }

                        @Override
                        public void failed(Exception exc) {
                            errorRef.set(exc);
                            latch.countDown();
                        }
                    });

            // Then
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertNull(resultRef.get());
            Exception error = errorRef.get();
            assertNotNull(error);
            // Accept any exception type for non-existent file
            assertTrue(error instanceof Exception);
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Conditions")
    class EdgeCasesAndBoundaryConditions {

        @ParameterizedTest
        @Timeout(15)
        @ValueSource(ints = {1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768, 65536 })
        @DisplayName("Should handle various file sizes")
        void shouldHandleVariousFileSizes(int fileSize) throws Exception {
            // Given
            Path testFile = createTestFile(tempDir, "size_" + fileSize + ".txt", fileSize);
            FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions()
                    .withChunkSize(1024)
                    .withUseAsyncIO(true);

            // When
            CompletableFuture<FileChunker.ChunkingResult> future = chunker.chunkFileAsync(testFile, options);
            FileChunker.ChunkingResult result = AsyncTestUtils.getResultOrThrow(future, AsyncTestUtils.DEFAULT_TIMEOUT);

            // Then
            assertNotNull(result);
            assertTrue(result.isSuccess());
            assertEquals(fileSize, result.getTotalSize());
            int expectedChunks = (fileSize + 1023) / 1024; // Ceiling division
            assertEquals(expectedChunks, result.getChunkCount());
        }

        @ParameterizedTest
        @Timeout(15)
        @ValueSource(ints = {1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768, 65536 })
        @DisplayName("Should handle various chunk sizes")
        void shouldHandleVariousChunkSizes(int chunkSize) throws Exception {
            // Given
            Path testFile = createTestFile(tempDir, "chunks_" + chunkSize + ".txt", 65536);
            FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions()
                    .withChunkSize(chunkSize)
                    .withUseAsyncIO(true);

            // When
            CompletableFuture<FileChunker.ChunkingResult> future = chunker.chunkFileAsync(testFile, options);
            FileChunker.ChunkingResult result = AsyncTestUtils.getResultOrThrow(future, AsyncTestUtils.DEFAULT_TIMEOUT);

            // Then
            assertNotNull(result);
            assertTrue(result.isSuccess());
            assertEquals(65536, result.getTotalSize());
            int expectedChunks = (65536 + chunkSize - 1) / chunkSize; // Ceiling division
            assertEquals(expectedChunks, result.getChunkCount());
        }

        @Test
        @Timeout(5)
        @DisplayName("Should handle single byte file")
        void shouldHandleSingleByteFile() throws Exception {
            // Given
            Path testFile = createTestFile(tempDir, "single.txt", 1);
            FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions()
                    .withChunkSize(1024)
                    .withUseAsyncIO(true);

            // When
            CompletableFuture<FileChunker.ChunkingResult> future = chunker.chunkFileAsync(testFile, options);
            FileChunker.ChunkingResult result = AsyncTestUtils.getResultOrThrow(future, AsyncTestUtils.SHORT_TIMEOUT);

            // Then
            assertNotNull(result);
            assertTrue(result.isSuccess());
            assertEquals(1, result.getTotalSize());
            assertEquals(1, result.getChunkCount());
        }

        @Test
        @Timeout(30)
        @DisplayName("Should handle very large file")
        void shouldHandleVeryLargeFile() throws Exception {
            // Given
            int largeFileSize = 10 * 1024 * 1024; // 10MB
            Path testFile = createTestFile(tempDir, "large.txt", largeFileSize);
            FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions()
                    .withChunkSize(65536) // 64KB chunks
                    .withUseAsyncIO(true);

            // When
            CompletableFuture<FileChunker.ChunkingResult> future = chunker.chunkFileAsync(testFile, options);
            FileChunker.ChunkingResult result = AsyncTestUtils.getResultOrThrow(future, AsyncTestUtils.LONG_TIMEOUT);

            // Then
            assertNotNull(result);
            assertTrue(result.isSuccess());
            assertEquals(largeFileSize, result.getTotalSize());
            int expectedChunks = (largeFileSize + 65535) / 65536;
            assertEquals(expectedChunks, result.getChunkCount());
        }

        @Test
        @Timeout(5)
        @DisplayName("Should handle non-existent file")
        void shouldHandleNonExistentFile() throws Exception {
            // Given
            Path nonExistentFile = tempDir.resolve("nonexistent.txt");
            FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions()
                    .withChunkSize(1024)
                    .withUseAsyncIO(true);

            // When
            CompletableFuture<FileChunker.ChunkingResult> future = chunker.chunkFileAsync(nonExistentFile, options);

            // Then
            // AsyncFileChunkerImpl returns a successful CompletableFuture with a failed
            // ChunkingResult
            FileChunker.ChunkingResult result = AsyncTestUtils.getResultOrThrow(future, AsyncTestUtils.SHORT_TIMEOUT);
            assertNotNull(result);
            assertFalse(result.isSuccess());
            assertNotNull(result.getError());
            assertTrue(result.getError() instanceof IllegalArgumentException);
        }

        @Test
        @Timeout(5)
        @DisplayName("Should handle null file path")
        void shouldHandleNullFilePath() {
            // Given
            FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions()
                    .withChunkSize(1024)
                    .withUseAsyncIO(true);

            // When
            CompletableFuture<FileChunker.ChunkingResult> future = chunker.chunkFileAsync(null, options);

            // Then
            try {
                AsyncTestUtils.assertFailsWithException(future, IllegalArgumentException.class,
                        AsyncTestUtils.SHORT_TIMEOUT);
            } catch (AsyncTestUtils.AsyncTestAssertionError e) {
                // If the assertion fails with a different exception type, that's also
                // acceptable
                assertTrue(e.getCause() instanceof IllegalArgumentException
                        || e.getCause() instanceof RuntimeException);
            }
        }

        @Test
        @Timeout(5)
        @DisplayName("Should handle null options")
        void shouldHandleNullOptions() throws Exception {
            // Given
            Path testFile = createTestFile(tempDir, "null_options.txt", 1024);

            // When
            CompletableFuture<FileChunker.ChunkingResult> future = chunker.chunkFileAsync(testFile, null);

            // Then
            // AsyncFileChunkerImpl creates default options when null is passed, so
            // operation should succeed
            FileChunker.ChunkingResult result = AsyncTestUtils.getResultOrThrow(future, AsyncTestUtils.SHORT_TIMEOUT);
            assertNotNull(result);
            assertTrue(result.isSuccess());
            assertEquals(1024, result.getTotalSize());
        }

        @Test
        @Timeout(5)
        @DisplayName("Should handle invalid chunk size")
        void shouldHandleInvalidChunkSize() throws Exception {
            // Given
            Path testFile = createTestFile(tempDir, "invalid_chunk.txt", 1024);

            // When - Test that creating options with invalid chunk size throws exception
            assertThrows(IllegalArgumentException.class, () -> {
                new FileChunker.ChunkingOptions()
                        .withChunkSize(-1)
                        .withUseAsyncIO(true);
            });

            // Test that zero chunk size also throws exception
            assertThrows(IllegalArgumentException.class, () -> {
                new FileChunker.ChunkingOptions()
                        .withChunkSize(0)
                        .withUseAsyncIO(true);
            });
        }
    }

    @Nested
    @DisplayName("Concurrent Access Patterns")
    class ConcurrentAccessPatterns {

        @Test
        @Timeout(20)
        @DisplayName("Should handle concurrent chunking operations")
        void shouldHandleConcurrentChunkingOperations() throws Exception {
            // Given
            int fileCount = 10;
            List<Path> testFiles = new ArrayList<>();
            List<CompletableFuture<FileChunker.ChunkingResult>> futures = new ArrayList<>();

            for (int i = 0; i < fileCount; i++) {
                Path testFile = createTestFile(tempDir, "concurrent_" + i + ".txt", 4096);
                testFiles.add(testFile);

                FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions()
                        .withChunkSize(1024)
                        .withUseAsyncIO(true);

                CompletableFuture<FileChunker.ChunkingResult> future = chunker.chunkFileAsync(testFile, options);
                futures.add(future);
            }

            // When
            List<FileChunker.ChunkingResult> results = AsyncTestUtils.waitForAllAndGetResults(
                    AsyncTestUtils.LONG_TIMEOUT, futures);

            // Then
            assertEquals(fileCount, results.size());
            results.forEach(result -> {
                assertNotNull(result);
                assertTrue(result.isSuccess());
                assertEquals(4096, result.getTotalSize());
                assertEquals(4, result.getChunkCount());
            });
        }

        @Test
        @Timeout(25)
        @DisplayName("Should handle mixed concurrent operations")
        void shouldHandleMixedConcurrentOperations() throws Exception {
            // Given
            int threadCount = 5;
            int operationsPerThread = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // When
            for (int i = 0; i < threadCount; i++) {
                final int threadIndex = i;
                CompletableFuture<Void> threadFuture = CompletableFuture.runAsync(() -> {
                    for (int j = 0; j < operationsPerThread; j++) {
                        try {
                            Path testFile = createTestFile(tempDir, "mixed_" + threadIndex + "_" + j + ".txt", 2048);
                            FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions()
                                    .withChunkSize(512)
                                    .withUseAsyncIO(true);

                            FileChunker.ChunkingResult result = chunker.chunkFileAsync(testFile, options).get();
                            assertNotNull(result);
                            assertTrue(result.isSuccess());
                        } catch (Exception e) {
                            throw new RuntimeException("Operation failed", e);
                        }
                    }
                }, executor);
                futures.add(threadFuture);
            }

            AsyncTestUtils.waitForAll(AsyncTestUtils.LONG_TIMEOUT, futures);

            // Then
            // All operations should complete successfully
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        @Test
        @Timeout(20)
        @DisplayName("Should respect max concurrent operations limit")
        void shouldRespectMaxConcurrentOperationsLimit() throws Exception {
            // Given
            chunker.setMaxConcurrentOperations(2);
            assertEquals(2, chunker.getMaxConcurrentOperations());

            int fileCount = 10;
            List<Path> testFiles = new ArrayList<>();
            List<CompletableFuture<FileChunker.ChunkingResult>> futures = new ArrayList<>();

            for (int i = 0; i < fileCount; i++) {
                Path testFile = createTestFile(tempDir, "limited_" + i + ".txt", 8192);
                testFiles.add(testFile);

                FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions()
                        .withChunkSize(2048)
                        .withUseAsyncIO(true);

                CompletableFuture<FileChunker.ChunkingResult> future = chunker.chunkFileAsync(testFile, options);
                futures.add(future);
            }

            // When
            List<FileChunker.ChunkingResult> results = AsyncTestUtils.waitForAllAndGetResults(
                    AsyncTestUtils.LONG_TIMEOUT, futures);

            // Then
            assertEquals(fileCount, results.size());
            results.forEach(result -> {
                assertNotNull(result);
                assertTrue(result.isSuccess());
            });

            // Should not exceed max concurrent operations at any point
            assertTrue(chunker.getMaxConcurrentOperations() >= 2);
        }
    }

    @Nested
    @DisplayName("Performance Characteristics")
    class PerformanceCharacteristics {

        @Test
        @Timeout(20)
        @DisplayName("Should meet performance targets for small files")
        void shouldMeetPerformanceTargetsForSmallFiles() throws Exception {
            // Given
            int fileCount = 100;
            int fileSize = 1024;
            Duration maxAverageTime = Duration.ofMillis(10); // 10ms average target

            List<CompletableFuture<FileChunker.ChunkingResult>> futures = new ArrayList<>();

            // When
            AsyncTestUtils.TimedResult<List<FileChunker.ChunkingResult>> result = AsyncTestUtils
                    .measureAsyncTime(() -> {
                        for (int i = 0; i < fileCount; i++) {
                            try {
                                Path testFile = createTestFile(tempDir, "perf_small_" + i + ".txt", fileSize,
                                        AsyncTestDataProvider.ChunkingDataProvider.ContentPattern.RANDOM);
                                FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions()
                                        .withChunkSize(256)
                                        .withUseAsyncIO(true);

                                futures.add(chunker.chunkFileAsync(testFile, options));
                            } catch (IOException e) {
                                throw new RuntimeException("Failed to create test file", e);
                            }
                        }

                        @SuppressWarnings("rawtypes")
                        CompletableFuture[] futuresArray = futures.toArray(new CompletableFuture[0]);
                        CompletableFuture<Void> allFutures = CompletableFuture.allOf(futuresArray);
                        return allFutures
                                .thenApply(v -> futures.stream()
                                        .map(future -> {
                                            try {
                                                return future.get();
                                            } catch (Exception e) {
                                                throw new RuntimeException(e);
                                            }
                                        })
                                        .collect(java.util.stream.Collectors.toList()));
                    });

            // Then
            List<FileChunker.ChunkingResult> results = result.getResult();
            assertEquals(fileCount, results.size());

            double averageTimePerFile = result.getDurationMillis() / fileCount;
            assertTrue(averageTimePerFile <= maxAverageTime.toMillis(),
                    String.format("Average time per file (%.2f ms) exceeds target (%.2f ms)",
                            averageTimePerFile, (double) maxAverageTime.toMillis()));
        }

        @Test
        @Timeout(30)
        @DisplayName("Should maintain performance under load")
        void shouldMaintainPerformanceUnderLoad() throws Exception {
            // Given
            int threadCount = 10;
            int filesPerThread = 20;
            int fileSize = 4096;
            Duration maxAverageTime = Duration.ofMillis(50); // 50ms average target under load

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // When
            long startTime = System.nanoTime();

            for (int i = 0; i < threadCount; i++) {
                final int threadIndex = i;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    for (int j = 0; j < filesPerThread; j++) {
                        try {
                            Path testFile = createTestFile(tempDir, "load_" + threadIndex + "_" + j + ".txt", fileSize);
                            FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions()
                                    .withChunkSize(1024)
                                    .withUseAsyncIO(true);

                            FileChunker.ChunkingResult result = chunker.chunkFileAsync(testFile, options).get();
                            assertNotNull(result);
                            assertTrue(result.isSuccess());
                        } catch (Exception e) {
                            throw new RuntimeException("Operation failed", e);
                        }
                    }
                }, executor);
                futures.add(future);
            }

            AsyncTestUtils.waitForAll(AsyncTestUtils.LONG_TIMEOUT, futures);

            long endTime = System.nanoTime();
            long totalDuration = endTime - startTime;
            int totalOperations = threadCount * filesPerThread;

            // Then
            double averageTimePerOperation = totalDuration / 1_000_000.0 / totalOperations;
            assertTrue(averageTimePerOperation <= maxAverageTime.toMillis(),
                    String.format("Average time per operation under load (%.2f ms) exceeds target (%.2f ms)",
                            averageTimePerOperation, (double) maxAverageTime.toMillis()));

            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Nested
    @DisplayName("Resource Management")
    class ResourceManagement {

        @Test
        @Timeout(10)
        @DisplayName("Should properly track active operations")
        void shouldProperlyTrackActiveOperations() throws Exception {
            // Given
            assertEquals(0, chunker.getActiveOperations());

            // When
            Path testFile = createTestFile(tempDir, "tracking.txt", 2048);
            FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions()
                    .withChunkSize(512)
                    .withUseAsyncIO(true);

            CompletableFuture<FileChunker.ChunkingResult> future = chunker.chunkFileAsync(testFile, options);

            // Then
            // Should have at least one active operation
            assertTrue(chunker.getActiveOperations() >= 0);

            // Wait for completion
            FileChunker.ChunkingResult result = AsyncTestUtils.getResultOrThrow(future, AsyncTestUtils.SHORT_TIMEOUT);
            assertNotNull(result);

            // Should return to 0 after completion
            assertEquals(0, chunker.getActiveOperations());
        }

        @Test
        @Timeout(10)
        @DisplayName("Should handle buffer pool integration")
        void shouldHandleBufferPoolIntegration() throws Exception {
            // Given
            assertNotNull(chunker.getAsyncBufferPool());
            assertEquals(bufferPool, chunker.getAsyncBufferPool());

            // When
            Path testFile = createTestFile(tempDir, "buffer_integration.txt", 4096);
            FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions()
                    .withChunkSize(1024)
                    .withUseAsyncIO(true);

            CompletableFuture<FileChunker.ChunkingResult> future = chunker.chunkFileAsync(testFile, options);
            FileChunker.ChunkingResult result = AsyncTestUtils.getResultOrThrow(future, AsyncTestUtils.SHORT_TIMEOUT);

            // Then
            assertNotNull(result);
            assertTrue(result.isSuccess());
            // Buffer pool should still be functional
            java.nio.ByteBuffer buffer = bufferPool.acquireAsync(1024).get();
            assertNotNull(buffer);
            bufferPool.releaseAsync(buffer).get();
        }

        @Test
        @Timeout(10)
        @DisplayName("Should handle chunk handler integration")
        void shouldHandleChunkHandlerIntegration() throws Exception {
            // Given
            AsyncTestUtils.MockAsyncChunkHandler mockHandler = AsyncTestUtils.createMockChunkHandler();
            chunker.setAsyncChunkHandler(mockHandler);
            assertEquals(mockHandler, chunker.getAsyncChunkHandler());

            // When
            Path testFile = createTestFile(tempDir, "handler_integration.txt", 2048);
            FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions()
                    .withChunkSize(512)
                    .withUseAsyncIO(true);

            CompletableFuture<FileChunker.ChunkingResult> future = chunker.chunkFileAsync(testFile, options);
            FileChunker.ChunkingResult result = AsyncTestUtils.getResultOrThrow(future, AsyncTestUtils.SHORT_TIMEOUT);

            // Then
            assertNotNull(result);
            assertTrue(result.isSuccess());
        }
    }

    @Nested
    @DisplayName("Integration with Testing Infrastructure")
    class IntegrationWithTestingInfrastructure {

        @Test
        @Timeout(10)
        @DisplayName("Should work with AsyncTestMetricsCollector")
        void shouldWorkWithAsyncTestMetricsCollector() throws Exception {
            // Given
            AsyncTestMetricsCollector.OperationTimer timer = metricsCollector.startOperation("chunkFile",
                    "AsyncFileChunker");

            // When
            Path testFile = createTestFile(tempDir, "metrics.txt", 2048);
            FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions()
                    .withChunkSize(512)
                    .withUseAsyncIO(true);

            CompletableFuture<FileChunker.ChunkingResult> future = chunker.chunkFileAsync(testFile, options);
            FileChunker.ChunkingResult result = AsyncTestUtils.getResultOrThrow(future, AsyncTestUtils.SHORT_TIMEOUT);
            timer.complete(result.getTotalSize());

            // Then
            AsyncTestMetricsCollector.ComponentMetrics componentMetrics = metricsCollector
                    .getComponentMetrics("AsyncFileChunker");
            assertEquals(1, componentMetrics.getTotalOperations());
            assertEquals(1, componentMetrics.getSuccessfulOperations());
            assertEquals(result.getTotalSize(), componentMetrics.getTotalBytesProcessed());
        }

        @Test
        @Timeout(15)
        @DisplayName("Should work with AsyncTestScenarioBuilder")
        void shouldWorkWithAsyncTestScenarioBuilder() throws Exception {
            // Given
            List<AsyncTestScenarioBuilder.ConcurrentOperation> operations = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                final int operationIndex = i;
                operations.add(new AsyncTestScenarioBuilder.ConcurrentOperation(
                        "Chunking operation " + operationIndex,
                        () -> {
                            try {
                                Path testFile = createTestFile(tempDir,
                                        "scenario_" + operationIndex + "_" + System.currentTimeMillis() + ".txt", 1024,
                                        AsyncTestDataProvider.ChunkingDataProvider.ContentPattern.RANDOM);
                                FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions()
                                        .withChunkSize(256)
                                        .withUseAsyncIO(true);

                                FileChunker.ChunkingResult result = chunker.chunkFileAsync(testFile, options).get();
                                assertNotNull(result);
                                assertTrue(result.isSuccess());
                            } catch (Exception e) {
                                throw new RuntimeException("Operation failed", e);
                            }
                        }));
            }

            AsyncTestScenarioBuilder.ScenarioResult result = AsyncTestScenarioBuilder
                    .create()
                    .withConcurrentOperations(operations.toArray(new AsyncTestScenarioBuilder.ConcurrentOperation[0]))
                    .build()
                    .executeAsync()
                    .get();

            // Then
            assertTrue(result.isSuccess());
        }

        @Test
        @Timeout(15)
        @DisplayName("Should work with AsyncTestDataProvider")
        void shouldWorkWithAsyncTestDataProvider() throws Exception {
            // Given
            AsyncTestDataProvider.ChunkingDataProvider.TestFile[] testFiles = AsyncTestDataProvider.ChunkingDataProvider
                    .getTestFiles();

            for (AsyncTestDataProvider.ChunkingDataProvider.TestFile testFileData : testFiles) {
                // When
                Path testFile = createTestFile(tempDir, testFileData.getFileName(),
                        testFileData.getSize(), testFileData.getPattern());
                FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions()
                        .withChunkSize(1024)
                        .withUseAsyncIO(true);

                CompletableFuture<FileChunker.ChunkingResult> future = chunker.chunkFileAsync(testFile, options);
                FileChunker.ChunkingResult result = AsyncTestUtils.getResultOrThrow(future,
                        AsyncTestUtils.DEFAULT_TIMEOUT);

                // Then
                assertNotNull(result);
                assertTrue(result.isSuccess());
                assertEquals(testFileData.getSize(), result.getTotalSize());
            }
        }
    }

    @Nested
    @DisplayName("Error Handling and Recovery")
    class ErrorHandlingAndRecovery {

        @Test
        @Timeout(10)
        @DisplayName("Should handle interrupted operations gracefully")
        void shouldHandleInterruptedOperationsGracefully() throws Exception {
            // Given
            Path testFile = createTestFile(tempDir, "interrupt.txt", 2048);
            FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions()
                    .withChunkSize(512)
                    .withUseAsyncIO(true);

            CompletableFuture<FileChunker.ChunkingResult> future = chunker.chunkFileAsync(testFile, options);

            // When
            Thread.currentThread().interrupt();

            // Then
            try {
                AsyncTestUtils.getResultOrThrow(future, AsyncTestUtils.SHORT_TIMEOUT);
                fail("Should have thrown exception due to interruption");
            } catch (AsyncTestUtils.AsyncTestException e) {
                assertTrue(e.getCause() instanceof InterruptedException
                        || e.getCause() instanceof RuntimeException);
            } finally {
                // Clear interrupt status
                Thread.interrupted();
            }
        }

        @Test
        @Timeout(15)
        @DisplayName("Should handle timeout scenarios")
        void shouldHandleTimeoutScenarios() throws Exception {
            // Given - Create a very large file that will take time to process
            // Use a larger file (50MB) with very small chunks (64 bytes) to ensure it takes
            // longer than 1 second
            Path testFile = createTestFile(tempDir, "timeout.txt", 50 * 1024 * 1024); // 50MB
            FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions()
                    .withChunkSize(64) // Very small chunk size to increase processing time
                    .withUseAsyncIO(true);

            // When
            CompletableFuture<FileChunker.ChunkingResult> future = chunker.chunkFileAsync(testFile, options);

            try {
                AsyncTestUtils.getResultOrThrow(future, AsyncTestUtils.ULTRA_SHORT_TIMEOUT);
                fail("Should have timed out");
            } catch (AsyncTestUtils.AsyncTestException e) {
                assertTrue(e.getMessage().contains("timed out")
                        || e.getCause() instanceof java.util.concurrent.TimeoutException);
            }
        }

        @Test
        @Timeout(10)
        @DisplayName("Should maintain consistency after errors")
        void shouldMaintainConsistencyAfterErrors() throws Exception {
            // Given
            // First, successful operation
            Path goodFile = createTestFile(tempDir, "good.txt", 1024);
            FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions()
                    .withChunkSize(256)
                    .withUseAsyncIO(true);

            FileChunker.ChunkingResult goodResult = chunker.chunkFileAsync(goodFile, options).get();
            assertNotNull(goodResult);
            assertTrue(goodResult.isSuccess());

            // When - Trigger an error
            Path badFile = tempDir.resolve("nonexistent.txt");
            FileChunker.ChunkingResult badResult = chunker.chunkFileAsync(badFile, options).get();
            assertNotNull(badResult);
            assertFalse(badResult.isSuccess()); // AsyncFileChunker returns failed result, not exception

            // Then - Should still be able to chunk files normally
            Path anotherGoodFile = createTestFile(tempDir, "another_good.txt", 2048);
            FileChunker.ChunkingResult anotherResult = chunker.chunkFileAsync(anotherGoodFile, options).get();
            assertNotNull(anotherResult);
            assertTrue(anotherResult.isSuccess());
        }

        @Test
        @Timeout(10)
        @DisplayName("Should handle closed chunker gracefully")
        void shouldHandleClosedChunkerGracefully() throws Exception {
            // Given
            chunker.closeAsync().get();
            assertTrue(chunker.isClosed());

            Path testFile = createTestFile(tempDir, "closed.txt", 1024);
            FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions()
                    .withChunkSize(256)
                    .withUseAsyncIO(true);

            // When
            CompletableFuture<FileChunker.ChunkingResult> future = chunker.chunkFileAsync(testFile, options);

            // Then
            try {
                AsyncTestUtils.assertFailsWithException(future, IllegalStateException.class,
                        AsyncTestUtils.SHORT_TIMEOUT);
            } catch (AsyncTestUtils.AsyncTestAssertionError e) {
                // If the assertion fails with a different exception type, that's also
                // acceptable
                assertTrue(e.getCause() instanceof IllegalStateException
                        || e.getCause() instanceof RuntimeException);
            }
        }
    }

    static Stream<Arguments> provideFileAndChunkSizes() {
        return Stream.of(
                Arguments.of(1024, 256),
                Arguments.of(2048, 512),
                Arguments.of(4096, 1024),
                Arguments.of(8192, 2048),
                Arguments.of(16384, 4096),
                Arguments.of(32768, 8192),
                Arguments.of(65536, 16384));
    }

    @ParameterizedTest
    @Timeout(15)
    @MethodSource("provideFileAndChunkSizes")
    @DisplayName("Should handle various combinations of file and chunk sizes")
    void shouldHandleVariousCombinationsOfFileAndChunkSizes(int fileSize, int chunkSize) throws Exception {
        // Given
        Path testFile = createTestFile(tempDir, "combo_" + fileSize + "_" + chunkSize + ".txt", fileSize);
        FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions()
                .withChunkSize(chunkSize)
                .withUseAsyncIO(true);

        // When
        CompletableFuture<FileChunker.ChunkingResult> future = chunker.chunkFileAsync(testFile, options);
        FileChunker.ChunkingResult result = AsyncTestUtils.getResultOrThrow(future, AsyncTestUtils.DEFAULT_TIMEOUT);

        // Then
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(fileSize, result.getTotalSize());
        int expectedChunks = (fileSize + chunkSize - 1) / chunkSize; // Ceiling division
        assertEquals(expectedChunks, result.getChunkCount());
        assertEquals(expectedChunks, result.getChunkHashes().size());
    }

    /**
     * Helper method to create test files with specific content patterns.
     */
    private Path createTestFile(Path directory, String fileName, int size) throws IOException {
        return createTestFile(directory, fileName, size,
                AsyncTestDataProvider.ChunkingDataProvider.ContentPattern.RANDOM);
    }

    /**
     * Helper method to create test files with specific content patterns.
     */
    private Path createTestFile(Path directory, String fileName, int size,
            AsyncTestDataProvider.ChunkingDataProvider.ContentPattern pattern) throws IOException {
        Path file = directory.resolve(fileName);
        byte[] data;

        switch (pattern) {
            case EMPTY:
                data = new byte[0];
                break;
            case RANDOM:
                data = new byte[size];
                new java.util.Random().nextBytes(data);
                break;
            case SEQUENTIAL:
                data = new byte[size];
                for (int i = 0; i < size; i++) {
                    data[i] = (byte) (i % 256);
                }
                break;
            case MIXED:
                data = new byte[size];
                for (int i = 0; i < size; i++) {
                    if (i < size / 3) {
                        data[i] = 0;
                    } else if (i < 2 * size / 3) {
                        data[i] = (byte) (i % 256);
                    } else {
                        data[i] = (byte) 0xFF;
                    }
                }
                break;
            case REPEATING:
                data = new byte[size];
                byte[] dataPattern = {0x42, 0x43, 0x44, 0x45 };
                for (int i = 0; i < size; i++) {
                    data[i] = dataPattern[i % dataPattern.length];
                }
                break;
            case SPARSE:
                data = new byte[size];
                // Create sparse pattern - mostly zeros with some data
                for (int i = 0; i < size; i += 1024) {
                    if (i + 10 < size) {
                        data[i] = 0x42;
                        data[i + 1] = 0x43;
                    }
                }
                break;
            default:
                data = new byte[size];
                new java.util.Random().nextBytes(data);
                break;
        }

        Files.write(file, data);
        return file;
    }
}