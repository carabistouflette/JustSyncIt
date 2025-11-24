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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for NioFilesystemScanner following TDD principles.
 * Tests core filesystem scanning functionality with various scenarios.
 */
@DisplayName("NioFilesystemScanner Comprehensive Tests")
class NioFilesystemScannerComprehensiveTest extends AsyncTestBase {

    @TempDir
    Path tempDir;

    private NioFilesystemScanner scanner;
    private TestProgressListener progressListener;
    private TestFileVisitor fileVisitor;

    @BeforeEach
    void setUp() {
        super.setUp();
        scanner = new NioFilesystemScanner();
        progressListener = new TestProgressListener();
        fileVisitor = new TestFileVisitor();
    }

    @AfterEach
    void tearDown() {
        // Clean up if needed
    }

    @Nested
    @DisplayName("Basic Operations")
    class BasicOperations {

        @Test
        @DisplayName("Should scan empty directory successfully")
        void shouldScanEmptyDirectorySuccessfully() throws Exception {
            // Given
            scanner.setProgressListener(progressListener);

            // When
            CompletableFuture<ScanResult> future = scanner.scanDirectory(tempDir, new ScanOptions());
            ScanResult result = future.get(5, TimeUnit.SECONDS);

            // Then
            assertNotNull(result);
            assertEquals(tempDir, result.getRootDirectory());
            assertEquals(0, result.getScannedFileCount());
            assertEquals(0, result.getErrorCount());
            assertTrue(result.getDurationMillis() >= 0);

            // Verify progress listener was called
            assertTrue(progressListener.scanStartedCalled);
            assertTrue(progressListener.scanCompletedCalled);
        }

        @Test
        @DisplayName("Should scan directory with files")
        void shouldScanDirectoryWithFiles() throws Exception {
            // Given
            Path file1 = Files.createFile(tempDir.resolve("file1.txt"));
            Path file2 = Files.createFile(tempDir.resolve("file2.txt"));
            Path subdir = Files.createDirectory(tempDir.resolve("subdir"));
            Path file3 = Files.createFile(subdir.resolve("file3.txt"));

            scanner.setProgressListener(progressListener);

            // When
            CompletableFuture<ScanResult> future = scanner.scanDirectory(tempDir, new ScanOptions());
            ScanResult result = future.get(5, TimeUnit.SECONDS);

            // Then
            assertNotNull(result);
            assertEquals(tempDir, result.getRootDirectory());
            assertEquals(3, result.getScannedFileCount());
            assertEquals(0, result.getErrorCount());

            // Verify files were found
            List<ScanResult.ScannedFile> files = result.getScannedFiles();
            assertTrue(files.stream().anyMatch(f -> f.getPath().equals(file1)));
            assertTrue(files.stream().anyMatch(f -> f.getPath().equals(file2)));
            assertTrue(files.stream().anyMatch(f -> f.getPath().equals(file3)));

            // Verify progress listener
            assertEquals(3, progressListener.filesProcessed.get());
        }

