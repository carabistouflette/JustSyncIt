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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Comprehensive unit tests for FilesystemScanner following TDD principles.
 * Tests all aspects of filesystem scanning including edge cases, error conditions,
 * performance characteristics, and concurrent access patterns.
 * Tests are wrapped in CompletableFuture to simulate async behavior for testing infrastructure.
 */
@DisplayName("FilesystemScanner Comprehensive Tests")
class FilesystemScannerComprehensiveTest extends AsyncTestBase {

    @TempDir
    Path tempDir;
    
    private FilesystemScanner scanner;
    private AsyncTestMetricsCollector metricsCollector;

    @BeforeEach
    void setUp() {
        super.setUp();
        metricsCollector = new AsyncTestMetricsCollector();
        scanner = new NioFilesystemScanner();
    }

    @AfterEach
    void tearDown() throws Exception {
        super.tearDown();
        // FilesystemScanner doesn't have close method, so no cleanup needed
    }

    @Nested
    @DisplayName("Basic Operations")
    class BasicOperations {

        @Test
        @DisplayName("Should scan empty directory")
        void shouldScanEmptyDirectory() throws Exception {
            // Given
            ScanOptions options = new ScanOptions()
                .withIncludeHiddenFiles(false)
                .withFollowLinks(false);

            // When
            CompletableFuture<ScanResult> future = scanner.scanDirectory(tempDir, options);
            ScanResult result = AsyncTestUtils.getResultOrThrow(future, AsyncTestUtils.SHORT_TIMEOUT);

            // Then
            assertNotNull(result);
            assertEquals(tempDir, result.getRootDirectory());
            assertEquals(0, result.getScannedFileCount());
            assertEquals(0, result.getErrorCount());
            assertEquals(0, result.getTotalSize());
            assertTrue(result.getScannedFiles().isEmpty());
        }

        @Test
        @DisplayName("Should scan directory with files")
        void shouldScanDirectoryWithFiles() throws Exception {
            // Given
            createTestFiles(tempDir, "file", 5, 1024);
            ScanOptions options = new ScanOptions()
                .withIncludeHiddenFiles(false)
                .withFollowLinks(false);

            // When
            CompletableFuture<ScanResult> future = scanner.scanDirectory(tempDir, options);
            ScanResult result = AsyncTestUtils.getResultOrThrow(future, AsyncTestUtils.SHORT_TIMEOUT);

            // Then
            assertNotNull(result);
            assertEquals(tempDir, result.getRootDirectory());
            assertEquals(5, result.getScannedFileCount());
            assertEquals(0, result.getErrorCount());
            assertEquals(5 * 1024, result.getTotalSize());
            assertEquals(5, result.getScannedFiles().size());
        }

        @Test
        @DisplayName("Should scan directory with subdirectories")
        void shouldScanDirectoryWithSubdirectories() throws Exception {
            // Given
            Path subdir1 = tempDir.resolve("subdir1");
            Path subdir2 = tempDir.resolve("subdir2");
            Files.createDirectories(subdir1);
            Files.createDirectories(subdir2);
            
            createTestFiles(subdir1, "file", 3, 512);
            createTestFiles(subdir2, "file", 2, 2048);
            
            ScanOptions options = new ScanOptions()
                .withMaxDepth(Integer.MAX_VALUE) // Recursive
                .withIncludeHiddenFiles(false)
                .withFollowLinks(false);

            // When
            CompletableFuture<ScanResult> future = scanner.scanDirectory(tempDir, options);
            ScanResult result = AsyncTestUtils.getResultOrThrow(future, AsyncTestUtils.SHORT_TIMEOUT);

            // Then
            assertNotNull(result);
            assertEquals(tempDir, result.getRootDirectory());
            assertEquals(5, result.getScannedFileCount()); // 3 + 2 files
            assertEquals(0, result.getErrorCount());
            assertEquals((3 * 512) + (2 * 2048), result.getTotalSize());
            assertEquals(5, result.getScannedFiles().size());
        }

        @Test
        @DisplayName("Should handle non-existent directory")
        void shouldHandleNonExistentDirectory() {
            // Given
            Path nonExistentDir = tempDir.resolve("nonexistent");
            ScanOptions options = new ScanOptions();

            // When
            CompletableFuture<ScanResult> future = scanner.scanDirectory(nonExistentDir, options);

            // Then
            AsyncTestUtils.assertFailsWithException(future, IllegalArgumentException.class, AsyncTestUtils.SHORT_TIMEOUT);
        }

