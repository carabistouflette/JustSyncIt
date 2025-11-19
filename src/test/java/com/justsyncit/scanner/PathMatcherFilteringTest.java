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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.justsyncit.scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for PathMatcher filtering in filesystem scanner.
 */
class PathMatcherFilteringTest {

    /** Temporary directory for test files. */
    @TempDir
    Path tempDir;

    /** The filesystem scanner under test. */
    private FilesystemScanner scanner;

    @BeforeEach
    void setUp() {
        scanner = new NioFilesystemScanner();
    }

    @Test
    void testIncludePatternGlob() throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        // Create test files
        Files.write(tempDir.resolve("file1.txt"), "content1".getBytes(StandardCharsets.UTF_8));
        Files.write(tempDir.resolve("file2.log"), "content2".getBytes(StandardCharsets.UTF_8));
        Files.write(tempDir.resolve("file3.txt"), "content3".getBytes(StandardCharsets.UTF_8));
        Files.write(tempDir.resolve("file4.dat"), "content4".getBytes(StandardCharsets.UTF_8));

        PathMatcher includePattern = FileSystems.getDefault().getPathMatcher("glob:*.txt");
        ScanOptions options = new ScanOptions()
                .withIncludePattern(includePattern);
        ScanResult result = scanner.scanDirectory(tempDir, options).get();

        assertEquals(2, result.getScannedFileCount());
        assertEquals(0, result.getErrorCount());

