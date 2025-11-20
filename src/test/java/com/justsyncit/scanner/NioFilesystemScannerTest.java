package com.justsyncit.scanner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for NioFilesystemScanner.
 */
class NioFilesystemScannerTest {
    /** Temporary directory for test files. */
    @TempDir
    Path tempDir;

    /** Filesystem scanner instance for testing. */
    private NioFilesystemScanner scanner;

    @BeforeEach
    void setUp() {
        scanner = new NioFilesystemScanner();
    }

    @Test
    void testScanEmptyDirectory()
            throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        ScanResult result = scanner.scanDirectory(tempDir, new ScanOptions()).get();

        assertTrue(result.getScannedFiles().isEmpty());
        assertTrue(result.getErrors().isEmpty());
        assertEquals(tempDir, result.getRootDirectory());
    }

    @Test
    void testScanDirectoryWithFiles()
            throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        // Create test files
        Files.createFile(tempDir.resolve("file1.txt"));
        Files.createFile(tempDir.resolve("file2.txt"));
        Files.createDirectories(tempDir.resolve("subdir"));
        Files.createFile(tempDir.resolve("subdir/file3.txt"));

        ScanResult result = scanner.scanDirectory(tempDir, new ScanOptions())
                .get();

        assertEquals(3, result.getScannedFileCount());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void testScanWithIncludePattern()
            throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        // Create test files
        Files.createFile(tempDir.resolve("file1.txt"));
        Files.createFile(tempDir.resolve("file2.log"));
        Files.createFile(tempDir.resolve("file3.txt"));

        ScanOptions options = new ScanOptions()
                .withIncludePattern(FileSystems.getDefault()
                        .getPathMatcher("glob:*.txt"));

        ScanResult result = scanner.scanDirectory(tempDir, options).get();

        assertEquals(2, result.getScannedFileCount());
        assertTrue(result.getScannedFiles().stream()
                .allMatch(f -> {
                    Path fileName = f.getPath().getFileName();
                    return fileName != null && fileName.toString().endsWith(".txt");
                }));
    }

    @Test
    void testScanWithExcludePattern()
            throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        // Create test files
        Files.createFile(tempDir.resolve("file1.txt"));
        Files.createFile(tempDir.resolve("file2.tmp"));
        Files.createFile(tempDir.resolve("file3.txt"));

        ScanOptions options = new ScanOptions()
                .withExcludePattern(FileSystems.getDefault()
                        .getPathMatcher("glob:*.tmp"));

        ScanResult result = scanner.scanDirectory(tempDir, options).get();

        assertEquals(2, result.getScannedFileCount());
        assertTrue(result.getScannedFiles().stream()
                .noneMatch(f -> {
                    Path fileName = f.getPath().getFileName();
                    return fileName != null && fileName.toString().endsWith(".tmp");
                }));
    }

    @Test
    void testScanWithMaxDepth()
            throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        // Create directory structure
        Files.createDirectories(tempDir.resolve("level1/level2/level3"));
        Files.createFile(tempDir.resolve("file1.txt"));
        Files.createFile(tempDir.resolve("level1/file2.txt"));
        Files.createFile(tempDir.resolve("level1/level2/file3.txt"));
        Files.createFile(tempDir.resolve("level1/level2/level3/file4.txt"));

        ScanOptions options = new ScanOptions()
                .withMaxDepth(2);

        ScanResult result = scanner.scanDirectory(tempDir, options).get();

        assertEquals(3, result.getScannedFileCount());
        assertTrue(result.getScannedFiles().stream()
                .noneMatch(f -> f.getPath().toString().contains("level3")));
    }

    @Test
    void testScanWithFileSizeLimits()
            throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        // Create files of different sizes
        Path smallFile = tempDir.resolve("small.txt");
        Path largeFile = tempDir.resolve("large.txt");

        Files.write(smallFile, "small".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] largeData = new byte[10000];
        Files.write(largeFile, largeData);

        ScanOptions options = new ScanOptions()
                .withMinFileSize(10)
                .withMaxFileSize(5000);

        ScanResult result = scanner.scanDirectory(tempDir, options).get();

        assertEquals(0, result.getScannedFileCount()); // small file too small, large file too big
    }

    @Test
    void testScanWithSymlinkStrategy()
            throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        // Create target file completely outside the temp directory
        Path targetFile = Files.createTempFile("target", ".txt");
        targetFile.toFile().deleteOnExit();
        Files.write(targetFile, "content".getBytes(
                java.nio.charset.StandardCharsets.UTF_8));

        // Create symlink in the scan directory pointing to outside target
        Path symlinkFile = tempDir.resolve("symlink.txt");
        Files.createSymbolicLink(symlinkFile, targetFile);

        // Test SKIP strategy
        ScanOptions skipOptions = new ScanOptions()
                .withSymlinkStrategy(SymlinkStrategy.SKIP);

        ScanResult skipResult = scanner.scanDirectory(tempDir, skipOptions).get();
        assertEquals(0, skipResult.getScannedFileCount()); // Only symlink, should be skipped

        // Test RECORD strategy
        ScanOptions recordOptions = new ScanOptions()
                .withSymlinkStrategy(SymlinkStrategy.RECORD);

        ScanResult recordResult = scanner.scanDirectory(tempDir, recordOptions).get();
        assertEquals(1, recordResult.getScannedFileCount());
        assertTrue(recordResult.getScannedFiles().get(0).isSymbolicLink());
    }

    @Test
    void testScanInvalidDirectory() {
        Path invalidPath = tempDir.resolve("nonexistent");
        CompletableFuture<ScanResult> future = scanner.scanDirectory(invalidPath, new ScanOptions());

        assertThrows(java.util.concurrent.ExecutionException.class, () -> future.get());
    }

    @Test
    void testProgressListener()
            throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        // Create test file
        Files.createFile(tempDir.resolve("test.txt"));

        TestProgressListener listener = new TestProgressListener();
        scanner.setProgressListener(listener);

        ScanResult result = scanner.scanDirectory(tempDir, new ScanOptions())
                .get();

        assertTrue(listener.scanStartedCalled);
        assertTrue(listener.fileProcessedCalled);
        assertTrue(listener.scanCompletedCalled);
        assertEquals(result, listener.completedResult);
    }

    @Test
    void testCustomFileVisitor()
            throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        // Create test files
        Files.createFile(tempDir.resolve("file1.txt"));
        Files.createFile(tempDir.resolve("file2.txt"));

        TestFileVisitor visitor = new TestFileVisitor();
        scanner.setFileVisitor(visitor);

        scanner.scanDirectory(tempDir, new ScanOptions()).get();

        assertEquals(2, visitor.visitedFiles.size());
        assertTrue(visitor.visitedFiles.contains(tempDir.resolve("file1.txt")));
        assertTrue(visitor.visitedFiles.contains(tempDir.resolve("file2.txt")));
    }

    /**
     * Test implementation of ProgressListener.
     */
    private static class TestProgressListener implements FilesystemScanner.ProgressListener {
        /** Flag indicating if scan started was called. */
        boolean scanStartedCalled = false;
        /** Flag indicating if file processed was called. */
        boolean fileProcessedCalled = false;
        /** Flag indicating if scan completed was called. */
        boolean scanCompletedCalled = false;
        /** The result completed from the scan. */
        ScanResult completedResult;

        @Override
        public void onScanStarted(Path directory) {
            scanStartedCalled = true;
        }

        @Override
        public void onFileProcessed(Path file, long filesProcessed, long totalFiles) {
            fileProcessedCalled = true;
        }

        @Override
        public void onScanCompleted(ScanResult result) {
            scanCompletedCalled = true;
            completedResult = result;
        }

        @Override
        public void onScanError(Path path, Exception error) {
            // Not used in this test
        }
    }

    /**
     * Test implementation of FileVisitor.
     */
    private static class TestFileVisitor implements FileVisitor {
        /** Set of visited files during the scan. */
        final java.util.Set<Path> visitedFiles = new java.util.HashSet<>();

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            visitedFiles.add(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFailed(Path file, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE;
        }
    }
}