        @Test
        @DisplayName("Should handle file instead of directory")
        void shouldHandleFileInsteadOfDirectory() throws Exception {
            // Given
            Path file = createTestFile(tempDir, "file.txt", 1024);
            ScanOptions options = new ScanOptions();

            // When
            CompletableFuture<ScanResult> future = scanner.scanDirectory(file, options);

            // Then
            AsyncTestUtils.assertFailsWithException(future, IllegalArgumentException.class, AsyncTestUtils.SHORT_TIMEOUT);
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Conditions")
    class EdgeCasesAndBoundaryConditions {

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 10, 100, 1000})
        @DisplayName("Should handle various file counts")
        void shouldHandleVariousFileCounts(int fileCount) throws Exception {
            // Given
            createTestFiles(tempDir, "various", fileCount, 1024);
            ScanOptions options = new ScanOptions()
                .withIncludeHiddenFiles(false)
                .withFollowLinks(false);

            // When
            CompletableFuture<ScanResult> future = scanner.scanDirectory(tempDir, options);
            ScanResult result = AsyncTestUtils.getResultOrThrow(future, AsyncTestUtils.DEFAULT_TIMEOUT);

            // Then
            assertNotNull(result);
            assertEquals(fileCount, result.getScannedFileCount());
            assertEquals(fileCount * 1024, result.getTotalSize());
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 1024, 4096, 65536, 1048576})
        @DisplayName("Should handle various file sizes")
        void shouldHandleVariousFileSizes(int fileSize) throws Exception {
            // Given
            createTestFiles(tempDir, "size", 5, fileSize);
            ScanOptions options = new ScanOptions()
                .withIncludeHiddenFiles(false)
                .withFollowLinks(false);

            // When
            CompletableFuture<ScanResult> future = scanner.scanDirectory(tempDir, options);
            ScanResult result = AsyncTestUtils.getResultOrThrow(future, AsyncTestUtils.DEFAULT_TIMEOUT);

            // Then
            assertNotNull(result);
            assertEquals(5, result.getScannedFileCount());
            assertEquals(5 * fileSize, result.getTotalSize());
        }

        @Test
        @DisplayName("Should handle deep directory structure")
        void shouldHandleDeepDirectoryStructure() throws Exception {
            // Given
            Path current = tempDir;
            int depth = 10;
            for (int i = 0; i < depth; i++) {
                current = current.resolve("level" + i);
                Files.createDirectories(current);
                createTestFiles(current, "deep", 2, 512);
            }
            
            ScanOptions options = new ScanOptions()
                .withMaxDepth(Integer.MAX_VALUE) // Recursive
                .withIncludeHiddenFiles(false)
                .withFollowLinks(false);

            // When
            CompletableFuture<ScanResult> future = scanner.scanDirectory(tempDir, options);
            ScanResult result = AsyncTestUtils.getResultOrThrow(future, AsyncTestUtils.LONG_TIMEOUT);

            // Then
            assertNotNull(result);
            assertEquals(depth * 2, result.getScannedFileCount());
            assertEquals(depth * 2 * 512, result.getTotalSize());
        }

        @Test
        @DisplayName("Should handle hidden files and directories")
        void shouldHandleHiddenFilesAndDirectories() throws Exception {
            // Given
            createTestFiles(tempDir, "visible", 3, 1024);
            createTestFiles(tempDir, ".hidden", 2, 512);
            
            Path hiddenDir = tempDir.resolve(".hiddenDir");
            Files.createDirectories(hiddenDir);
            createTestFiles(hiddenDir, "hidden", 2, 256);

            // When - exclude hidden
            ScanOptions excludeHidden = new ScanOptions()
                .withIncludeHiddenFiles(false)
                .withMaxDepth(Integer.MAX_VALUE);
            
            CompletableFuture<ScanResult> excludeFuture = scanner.scanDirectory(tempDir, excludeHidden);
            ScanResult excludeResult = AsyncTestUtils.getResultOrThrow(excludeFuture, AsyncTestUtils.SHORT_TIMEOUT);

            // When - include hidden
            ScanOptions includeHidden = new ScanOptions()
                .withIncludeHiddenFiles(true)
                .withMaxDepth(Integer.MAX_VALUE);
            
            CompletableFuture<ScanResult> includeFuture = scanner.scanDirectory(tempDir, includeHidden);
            ScanResult includeResult = AsyncTestUtils.getResultOrThrow(includeFuture, AsyncTestUtils.SHORT_TIMEOUT);

            // Then
            assertNotNull(excludeResult);
            assertEquals(3, excludeResult.getScannedFileCount()); // Only visible files
            assertEquals(3 * 1024, excludeResult.getTotalSize());

            assertNotNull(includeResult);
            assertEquals(7, includeResult.getScannedFileCount()); // All files including hidden
            assertEquals((3 * 1024) + (2 * 512) + (2 * 256), includeResult.getTotalSize());
        }