        // Verify only .txt files are included
        assertTrue(result.getScannedFiles().stream()
                .allMatch(f -> f.getPath().toString().endsWith(".txt")));
    }

    @Test
    void testExcludePatternGlob() throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        // Create test files
        Files.write(tempDir.resolve("file1.txt"), "content1".getBytes(StandardCharsets.UTF_8));
        Files.write(tempDir.resolve("file2.tmp"), "content2".getBytes(StandardCharsets.UTF_8));
        Files.write(tempDir.resolve("file3.txt"), "content3".getBytes(StandardCharsets.UTF_8));
        Files.write(tempDir.resolve("file4.tmp"), "content4".getBytes(StandardCharsets.UTF_8));

        PathMatcher excludePattern = FileSystems.getDefault().getPathMatcher("glob:*.tmp");
        ScanOptions options = new ScanOptions()
                .withExcludePattern(excludePattern);
        ScanResult result = scanner.scanDirectory(tempDir, options).get();

        assertEquals(2, result.getScannedFileCount());
        assertEquals(0, result.getErrorCount());

        // Verify .tmp files are excluded
        assertTrue(result.getScannedFiles().stream()
                .noneMatch(f -> f.getPath().toString().endsWith(".tmp")));
    }

    @Test
    void testIncludeAndExcludePatterns()
            throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        // Create test files
        Files.write(tempDir.resolve("file1.txt"), "content1".getBytes(StandardCharsets.UTF_8));
        Files.write(tempDir.resolve("file2.tmp"), "content2".getBytes(StandardCharsets.UTF_8));
        Files.write(tempDir.resolve("file3.txt"), "content3".getBytes(StandardCharsets.UTF_8));
        Files.write(tempDir.resolve("file4.log"), "content4".getBytes(StandardCharsets.UTF_8));

        PathMatcher includePattern = FileSystems.getDefault().getPathMatcher("glob:*.txt");
        PathMatcher excludePattern = FileSystems.getDefault().getPathMatcher("glob:file3.*");
        ScanOptions options = new ScanOptions()
                .withIncludePattern(includePattern)
                .withExcludePattern(excludePattern);
        ScanResult result = scanner.scanDirectory(tempDir, options)
                .get();

        assertEquals(1, result.getScannedFileCount()); // Only file1.txt should be included
        // (file3.txt excluded by exclude)
        assertEquals(0, result.getErrorCount());

        // Verify correct file is included
        assertTrue(result.getScannedFiles().stream()
                .anyMatch(f -> {
                    Path fileName = f.getPath().getFileName();
                    return fileName != null && fileName.toString().equals("file1.txt");
                }));
    }

    @Test
    void testRegexPattern()
            throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        // Create test files
        Files.write(tempDir.resolve("test123.txt"), "content1".getBytes(StandardCharsets.UTF_8));
        Files.write(tempDir.resolve("test456.txt"), "content2".getBytes(StandardCharsets.UTF_8));
        Files.write(tempDir.resolve("test789.txt"), "content3".getBytes(StandardCharsets.UTF_8));
        Files.write(tempDir.resolve("other.txt"), "content4".getBytes(StandardCharsets.UTF_8));

        PathMatcher includePattern = FileSystems.getDefault().getPathMatcher("regex:test[0-9]+\\.txt");
        ScanOptions options = new ScanOptions()
                .withIncludePattern(includePattern);
        ScanResult result = scanner.scanDirectory(tempDir, options).get();

        assertEquals(3, result.getScannedFileCount());
        assertEquals(0, result.getErrorCount());

        // Verify only testXXX.txt files are included
        assertTrue(result.getScannedFiles().stream()
                .allMatch(f -> {
                    Path fileName = f.getPath().getFileName();
                    return fileName != null && fileName.toString().matches("test[0-9]+\\.txt");
                }));
    }

    @Test
    void testComplexGlobPattern()
            throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        // Create test files
        Files.write(tempDir.resolve("src_main.java"), "content1".getBytes(StandardCharsets.UTF_8));
        Files.write(tempDir.resolve("src_test.java"), "content2".getBytes(StandardCharsets.UTF_8));
        Files.write(tempDir.resolve("src_helper.java"), "content3".getBytes(StandardCharsets.UTF_8));
        Files.write(tempDir.resolve("main.java"), "content4".getBytes(StandardCharsets.UTF_8));
        Files.write(tempDir.resolve("test.txt"), "content5".getBytes(StandardCharsets.UTF_8));

        PathMatcher includePattern = FileSystems.getDefault().getPathMatcher("glob:src_*.java");
        ScanOptions options = new ScanOptions()
                .withIncludePattern(includePattern);
        ScanResult result = scanner.scanDirectory(tempDir, options).get();

        assertEquals(3, result.getScannedFileCount());
        assertEquals(0, result.getErrorCount());

        // Verify only src_*.java files are included
        assertTrue(result.getScannedFiles().stream()
                .allMatch(f -> {
                    Path fileName = f.getPath().getFileName();
                    if (fileName == null) {
                        return false;
                    }
                    String name = fileName.toString();
                    return name.startsWith("src_") && name.endsWith(".java");
                }));
    }

    @Test
    void testPatternCaseSensitivity()
            throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        // Create test files
        Files.write(tempDir.resolve("FILE1.TXT"), "content1".getBytes(StandardCharsets.UTF_8));
        Files.write(tempDir.resolve("file2.txt"), "content2".getBytes(StandardCharsets.UTF_8));
        Files.write(tempDir.resolve("File3.TXT"), "content3".getBytes(StandardCharsets.UTF_8));
        Files.write(tempDir.resolve("file4.txt"), "content4".getBytes(StandardCharsets.UTF_8));

        // Test case-sensitive pattern - glob patterns are case-sensitive on most systems
        PathMatcher caseSensitivePattern = FileSystems.getDefault().getPathMatcher("glob:*.txt");
        ScanOptions options = new ScanOptions()
                .withIncludePattern(caseSensitivePattern);
        ScanResult result = scanner.scanDirectory(tempDir, options).get();

        // On Windows, glob patterns are case-insensitive, on Unix they are case-sensitive
        // Adjust expectations based on platform
        boolean isCaseSensitive = !System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");
        int expectedCount = isCaseSensitive ? 2 : 4; // 2 lowercase on Unix, 4 total on Windows

        assertEquals(expectedCount, result.getScannedFileCount(),
                "Expected " + expectedCount + " files on " + System.getProperty("os.name"));
        assertEquals(0, result.getErrorCount());

        // Verify all files end with .txt (case-insensitive check)
        assertTrue(result.getScannedFiles().stream()
                .allMatch(f -> f.getPath().toString().toLowerCase(Locale.ROOT).endsWith(".txt")));
    }

    @Test
    void testPatternWithDirectories()
            throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        // Create directory structure
        Path dir1 = tempDir.resolve("dir1");
        Path dir2 = tempDir.resolve("dir2");
        Files.createDirectories(dir1);
        Files.createDirectories(dir2);

        Files.write(dir1.resolve("file1.txt"), "content1".getBytes(StandardCharsets.UTF_8));
        Files.write(dir1.resolve("file2.log"), "content2".getBytes(StandardCharsets.UTF_8));
        Files.write(dir2.resolve("file3.txt"), "content3".getBytes(StandardCharsets.UTF_8));
        Files.write(dir2.resolve("file4.txt"), "content4".getBytes(StandardCharsets.UTF_8));

        PathMatcher includePattern = FileSystems.getDefault().getPathMatcher("glob:**/*.txt");
        ScanOptions options = new ScanOptions()
                .withIncludePattern(includePattern);
        ScanResult result = scanner.scanDirectory(tempDir, options).get();

        assertEquals(3, result.getScannedFileCount());
        assertEquals(0, result.getErrorCount());

        // Verify all .txt files in subdirectories are included
        assertTrue(result.getScannedFiles().stream()
                .allMatch(f -> f.getPath().toString().endsWith(".txt")));
    }

    @Test
    void testNoPattern()
            throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        // Create test files
        Files.write(tempDir.resolve("file1.txt"), "content1".getBytes(StandardCharsets.UTF_8));
        Files.write(tempDir.resolve("file2.log"), "content2".getBytes(StandardCharsets.UTF_8));
        Files.write(tempDir.resolve("file3.dat"), "content3".getBytes(StandardCharsets.UTF_8));

        // No pattern - should include all files
        ScanOptions options = new ScanOptions(); // No include/exclude pattern

        ScanResult result = scanner.scanDirectory(tempDir, options).get();

        assertEquals(3, result.getScannedFileCount());
        assertEquals(0, result.getErrorCount());
    }

    @Test
    void testPatternMatchingWithHiddenFiles()
            throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        // Create test files including hidden ones
        Files.write(tempDir.resolve("visible.txt"), "content1".getBytes(StandardCharsets.UTF_8));
        Files.write(tempDir.resolve(".hidden.txt"), "content2".getBytes(StandardCharsets.UTF_8));
        Files.write(tempDir.resolve("visible2.txt"), "content3".getBytes(StandardCharsets.UTF_8));

        PathMatcher includePattern = FileSystems.getDefault().getPathMatcher("glob:*.txt");
        ScanOptions options = new ScanOptions()
                .withIncludePattern(includePattern)
                .withIncludeHiddenFiles(false);

        ScanResult result = scanner.scanDirectory(tempDir, options).get();

        // Should find only visible files that match the pattern
        // The glob pattern *.txt should not match .hidden.txt because it doesn't start with .
        // However, on Windows the pattern matching might behave differently, so we check for at least 2
        assertTrue(result.getScannedFileCount() >= 2,
                "Expected at least 2 visible files, but found " + result.getScannedFileCount());
        assertEquals(0, result.getErrorCount());

        // Verify hidden files are not included - check both file name and that we have expected visible files
        assertTrue(result.getScannedFiles().stream()
                .noneMatch(f -> {
                    Path fileName = f.getPath().getFileName();
                    return fileName != null && fileName.toString().startsWith(".");
                }),
                "Found hidden files in results: " + result.getScannedFiles().stream()
                        .map(f -> f.getPath().getFileName() != null ? f.getPath().getFileName().toString() : "null")
                        .toList());
        
        // Also verify we have the expected visible files
        assertTrue(result.getScannedFiles().stream()
                .anyMatch(f -> {
                    Path fileName = f.getPath().getFileName();
                    return fileName != null && fileName.toString().equals("visible.txt");
                }),
                "visible.txt not found in results");
        assertTrue(result.getScannedFiles().stream()
                .anyMatch(f -> {
                    Path fileName = f.getPath().getFileName();
                    return fileName != null && fileName.toString().equals("visible2.txt");
                }),
                "visible2.txt not found in results");
    }

    @Test
    void testPatternWithMaxDepth()
            throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        // Create nested directory structure
        Path level1 = tempDir.resolve("level1");
        Path level2 = level1.resolve("level2");
        Path level3 = level2.resolve("level3");
        Files.createDirectories(level1);
        Files.createDirectories(level2);
        Files.createDirectories(level3);

        Files.write(tempDir.resolve("root.txt"), "content0".getBytes(StandardCharsets.UTF_8));
        Files.write(level1.resolve("file1.txt"), "content1".getBytes(StandardCharsets.UTF_8));
        Files.write(level2.resolve("file2.txt"), "content2".getBytes(StandardCharsets.UTF_8));
        Files.write(level3.resolve("file3.txt"), "content3".getBytes(StandardCharsets.UTF_8));

        PathMatcher includePattern = FileSystems.getDefault().getPathMatcher("glob:*.txt");
        ScanOptions options = new ScanOptions()
                .withIncludePattern(includePattern)
                .withMaxDepth(2);

        ScanResult result = scanner.scanDirectory(tempDir, options).get();

        assertEquals(2, result.getScannedFileCount()); // root.txt, level1/file1.txt (level2/file2.txt is at depth 3)
        assertEquals(0, result.getErrorCount());

        // Verify level3/file3.txt is not included due to depth limit
        assertTrue(result.getScannedFiles().stream()
                .noneMatch(f -> f.getPath().toString().contains("level3")));
    }
}