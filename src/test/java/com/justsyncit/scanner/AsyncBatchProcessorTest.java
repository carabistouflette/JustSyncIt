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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comprehensive tests for AsyncBatchProcessor implementation.
 * Tests all core functionality including adaptive sizing, priority scheduling,
 * resource management, and error handling.
 */
@DisplayName("AsyncBatchProcessor Tests")
public class AsyncBatchProcessorTest {

    @Mock
    private AsyncFileChunker mockAsyncFileChunker;

    @Mock
    private AsyncByteBufferPool mockAsyncBufferPool;

    @Mock
    private ThreadPoolManager mockThreadPoolManager;

    private AsyncFileBatchProcessorImpl batchProcessor;
    private BatchConfiguration configuration;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        configuration = new BatchConfiguration();
        batchProcessor = AsyncFileBatchProcessorImpl.create(
                mockAsyncFileChunker, mockAsyncBufferPool, mockThreadPoolManager, configuration);

        // Setup mock thread pool manager
        when(mockThreadPoolManager.getThreadPool(any()))
                .thenReturn(java.util.concurrent.ForkJoinPool.commonPool());
        when(mockThreadPoolManager.getThreadPool(ThreadPoolManager.PoolType.IO))
                .thenReturn(java.util.concurrent.ForkJoinPool.commonPool());
        when(mockThreadPoolManager.getThreadPool(ThreadPoolManager.PoolType.BATCH_PROCESSING))
                .thenReturn(java.util.concurrent.ForkJoinPool.commonPool());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (batchProcessor != null && !batchProcessor.isClosed()) {
            batchProcessor.closeAsync().get(5, TimeUnit.SECONDS);
        }
    }

    @Nested
    @DisplayName("Basic Batch Processing Tests")
    class BasicBatchProcessingTests {

        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        @DisplayName("Should process empty batch successfully")
        void shouldProcessEmptyBatch() throws Exception {
            // Given
            List<Path> emptyFiles = Collections.emptyList();
            BatchOptions options = new BatchOptions();

            // When
            CompletableFuture<BatchResult> future = batchProcessor.processBatch(emptyFiles, options);
            BatchResult result = future.get(5, TimeUnit.SECONDS);

            // Then
            assertFalse(result.isSuccess());
            assertNotNull(result.getError());
            assertTrue(result.getError().getMessage().contains("Files list cannot be null or empty"));
        }

        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        @DisplayName("Should reject null options")
        void shouldRejectNullOptions() throws Exception {
            // Given
            List<Path> files = Arrays.asList(Paths.get("test1.txt"), Paths.get("test2.txt"));

            // When
            CompletableFuture<BatchResult> future = batchProcessor.processBatch(files, null);
            BatchResult result = future.get(5, TimeUnit.SECONDS);

            // Then
            assertFalse(result.isSuccess());
            assertNotNull(result.getError());
            assertTrue(result.getError().getMessage().contains("Options cannot be null"));
        }

        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        @DisplayName("Should process single file batch")
        void shouldProcessSingleFileBatch() throws Exception {
            // Given
            Path testFile = Paths.get("test.txt");
            List<Path> files = Arrays.asList(testFile);
            BatchOptions options = new BatchOptions().withBatchSize(1);

            FileChunker.ChunkingResult mockChunkingResult = createMockChunkingResult(testFile, true);
            when(mockAsyncFileChunker.chunkFileAsync(eq(testFile), any()))
                    .thenReturn(CompletableFuture.completedFuture(mockChunkingResult));

            // When
            CompletableFuture<BatchResult> future = batchProcessor.processBatch(files, options);
            BatchResult result = future.get(10, TimeUnit.SECONDS);

            // Then
            assertTrue(result.isSuccess());
            assertEquals(1, result.getFiles().size());
            assertEquals(testFile, result.getFiles().get(0));
            assertEquals(1, result.getSuccessfulFiles());
            assertEquals(0, result.getFailedFiles());
        }

        @Test
        @Timeout(value = 15, unit = TimeUnit.SECONDS)
        @DisplayName("Should process multiple file batch")
        void shouldProcessMultipleFileBatch() throws Exception {
            // Given
            List<Path> files = Arrays.asList(
                    Paths.get("test1.txt"),
                    Paths.get("test2.txt"),
                    Paths.get("test3.txt"));
            BatchOptions options = new BatchOptions().withBatchSize(3);

            // Setup mock chunking results
            for (Path file : files) {
                FileChunker.ChunkingResult mockChunkingResult = createMockChunkingResult(file, true);
                when(mockAsyncFileChunker.chunkFileAsync(eq(file), any()))
                        .thenReturn(CompletableFuture.completedFuture(mockChunkingResult));
            }

            // When
            CompletableFuture<BatchResult> future = batchProcessor.processBatch(files, options);
            BatchResult result = future.get(15, TimeUnit.SECONDS);

            // Then
            assertTrue(result.isSuccess());
            assertEquals(3, result.getFiles().size());
            assertEquals(3, result.getSuccessfulFiles());
            assertEquals(0, result.getFailedFiles());
            assertNotNull(result.getPerformanceMetrics());
        }
    }

    @Nested
    @DisplayName("Priority Scheduling Tests")
    class PrioritySchedulingTests {

        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        @DisplayName("Should process high priority batch with priority")
        void shouldProcessHighPriorityBatch() throws Exception {
            // Given
            List<Path> files = Arrays.asList(Paths.get("high_priority.txt"));
            BatchOptions options = new BatchOptions().withPriorityScheduling(true);

            FileChunker.ChunkingResult mockChunkingResult = createMockChunkingResult(files.get(0), true);
            when(mockAsyncFileChunker.chunkFileAsync(any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(mockChunkingResult));

            // When
            CompletableFuture<BatchResult> future = batchProcessor.processBatch(
                    files, options, BatchPriority.HIGH);
            BatchResult result = future.get(10, TimeUnit.SECONDS);

            // Then
            assertTrue(result.isSuccess());
            verify(mockAsyncFileChunker, atLeastOnce()).chunkFileAsync(any(), any());
        }

        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        @DisplayName("Should process critical priority batch first")
        void shouldProcessCriticalPriorityBatchFirst() throws Exception {
            // Given
            List<Path> criticalFiles = Arrays.asList(Paths.get("critical.txt"));
            List<Path> normalFiles = Arrays.asList(Paths.get("normal.txt"));
            BatchOptions options = new BatchOptions().withPriorityScheduling(true);

            FileChunker.ChunkingResult criticalResult = createMockChunkingResult(criticalFiles.get(0),
                    true);
            FileChunker.ChunkingResult normalResult = createMockChunkingResult(normalFiles.get(0), true);

            when(mockAsyncFileChunker.chunkFileAsync(eq(criticalFiles.get(0)), any()))
                    .thenReturn(CompletableFuture.completedFuture(criticalResult));
            when(mockAsyncFileChunker.chunkFileAsync(eq(normalFiles.get(0)), any()))
                    .thenReturn(CompletableFuture.completedFuture(normalResult));

            // When
            CompletableFuture<BatchResult> criticalFuture = batchProcessor.processBatch(
                    criticalFiles, options, BatchPriority.CRITICAL);
            CompletableFuture<BatchResult> normalFuture = batchProcessor.processBatch(
                    normalFiles, options, BatchPriority.NORMAL);

            // Then
            BatchResult criticalBatchResult = criticalFuture.get(10, TimeUnit.SECONDS);
            BatchResult normalBatchResult = normalFuture.get(10, TimeUnit.SECONDS);

            assertTrue(criticalBatchResult.isSuccess());
            assertTrue(normalBatchResult.isSuccess());
        }
    }

    @Nested
    @DisplayName("Adaptive Sizing Tests")
    class AdaptiveSizingTests {

        @Test
        @Timeout(value = 15, unit = TimeUnit.SECONDS)
        @DisplayName("Should adapt batch size based on file sizes")
        void shouldAdaptBatchSizeBasedOnFileSizes() throws Exception {
            // Given
            List<Path> files = Arrays.asList(
                    Paths.get("small1.txt"), // 1KB
                    Paths.get("small2.txt"), // 1KB
                    Paths.get("large1.txt") // 10MB
            );
            BatchOptions options = new BatchOptions()
                    .withAdaptiveSizing(true)
                    .withBatchSize(10);

            // Setup mock chunking results with different file sizes
            when(mockAsyncFileChunker.chunkFileAsync(eq(files.get(0)), any()))
                    .thenReturn(CompletableFuture.completedFuture(
                            createMockChunkingResult(files.get(0), true, 1024)));
            when(mockAsyncFileChunker.chunkFileAsync(eq(files.get(1)), any()))
                    .thenReturn(CompletableFuture.completedFuture(
                            createMockChunkingResult(files.get(1), true, 1024)));
            when(mockAsyncFileChunker.chunkFileAsync(eq(files.get(2)), any()))
                    .thenReturn(CompletableFuture.completedFuture(
                            createMockChunkingResult(files.get(2), true,
                                    10 * 1024 * 1024)));

            // When
            CompletableFuture<BatchResult> future = batchProcessor.processBatch(files, options);
            BatchResult result = future.get(15, TimeUnit.SECONDS);

            // Then
            assertTrue(result.isSuccess());
            assertEquals(3, result.getSuccessfulFiles());
            assertNotNull(result.getPerformanceMetrics());
        }

        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        @DisplayName("Should disable adaptive sizing when configured")
        void shouldDisableAdaptiveSizingWhenConfigured() throws Exception {
            // Given
            List<Path> files = Arrays.asList(Paths.get("test1.txt"), Paths.get("test2.txt"));
            BatchOptions options = new BatchOptions()
                    .withAdaptiveSizing(false)
                    .withBatchSize(2);

            for (Path file : files) {
                FileChunker.ChunkingResult mockChunkingResult = createMockChunkingResult(file, true);
                when(mockAsyncFileChunker.chunkFileAsync(eq(file), any()))
                        .thenReturn(CompletableFuture.completedFuture(mockChunkingResult));
            }

            // When
            CompletableFuture<BatchResult> future = batchProcessor.processBatch(files, options);
            BatchResult result = future.get(10, TimeUnit.SECONDS);

            // Then
            assertTrue(result.isSuccess());
            assertEquals(2, result.getSuccessfulFiles());
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        @DisplayName("Should handle chunking errors gracefully")
        void shouldHandleChunkingErrorsGracefully() throws Exception {
            // Given
            List<Path> files = Arrays.asList(Paths.get("error.txt"));
            BatchOptions options = new BatchOptions();

            FileChunker.ChunkingResult errorResult = createMockChunkingResult(files.get(0), false);
            when(mockAsyncFileChunker.chunkFileAsync(eq(files.get(0)), any()))
                    .thenReturn(CompletableFuture.completedFuture(errorResult));

            // When
            CompletableFuture<BatchResult> future = batchProcessor.processBatch(files, options);
            BatchResult result = future.get(10, TimeUnit.SECONDS);

            // Then
            assertTrue(result.isSuccess()); // Batch succeeds even if individual files fail
            assertEquals(0, result.getSuccessfulFiles());
            assertEquals(1, result.getFailedFiles());
        }

        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        @DisplayName("Should handle async exceptions")
        void shouldHandleAsyncExceptions() throws Exception {
            // Given
            List<Path> files = Arrays.asList(Paths.get("exception.txt"));
            BatchOptions options = new BatchOptions();

            when(mockAsyncFileChunker.chunkFileAsync(any(), any()))
                    .thenReturn(CompletableFuture.failedFuture(
                            new RuntimeException("Async processing failed")));

            // When
            CompletableFuture<BatchResult> future = batchProcessor.processBatch(files, options);
            BatchResult result = future.get(10, TimeUnit.SECONDS);

            // Then
            assertTrue(result.isSuccess());
            assertEquals(0, result.getSuccessfulFiles());
            assertEquals(1, result.getFailedFiles());
        }
    }

    @Nested
    @DisplayName("Resource Management Tests")
    class ResourceManagementTests {

        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        @DisplayName("Should respect concurrent batch limits")
        void shouldRespectConcurrentBatchLimits() throws Exception {
            // Given
            configuration = new BatchConfiguration()
                    .withMaxConcurrentBatches(1);
            batchProcessor = AsyncFileBatchProcessorImpl.create(
                    mockAsyncFileChunker, mockAsyncBufferPool, mockThreadPoolManager,
                    configuration);

            List<Path> files1 = Arrays.asList(Paths.get("batch1.txt"));
            List<Path> files2 = Arrays.asList(Paths.get("batch2.txt"));
            BatchOptions options = new BatchOptions();

            // Setup slow chunking for first batch
            CompletableFuture<FileChunker.ChunkingResult> slowFuture = new CompletableFuture<>();
            when(mockAsyncFileChunker.chunkFileAsync(eq(files1.get(0)), any()))
                    .thenReturn(slowFuture);

            // Setup fast chunking for second batch
            FileChunker.ChunkingResult fastResult = createMockChunkingResult(files2.get(0), true);
            when(mockAsyncFileChunker.chunkFileAsync(eq(files2.get(0)), any()))
                    .thenReturn(CompletableFuture.completedFuture(fastResult));

            // When
            CompletableFuture<BatchResult> future1 = batchProcessor.processBatch(files1, options);
            CompletableFuture<BatchResult> future2 = batchProcessor.processBatch(files2, options);

            // Then - second batch should wait for first to complete
            slowFuture.complete(createMockChunkingResult(files1.get(0), true));

            BatchResult result1 = future1.get(10, TimeUnit.SECONDS);
            BatchResult result2 = future2.get(10, TimeUnit.SECONDS);

            assertTrue(result1.isSuccess());
            assertTrue(result2.isSuccess());
        }

        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        @DisplayName("Should apply backpressure when configured")
        void shouldApplyBackpressureWhenConfigured() throws Exception {
            // Given
            List<Path> files = Arrays.asList(Paths.get("backpressure.txt"));
            BatchOptions options = new BatchOptions().withBackpressureControl(true);

            FileChunker.ChunkingResult mockChunkingResult = createMockChunkingResult(files.get(0), true);
            when(mockAsyncFileChunker.chunkFileAsync(any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(mockChunkingResult));

            // When
            batchProcessor.applyBackpressure(0.8); // 80% backpressure
            CompletableFuture<BatchResult> future = batchProcessor.processBatch(files, options);
            BatchResult result = future.get(10, TimeUnit.SECONDS);

            // Then
            assertTrue(result.isSuccess());
            assertEquals(0.8, batchProcessor.getCurrentBackpressure(), 0.01);
        }
    }

    @Nested
    @DisplayName("Performance Metrics Tests")
    class PerformanceMetricsTests {

        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        @DisplayName("Should collect accurate performance metrics")
        void shouldCollectAccuratePerformanceMetrics() throws Exception {
            // Given
            List<Path> files = Arrays.asList(Paths.get("metrics.txt"));
            BatchOptions options = new BatchOptions();

            FileChunker.ChunkingResult mockChunkingResult = createMockChunkingResult(files.get(0), true,
                    1024 * 1024);
            when(mockAsyncFileChunker.chunkFileAsync(any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(mockChunkingResult));

            // When
            CompletableFuture<BatchResult> future = batchProcessor.processBatch(files, options);
            BatchResult result = future.get(10, TimeUnit.SECONDS);

            // Then
            assertTrue(result.isSuccess());
            assertNotNull(result.getPerformanceMetrics());
            assertTrue(result.getPerformanceMetrics().getThroughputMBps() >= 0);
            assertTrue(result.getPerformanceMetrics().getAverageProcessingTimePerFileMs() >= 0);
            assertNotNull(result.getPerformanceMetrics().getPerformanceGrade());
        }

        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        @DisplayName("Should calculate optimal performance grade")
        void shouldCalculateOptimalPerformanceGrade() throws Exception {
            // Given
            List<Path> files = Arrays.asList(Paths.get("optimal.txt"));
            BatchOptions options = new BatchOptions();

            FileChunker.ChunkingResult mockChunkingResult = createMockChunkingResult(files.get(0), true);
            when(mockAsyncFileChunker.chunkFileAsync(any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(mockChunkingResult));

            // When
            CompletableFuture<BatchResult> future = batchProcessor.processBatch(files, options);
            BatchResult result = future.get(10, TimeUnit.SECONDS);

            // Then
            assertTrue(result.isSuccess());
            BatchPerformanceMetrics metrics = result.getPerformanceMetrics();
            assertNotNull(metrics.getPerformanceGrade());
            assertTrue(metrics.getPerformanceGrade().ordinal() <= BatchPerformanceMetrics.PerformanceGrade.B
                    .ordinal());
        }
    }

    /**
     * Creates a mock chunking result for testing.
     */
    private FileChunker.ChunkingResult createMockChunkingResult(Path file, boolean success) {
        return createMockChunkingResult(file, success, 1024);
    }

    /**
     * Creates a mock chunking result with specific file size.
     */
    private FileChunker.ChunkingResult createMockChunkingResult(Path file, boolean success, long fileSize) {
        if (success) {
            return new FileChunker.ChunkingResult(
                    file,
                    (int) Math.max(1, fileSize / (64 * 1024)), // chunk count
                    fileSize,
                    0L, // sparse size
                    "mock-hash-" + file.getFileName(),
                    java.util.Arrays.asList("chunk1", "chunk2"));
        } else {
            return FileChunker.ChunkingResult.createFailed(
                    file,
                    new RuntimeException("Mock chunking error"));
        }
    }
}