        @Test
        @DisplayName("Should handle symbolic links")
        void shouldHandleSymbolicLinks() throws Exception {
            // Given
            createTestFiles(tempDir, "original", 2, 1024);
            
            Path linkTarget = tempDir.resolve("original0.txt");
            Path symlink = tempDir.resolve("symlink.txt");
            
            try {
                Files.createSymbolicLink(symlink, linkTarget);
            } catch (UnsupportedOperationException e) {
                // Skip test if symlinks are not supported
                assumeTrue(false, "Symbolic links not supported on this platform");
                return;
            }

            // When - don't follow symlinks
            ScanOptions noFollow = new ScanOptions()
                .withFollowLinks(false)
                .withSymlinkStrategy(SymlinkStrategy.SKIP);
            
            CompletableFuture<ScanResult> noFollowFuture = scanner.scanDirectory(tempDir, noFollow);
            ScanResult noFollowResult = AsyncTestUtils.getResultOrThrow(noFollowFuture, AsyncTestUtils.SHORT_TIMEOUT);

            // When - follow symlinks
            ScanOptions follow = new ScanOptions()
                .withFollowLinks(true)
                .withSymlinkStrategy(SymlinkStrategy.FOLLOW);
            
            CompletableFuture<ScanResult> followFuture = scanner.scanDirectory(tempDir, follow);
            ScanResult followResult = AsyncTestUtils.getResultOrThrow(followFuture, AsyncTestUtils.SHORT_TIMEOUT);

            // Then
            assertNotNull(noFollowResult);
            // Should count symlink as a file, not follow it
            assertTrue(noFollowResult.getScannedFileCount() >= 2);

            assertNotNull(followResult);
            // Should follow symlink and count target file
            assertTrue(followResult.getScannedFileCount() >= 2);
        }
    }

    @Nested
    @DisplayName("Concurrent Access Patterns")
    class ConcurrentAccessPatterns {

        @Test
        @DisplayName("Should handle concurrent scan operations")
        void shouldHandleConcurrentScanOperations() throws Exception {
            // Given
            int scanCount = 5;
            List<Path> scanDirs = new ArrayList<>();
            List<CompletableFuture<ScanResult>> futures = new ArrayList<>();

            for (int i = 0; i < scanCount; i++) {
                Path scanDir = tempDir.resolve("scan" + i);
                Files.createDirectories(scanDir);
                createTestFiles(scanDir, "concurrent", 3, 1024);
                scanDirs.add(scanDir);
                
                ScanOptions options = new ScanOptions();
                CompletableFuture<ScanResult> future = scanner.scanDirectory(scanDir, options);
                futures.add(future);
            }

            // When
            CompletableFuture<ScanResult>[] futureArray = futures.toArray(new CompletableFuture[0]);
            List<ScanResult> results = AsyncTestUtils.waitForAllAndGetResults(
                AsyncTestUtils.LONG_TIMEOUT, futureArray);

            // Then
            assertEquals(scanCount, results.size());
            results.forEach(result -> {
                assertNotNull(result);
                assertEquals(3, result.getScannedFileCount());
                assertEquals(3 * 1024, result.getTotalSize());
            });
        }

        @Test
        @DisplayName("Should handle mixed concurrent operations")
        void shouldHandleMixedConcurrentOperations() throws Exception {
            // Given
            int threadCount = 3;
            int operationsPerThread = 5;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // When
            for (int i = 0; i < threadCount; i++) {
                final int threadIndex = i;
                CompletableFuture<Void> threadFuture = CompletableFuture.runAsync(() -> {
                    for (int j = 0; j < operationsPerThread; j++) {
                        try {
                            Path scanDir = tempDir.resolve("mixed_" + threadIndex + "_" + j);
                            Files.createDirectories(scanDir);
                            createTestFiles(scanDir, "mixed", 2, 512);
                            
                            ScanOptions options = new ScanOptions();
                            CompletableFuture<ScanResult> resultFuture = scanner.scanDirectory(scanDir, options);
                            ScanResult result = resultFuture.get();
                            assertNotNull(result);
                            assertEquals(2, result.getScannedFileCount());
                        } catch (Exception e) {
                            throw new RuntimeException("Operation failed", e);
                        }
                    }
                }, executor);
                futures.add(threadFuture);
            }

            AsyncTestUtils.waitForAll(AsyncTestUtils.LONG_TIMEOUT, futures.toArray(new CompletableFuture[0]));

            // Then
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Nested
    @DisplayName("Performance Characteristics")
    class PerformanceCharacteristics {

        @Test
        @DisplayName("Should meet performance targets for small directories")
        void shouldMeetPerformanceTargetsForSmallDirectories() throws Exception {
            // Given
            int fileCount = 100;
            int fileSize = 1024;
            Duration maxAverageTime = Duration.ofMillis(50); // 50ms target

            createTestFiles(tempDir, "perf", fileCount, fileSize);
            ScanOptions options = new ScanOptions();

            // When
            AsyncTestUtils.TimedResult<ScanResult> result = AsyncTestUtils.measureAsyncTime(() -> {
                return scanner.scanDirectory(tempDir, options);
            });

            // Then
            ScanResult scanResult = result.getResult();
            assertNotNull(scanResult);
            assertEquals(fileCount, scanResult.getScannedFileCount());
            
            double averageTimePerFile = result.getDurationMillis() / (double)fileCount;
            assertTrue(averageTimePerFile <= maxAverageTime.toMillis(),
                String.format("Average time per file (%.2f ms) exceeds target (%.2f ms)",
                    averageTimePerFile, (double)maxAverageTime.toMillis()));
        }

        @Test
        @DisplayName("Should maintain performance under load")
        void shouldMaintainPerformanceUnderLoad() throws Exception {
            // Given
            int threadCount = 10;
            int scansPerThread = 10;
            int filesPerScan = 50;
            Duration maxAverageTime = Duration.ofMillis(100); // 100ms target under load

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // When
            long startTime = System.nanoTime();
            
            for (int i = 0; i < threadCount; i++) {
                final int threadIndex = i;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    for (int j = 0; j < scansPerThread; j++) {
                        try {
                            Path scanDir = tempDir.resolve("load_" + threadIndex + "_" + j);
                            Files.createDirectories(scanDir);
                            createTestFiles(scanDir, "load", filesPerScan, 1024);
                            
                            ScanOptions options = new ScanOptions();
                            CompletableFuture<ScanResult> resultFuture = scanner.scanDirectory(scanDir, options);
                            ScanResult result = resultFuture.get();
                            assertNotNull(result);
                            assertEquals(filesPerScan, result.getScannedFileCount());
                        } catch (Exception e) {
                            throw new RuntimeException("Operation failed", e);
                        }
                    }
                }, executor);
                futures.add(future);
            }

            AsyncTestUtils.waitForAll(AsyncTestUtils.LONG_TIMEOUT, futures.toArray(new CompletableFuture[0]));
            
            long endTime = System.nanoTime();
            long totalDuration = endTime - startTime;
            int totalScans = threadCount * scansPerThread;

            // Then
            double averageTimePerScan = totalDuration / 1_000_000.0 / totalScans;
            assertTrue(averageTimePerScan <= maxAverageTime.toMillis(),
                String.format("Average time per scan under load (%.2f ms) exceeds target (%.2f ms)",
                    averageTimePerScan, (double)maxAverageTime.toMillis()));

            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Nested
    @DisplayName("Integration with Testing Infrastructure")
    class IntegrationWithTestingInfrastructure {

        @Test
        @DisplayName("Should work with AsyncTestMetricsCollector")
        void shouldWorkWithAsyncTestMetricsCollector() throws Exception {
            // Given
            AsyncTestMetricsCollector.OperationTimer timer = 
                metricsCollector.startOperation("scan", "FilesystemScanner");

            createTestFiles(tempDir, "metrics", 5, 2048);
            ScanOptions options = new ScanOptions();

            // When
            CompletableFuture<ScanResult> future = scanner.scanDirectory(tempDir, options);
            ScanResult result = AsyncTestUtils.getResultOrThrow(future, AsyncTestUtils.SHORT_TIMEOUT);
            timer.complete(result.getTotalSize());

            // Then
            AsyncTestMetricsCollector.ComponentMetrics componentMetrics = 
                metricsCollector.getComponentMetrics("FilesystemScanner");
            assertEquals(1, componentMetrics.getTotalOperations());
            assertEquals(1, componentMetrics.getSuccessfulOperations());
            assertEquals(result.getTotalSize(), componentMetrics.getTotalBytesProcessed());
        }

        @Test
        @DisplayName("Should work with AsyncTestScenarioBuilder")
        void shouldWorkWithAsyncTestScenarioBuilder() throws Exception {
            // Given
            AsyncTestScenarioBuilder builder = AsyncTestScenarioBuilder.create();
            
            // Create a concurrent operation
            AsyncTestScenarioBuilder.ConcurrentOperation scanOperation =
                new AsyncTestScenarioBuilder.ConcurrentOperation("Filesystem scan", () -> {
                    try {
                        Path scanDir = tempDir.resolve("scenario_" + System.currentTimeMillis());
                        Files.createDirectories(scanDir);
                        createTestFiles(scanDir, "scenario", 2, 1024);
                        
                        ScanOptions options = new ScanOptions();
                        CompletableFuture<ScanResult> resultFuture = scanner.scanDirectory(scanDir, options);
                        ScanResult scanResult = resultFuture.get(30, TimeUnit.SECONDS);
                        assertNotNull(scanResult);
                    } catch (Exception e) {
                        throw new RuntimeException("Scan operation failed", e);
                    }
                });
            
            builder.withConcurrentOperations(scanOperation);
            
            AsyncTestScenarioBuilder.AsyncTestScenario scenario = builder.build();
            CompletableFuture<AsyncTestScenarioBuilder.ScenarioResult> future = scenario.executeAsync();
            AsyncTestScenarioBuilder.ScenarioResult result = future.get();

            // Then
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("Should work with AsyncTestDataProvider")
        void shouldWorkWithAsyncTestDataProvider() throws Exception {
            // Given
            AsyncTestDataProvider.FilesystemDataProvider.FilesystemScanScenario[] scenarios = 
                AsyncTestDataProvider.FilesystemDataProvider.getScanScenarios();

            for (AsyncTestDataProvider.FilesystemDataProvider.FilesystemScanScenario scenario : scenarios) {
                // When
                scenario.setup(tempDir);
                ScanOptions options = scenario.getScanOptions();
                
                CompletableFuture<ScanResult> future = scanner.scanDirectory(tempDir, options);
                ScanResult result = AsyncTestUtils.getResultOrThrow(future, AsyncTestUtils.DEFAULT_TIMEOUT);

                // Then
                assertNotNull(result);
                scenario.validate(result);
            }
        }
    }

    @Nested
    @DisplayName("Error Handling and Recovery")
    class ErrorHandlingAndRecovery {

        @Test
        @DisplayName("Should handle interrupted operations gracefully")
        void shouldHandleInterruptedOperationsGracefully() throws Exception {
            // Given
            Path scanDir = tempDir.resolve("interrupt");
            Files.createDirectories(scanDir);
            createTestFiles(scanDir, "interrupt", 10, 1024);
            
            ScanOptions options = new ScanOptions();
            CompletableFuture<ScanResult> future = scanner.scanDirectory(scanDir, options);

            // When
            Thread.currentThread().interrupt();

            // Then
            try {
                AsyncTestUtils.getResultOrThrow(future, AsyncTestUtils.SHORT_TIMEOUT);
                fail("Should have thrown exception due to interruption");
            } catch (AsyncTestUtils.AsyncTestException e) {
                assertTrue(e.getCause() instanceof InterruptedException || 
                          e.getCause() instanceof RuntimeException);
            } finally {
                // Clear interrupt status
                Thread.interrupted();
            }
        }

        @Test
        @DisplayName("Should handle timeout scenarios")
        void shouldHandleTimeoutScenarios() throws Exception {
            // Given
            Path scanDir = tempDir.resolve("timeout");
            Files.createDirectories(scanDir);
            
            // Create a CompletableFuture that will never complete to simulate timeout
            CompletableFuture<ScanResult> neverCompletingFuture = new CompletableFuture<>();
            
            // Create a custom scanner that returns the never-completing future
            FilesystemScanner slowScanner = new FilesystemScanner() {
                @Override
                public CompletableFuture<ScanResult> scanDirectory(Path directory, ScanOptions options) {
                    return neverCompletingFuture;
                }
                
                // Add default implementations for other methods
                @Override
                public void setFileVisitor(FileVisitor visitor) {}
                
                @Override
                public void setProgressListener(ProgressListener listener) {}
            };
            
            ScanOptions options = new ScanOptions();
            CompletableFuture<ScanResult> future = slowScanner.scanDirectory(scanDir, options);

            // When
            try {
                AsyncTestUtils.getResultOrThrow(future, AsyncTestUtils.ULTRA_SHORT_TIMEOUT);
                fail("Should have timed out");
            } catch (AsyncTestUtils.AsyncTestException e) {
                assertTrue(e.getMessage().contains("timed out"));
            } finally {
                // Clean up the never-completing future
                neverCompletingFuture.cancel(true);
            }
        }

        @Test
        @DisplayName("Should maintain consistency after errors")
        void shouldMaintainConsistencyAfterErrors() throws Exception {
            // Given
            // First, successful operation
            Path goodDir = tempDir.resolve("good");
            Files.createDirectories(goodDir);
            createTestFiles(goodDir, "good", 3, 1024);
            
            ScanOptions options = new ScanOptions();
            CompletableFuture<ScanResult> goodFuture = scanner.scanDirectory(goodDir, options);
            ScanResult goodResult = AsyncTestUtils.getResultOrThrow(goodFuture, AsyncTestUtils.SHORT_TIMEOUT);
            assertNotNull(goodResult);
            assertEquals(3, goodResult.getScannedFileCount());

            // When - Trigger an error
            Path badDir = tempDir.resolve("nonexistent");
            CompletableFuture<ScanResult> badFuture = scanner.scanDirectory(badDir, options);
            try {
                AsyncTestUtils.getResultOrThrow(badFuture, AsyncTestUtils.SHORT_TIMEOUT);
                fail("Should have thrown exception");
            } catch (Exception e) {
                // Expected
            }

            // Then - Should still be able to scan directories normally
            Path anotherGoodDir = tempDir.resolve("another_good");
            Files.createDirectories(anotherGoodDir);
            createTestFiles(anotherGoodDir, "another", 2, 2048);
            
            CompletableFuture<ScanResult> anotherFuture = scanner.scanDirectory(anotherGoodDir, options);
            ScanResult anotherResult = AsyncTestUtils.getResultOrThrow(anotherFuture, AsyncTestUtils.SHORT_TIMEOUT);
            assertNotNull(anotherResult);
            assertEquals(2, anotherResult.getScannedFileCount());
        }
    }

    static Stream<Arguments> provideFileCountsAndSizes() {
        return Stream.of(
            Arguments.of(1, 1024),
            Arguments.of(10, 2048),
            Arguments.of(50, 4096),
            Arguments.of(100, 8192),
            Arguments.of(500, 16384)
        );
    }

    @ParameterizedTest
    @MethodSource("provideFileCountsAndSizes")
    @DisplayName("Should handle various combinations of file counts and sizes")
    void shouldHandleVariousCombinationsOfFileCountsAndSizes(int fileCount, int fileSize) throws Exception {
        // Given
        createTestFiles(tempDir, "combo", fileCount, fileSize);
        ScanOptions options = new ScanOptions();

        // When
        CompletableFuture<ScanResult> future = scanner.scanDirectory(tempDir, options);
        ScanResult result = AsyncTestUtils.getResultOrThrow(future, AsyncTestUtils.DEFAULT_TIMEOUT);

        // Then
        assertNotNull(result);
        assertEquals(fileCount, result.getScannedFileCount());
        assertEquals(fileCount * fileSize, result.getTotalSize());
    }

    /**
     * Helper method to create test files.
     */
    private void createTestFiles(Path directory, String prefix, int count, int size) throws IOException {
        for (int i = 0; i < count; i++) {
            createTestFile(directory, prefix + "_" + i + ".txt", size);
        }
    }

    /**
     * Helper method to create a single test file.
     */
    private Path createTestFile(Path directory, String fileName, int size) throws IOException {
        Path file = directory.resolve(fileName);
        byte[] data = new byte[size];
        new java.util.Random().nextBytes(data);
        Files.write(file, data);
        return file;
    }
}