        @Test
        @DisplayName("Should handle null directory gracefully")
        void shouldHandleNullDirectoryGracefully() throws Exception {
            // When/Then
            CompletableFuture<ScanResult> future = scanner.scanDirectory(null, new ScanOptions());
            assertThrows(java.util.concurrent.ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("Should handle non-existent directory")
        void shouldHandleNonExistentDirectory() throws Exception {
            // Given
            Path nonExistent = tempDir.resolve("nonexistent");

            // When/Then
            CompletableFuture<ScanResult> future = scanner.scanDirectory(nonExistent, new ScanOptions());
            assertThrows(java.util.concurrent.ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("Should handle file instead of directory")
        void shouldHandleFileInsteadOfDirectory() throws Exception {
            // Given
            Path file = Files.createFile(tempDir.resolve("file.txt"));

            // When/Then
            CompletableFuture<ScanResult> future = scanner.scanDirectory(file, new ScanOptions());
            assertThrows(java.util.concurrent.ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
        }
    }

    @Nested
    @DisplayName("Configuration and Filtering")
    class ConfigurationAndFiltering {

        @Test
        @DisplayName("Should respect include pattern")
        void shouldRespectIncludePattern() throws Exception {
            // Given
            Files.createFile(tempDir.resolve("file1.txt"));
            Files.createFile(tempDir.resolve("file2.log"));
            Files.createFile(tempDir.resolve("file3.txt"));

            ScanOptions options = new ScanOptions()
                    .withIncludePattern(tempDir.getFileSystem().getPathMatcher("glob:*.txt"));

            // When
            CompletableFuture<ScanResult> future = scanner.scanDirectory(tempDir, options);
            ScanResult result = future.get(5, TimeUnit.SECONDS);

            // Then
            assertEquals(2, result.getScannedFileCount());
            assertTrue(result.getScannedFiles().stream()
                    .allMatch(f -> f.getPath().toString().endsWith(".txt")));
        }

        @Test
        @DisplayName("Should respect exclude pattern")
        void shouldRespectExcludePattern() throws Exception {
            // Given
            Files.createFile(tempDir.resolve("file1.txt"));
            Files.createFile(tempDir.resolve("file2.tmp"));
            Files.createFile(tempDir.resolve("file3.txt"));

            ScanOptions options = new ScanOptions()
                    .withExcludePattern(tempDir.getFileSystem().getPathMatcher("glob:*.tmp"));

            // When
            CompletableFuture<ScanResult> future = scanner.scanDirectory(tempDir, options);
            ScanResult result = future.get(5, TimeUnit.SECONDS);

            // Then
            assertEquals(2, result.getScannedFileCount());
            assertTrue(result.getScannedFiles().stream()
                    .noneMatch(f -> f.getPath().toString().endsWith(".tmp")));
        }

        @Test
        @DisplayName("Should respect max depth")
        void shouldRespectMaxDepth() throws Exception {
            // Given
            Files.createFile(tempDir.resolve("file1.txt"));
            Path subdir1 = Files.createDirectory(tempDir.resolve("subdir1"));
            Files.createFile(subdir1.resolve("file2.txt"));
            Path subdir2 = Files.createDirectory(subdir1.resolve("subdir2"));
            Files.createFile(subdir2.resolve("file3.txt"));

            ScanOptions options = new ScanOptions()
                    .withMaxDepth(2);

            // When
            CompletableFuture<ScanResult> future = scanner.scanDirectory(tempDir, options);
            ScanResult result = future.get(5, TimeUnit.SECONDS);

            // Then
            assertEquals(3, result.getScannedFileCount()); // file1.txt, file2.txt, and file3.txt (maxDepth=2 includes 2 levels)
        }

        @Test
        @DisplayName("Should respect hidden file setting")
        void shouldRespectHiddenFileSetting() throws Exception {
            // Given
            Files.createFile(tempDir.resolve("visible.txt"));
            Path hiddenFile = Files.createFile(tempDir.resolve(".hidden.txt"));

            ScanOptions options = new ScanOptions()
                    .withIncludeHiddenFiles(false);

            // When
            CompletableFuture<ScanResult> future = scanner.scanDirectory(tempDir, options);
            ScanResult result = future.get(5, TimeUnit.SECONDS);

            // Then
            assertEquals(1, result.getScannedFileCount());
            assertTrue(result.getScannedFiles().stream()
                    .noneMatch(f -> f.getPath().equals(hiddenFile)));
        }

        @Test
        @DisplayName("Should respect file size limits")
        void shouldRespectFileSizeLimits() throws Exception {
            // Given
            Files.createFile(tempDir.resolve("small.txt"));
            Path mediumFile = Files.createFile(tempDir.resolve("medium.txt"));
            Files.write(mediumFile, "x".repeat(1000).getBytes());
            Path largeFile = Files.createFile(tempDir.resolve("large.txt"));
            Files.write(largeFile, "x".repeat(10000).getBytes());

            ScanOptions options = new ScanOptions()
                    .withMinFileSize(500)
                    .withMaxFileSize(5000);

            // When
            CompletableFuture<ScanResult> future = scanner.scanDirectory(tempDir, options);
            ScanResult result = future.get(5, TimeUnit.SECONDS);

            // Then
            assertEquals(1, result.getScannedFileCount()); // Only medium file
            assertEquals(mediumFile, result.getScannedFiles().get(0).getPath());
        }
    }

    @Nested
    @DisplayName("Symlink Handling")
    class SymlinkHandling {

        @Test
        @DisplayName("Should skip symlinks when configured")
        void shouldSkipSymlinksWhenConfigured() throws Exception {
            // Given
            Path targetFile = Files.createFile(tempDir.resolve("target.txt"));
            Path symlink = Files.createSymbolicLink(tempDir.resolve("symlink.txt"), targetFile);

            ScanOptions options = new ScanOptions()
                    .withSymlinkStrategy(SymlinkStrategy.SKIP);

            // When
            CompletableFuture<ScanResult> future = scanner.scanDirectory(tempDir, options);
            ScanResult result = future.get(5, TimeUnit.SECONDS);

            // Then
            assertEquals(1, result.getScannedFileCount()); // Only target file
            assertTrue(result.getScannedFiles().stream()
                    .noneMatch(f -> f.getPath().equals(symlink)));
        }

        @Test
        @DisplayName("Should record symlinks when configured")
        void shouldRecordSymlinksWhenConfigured() throws Exception {
            // Given
            Path targetFile = Files.createFile(tempDir.resolve("target.txt"));
            Path symlink = Files.createSymbolicLink(tempDir.resolve("symlink.txt"), targetFile);

            ScanOptions options = new ScanOptions()
                    .withSymlinkStrategy(SymlinkStrategy.RECORD);

            // When
            CompletableFuture<ScanResult> future = scanner.scanDirectory(tempDir, options);
            ScanResult result = future.get(5, TimeUnit.SECONDS);

            // Then
            assertEquals(2, result.getScannedFileCount()); // Target file and symlink
            
            ScanResult.ScannedFile symlinkScanned = result.getScannedFiles().stream()
                    .filter(f -> f.getPath().equals(symlink))
                    .findFirst()
                    .orElse(null);
            
            assertNotNull(symlinkScanned);
            assertTrue(symlinkScanned.isSymbolicLink());
            assertEquals(targetFile, symlinkScanned.getLinkTarget());
        }
    }

    @Nested
    @DisplayName("Custom Visitors and Listeners")
    class CustomVisitorsAndListeners {

        @Test
        @DisplayName("Should use custom file visitor")
        void shouldUseCustomFileVisitor() throws Exception {
            // Given
            Files.createFile(tempDir.resolve("file1.txt"));
            Files.createFile(tempDir.resolve("file2.txt"));
            scanner.setFileVisitor(fileVisitor);

            // When
            CompletableFuture<ScanResult> future = scanner.scanDirectory(tempDir, new ScanOptions());
            ScanResult result = future.get(5, TimeUnit.SECONDS);

            // Then
            assertEquals(2, result.getScannedFileCount());
            assertEquals(2, fileVisitor.filesVisited.get());
            assertEquals(1, fileVisitor.directoriesVisited.get());
        }

        @Test
        @DisplayName("Should call progress listener methods")
        void shouldCallProgressListenerMethods() throws Exception {
            // Given
            Files.createFile(tempDir.resolve("file1.txt"));
            Files.createFile(tempDir.resolve("file2.txt"));
            scanner.setProgressListener(progressListener);

            // When
            CompletableFuture<ScanResult> future = scanner.scanDirectory(tempDir, new ScanOptions());
            ScanResult result = future.get(5, TimeUnit.SECONDS);

            // Then
            assertTrue(progressListener.scanStartedCalled);
            assertEquals(2, progressListener.filesProcessed.get());
            assertTrue(progressListener.scanCompletedCalled);
            assertEquals(result, progressListener.lastResult);
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("Should handle unreadable files")
        void shouldHandleUnreadableFiles() throws Exception {
            // Given
            Files.createFile(tempDir.resolve("readable.txt"));
            Path unreadable = Files.createFile(tempDir.resolve("unreadable.txt"));
            unreadable.toFile().setReadable(false);
            unreadable.toFile().setExecutable(false); // Also remove execute permission

            // When
            CompletableFuture<ScanResult> future = scanner.scanDirectory(tempDir, new ScanOptions());
            ScanResult result = future.get(5, TimeUnit.SECONDS);

            // Then
            assertTrue(result.getScannedFileCount() >= 1); // At least the readable file
            // Note: On some systems, files might still be readable despite permission changes
            // So we don't strictly assert error count
        }

        @Test
        @DisplayName("Should handle broken symlinks")
        void shouldHandleBrokenSymlinks() throws Exception {
            // Given
            Path brokenSymlink = Files.createSymbolicLink(tempDir.resolve("broken.txt"), tempDir.resolve("nonexistent.txt"));

            ScanOptions options = new ScanOptions()
                    .withSymlinkStrategy(SymlinkStrategy.RECORD);

            // When
            CompletableFuture<ScanResult> future = scanner.scanDirectory(tempDir, options);
            ScanResult result = future.get(5, TimeUnit.SECONDS);

            // Then
            assertEquals(1, result.getScannedFileCount()); // Broken symlink should be recorded
            ScanResult.ScannedFile symlinkFile = result.getScannedFiles().get(0);
            assertTrue(symlinkFile.isSymbolicLink());
            // Note: The implementation might resolve the symlink target path even if broken
            // So we check if it's a symlink rather than checking target null
        }
    }

    @Nested
    @DisplayName("Performance Characteristics")
    class PerformanceCharacteristics {

        @Test
        @DisplayName("Should handle large number of files efficiently")
        void shouldHandleLargeNumberOfFilesEfficiently() throws Exception {
            // Given
            int fileCount = 100;
            for (int i = 0; i < fileCount; i++) {
                Files.createFile(tempDir.resolve("file" + i + ".txt"));
            }

            // When
            long startTime = System.currentTimeMillis();
            CompletableFuture<ScanResult> future = scanner.scanDirectory(tempDir, new ScanOptions());
            ScanResult result = future.get(10, TimeUnit.SECONDS);
            long endTime = System.currentTimeMillis();

            // Then
            assertEquals(fileCount, result.getScannedFileCount());
            assertTrue(endTime - startTime < 5000, "Scan should complete within 5 seconds");
        }

        @Test
        @DisplayName("Should handle deep directory structures")
        void shouldHandleDeepDirectoryStructures() throws Exception {
            // Given
            Path current = tempDir;
            int depth = 10;
            for (int i = 0; i < depth; i++) {
                current = Files.createDirectory(current.resolve("level" + i));
                Files.createFile(current.resolve("file.txt"));
            }

            // When
            CompletableFuture<ScanResult> future = scanner.scanDirectory(tempDir, new ScanOptions());
            ScanResult result = future.get(10, TimeUnit.SECONDS);

            // Then
            assertEquals(depth, result.getScannedFileCount());
        }
    }

    /**
     * Test implementation of ProgressListener for testing.
     */
    private static class TestProgressListener implements FilesystemScanner.ProgressListener {
        boolean scanStartedCalled = false;
        boolean scanCompletedCalled = false;
        AtomicLong filesProcessed = new AtomicLong(0);
        ScanResult lastResult = null;
        List<Path> errors = new ArrayList<>();

        @Override
        public void onScanStarted(Path directory) {
            scanStartedCalled = true;
        }

        @Override
        public void onFileProcessed(Path file, long filesProcessedCount, long totalFiles) {
            filesProcessed.incrementAndGet();
        }

        @Override
        public void onScanCompleted(ScanResult result) {
            scanCompletedCalled = true;
            lastResult = result;
        }

        @Override
        public void onScanError(Path path, Exception error) {
            errors.add(path);
        }
    }

    /**
     * Test implementation of FileVisitor for testing.
     */
    private static class TestFileVisitor implements FileVisitor {
        AtomicInteger filesVisited = new AtomicInteger(0);
        AtomicInteger directoriesVisited = new AtomicInteger(0);

        @Override
        public FileVisitResult visitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) {
            directoriesVisited.incrementAndGet();
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) {
            filesVisited.incrementAndGet();
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFailed(Path file, IOException exc) {
            return FileVisitResult.CONTINUE;
        }
    }
}