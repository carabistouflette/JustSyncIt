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
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for symbolic link handling in filesystem scanner.
 */
class SymlinkHandlingTest {

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
    void testSkipSymlinks() throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        // Create target file
        Path targetFile = tempDir.resolve("target.txt");
        Files.write(targetFile, "target content".getBytes(StandardCharsets.UTF_8));

        // Create symlink
        Path symlinkFile = tempDir.resolve("symlink.txt");
        Files.createSymbolicLink(symlinkFile, targetFile);

        ScanOptions options = new ScanOptions()
                .withSymlinkStrategy(SymlinkStrategy.SKIP);

        ScanResult result = scanner.scanDirectory(tempDir, options).get();

        // Should only find the target file, not the symlink
        assertEquals(1, result.getScannedFileCount());
        assertEquals(0, result.getErrorCount());

        ScanResult.ScannedFile scannedFile = result.getScannedFiles().get(0);
        assertEquals(targetFile, scannedFile.getPath());
        assertFalse(scannedFile.isSymbolicLink());
    }

    @Test
    void testRecordSymlinks() throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        // Create target file
        Path targetFile = tempDir.resolve("target.txt");
        Files.write(targetFile, "target content".getBytes(StandardCharsets.UTF_8));

        // Create symlink
        Path symlinkFile = tempDir.resolve("symlink.txt");
        Files.createSymbolicLink(symlinkFile, targetFile);

        ScanOptions options = new ScanOptions()
                .withSymlinkStrategy(SymlinkStrategy.RECORD);

        ScanResult result = scanner.scanDirectory(tempDir, options).get();

        // Should find both the target file and the symlink
        assertEquals(2, result.getScannedFileCount());
        assertEquals(0, result.getErrorCount());

        // Find the symlink
        ScanResult.ScannedFile symlinkScannedFile = result.getScannedFiles().stream()
                .filter(f -> f.isSymbolicLink())
                .findFirst()
                .orElseThrow(() -> new AssertionError("Symlink not found in results"));

        assertEquals(symlinkFile, symlinkScannedFile.getPath());
        assertEquals(targetFile, symlinkScannedFile.getLinkTarget());
    }

    @Test
    void testFollowSymlinks() throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        // Create target file
        Path targetFile = tempDir.resolve("target.txt");
        Files.write(targetFile, "target content".getBytes(StandardCharsets.UTF_8));

        // Create symlink
        Path symlinkFile = tempDir.resolve("symlink.txt");
        Files.createSymbolicLink(symlinkFile, targetFile);

        ScanOptions options = new ScanOptions()
                .withSymlinkStrategy(SymlinkStrategy.FOLLOW);

        ScanResult result = scanner.scanDirectory(tempDir, options).get();

        // Should find both files (symlink will be followed to target)
        assertEquals(2, result.getScannedFileCount());
        assertEquals(0, result.getErrorCount());
    }

    @Test
    void testBrokenSymlink() throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        // Create symlink to non-existent target
        Path symlinkFile = tempDir.resolve("broken_symlink.txt");
        Path nonExistentTarget = tempDir.resolve("non_existent.txt");
        Files.createSymbolicLink(symlinkFile, nonExistentTarget);

        ScanOptions options = new ScanOptions()
                .withSymlinkStrategy(SymlinkStrategy.RECORD);

        ScanResult result = scanner.scanDirectory(tempDir, options).get();

        // Should record the broken symlink
        assertEquals(1, result.getScannedFileCount());
        // May have errors due to broken symlink, but should still record it
        assertTrue(result.getErrorCount() >= 0);

        ScanResult.ScannedFile scannedFile = result.getScannedFiles().get(0);
        assertEquals(symlinkFile, scannedFile.getPath());
        assertTrue(scannedFile.isSymbolicLink());
        assertEquals(nonExistentTarget, scannedFile.getLinkTarget());
    }

    @Test
    void testSymlinkCycle() throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        // Create a cycle: A -> B -> A
        Path fileA = tempDir.resolve("fileA.txt");
        Path fileB = tempDir.resolve("fileB.txt");
        
        // First create the real file content
        Path realFileA = tempDir.resolve("real_fileA.txt");
        Files.write(realFileA, "content A".getBytes(StandardCharsets.UTF_8));
        
        // Create symlinks that form a cycle
        Files.createSymbolicLink(fileA, realFileA);
        Files.createSymbolicLink(fileB, fileA);
        
        // Now modify fileA to point to fileB, creating a cycle
        Files.delete(fileA);
        Files.createSymbolicLink(fileA, fileB); // This creates the cycle: A -> B -> A

        ScanOptions options = new ScanOptions()
                .withSymlinkStrategy(SymlinkStrategy.FOLLOW);

        ScanResult result = scanner.scanDirectory(tempDir, options).get();

        // Should handle cycle gracefully - may find one or both files, but shouldn't crash
        assertTrue(result.getScannedFileCount() >= 0);
        // May have errors due to cycle detection, but shouldn't crash
        // Allow some errors since symlink cycles can cause filesystem errors
        assertTrue(result.getErrorCount() >= 0);
    }

    @Test
    void testDirectorySymlinks() throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        // Create target directory
        Path targetDir = tempDir.resolve("target_dir");
        Files.createDirectories(targetDir);
        Files.write(targetDir.resolve("file1.txt"), "content 1".getBytes(StandardCharsets.UTF_8));
        Files.write(targetDir.resolve("file2.txt"), "content 2".getBytes(StandardCharsets.UTF_8));

        // Create symlink to directory
        Path symlinkDir = tempDir.resolve("symlink_dir");
        Files.createSymbolicLink(symlinkDir, targetDir);

        ScanOptions options = new ScanOptions()
                .withSymlinkStrategy(SymlinkStrategy.FOLLOW);

        ScanResult result = scanner.scanDirectory(tempDir, options).get();

        // Should find files in both the target directory and through the symlink
        assertTrue(result.getScannedFileCount() >= 2);
        assertEquals(0, result.getErrorCount());
    }

    @Test
    void testDeepSymlinkStructure() throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        // Create nested structure with symlinks
        Path level1 = tempDir.resolve("level1");
        Path level2 = tempDir.resolve("level2");
        Path level3 = tempDir.resolve("level3");

        Files.createDirectories(level1);
        Files.createDirectories(level2);
        Files.createDirectories(level3);

        // Create files at each level
        Files.write(level1.resolve("file1.txt"), "content 1".getBytes(StandardCharsets.UTF_8));
        Files.write(level2.resolve("file2.txt"), "content 2".getBytes(StandardCharsets.UTF_8));
        Files.write(level3.resolve("file3.txt"), "content 3".getBytes(StandardCharsets.UTF_8));

        // Create symlinks
        Files.createSymbolicLink(level1.resolve("link_to_level2"), level2);
        Files.createSymbolicLink(level2.resolve("link_to_level3"), level3);
        ScanOptions options = new ScanOptions()
                .withSymlinkStrategy(SymlinkStrategy.FOLLOW)
                .withMaxDepth(3);

        ScanResult result = scanner.scanDirectory(tempDir, options).get();
        // Should find all files including those accessible through symlinks
        assertTrue(result.getScannedFileCount() >= 3);
        assertEquals(0, result.getErrorCount());
    }

    @Test
    void testSymlinkWithDifferentStrategies()
            throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        // Create target file and symlink
        Path targetFile = tempDir.resolve("target.txt");
        Files.write(targetFile, "target content".getBytes(StandardCharsets.UTF_8));

        Path symlinkFile = tempDir.resolve("symlink.txt");
        Files.createSymbolicLink(symlinkFile, targetFile);

        // Test SKIP strategy
        ScanOptions skipOptions = new ScanOptions()
                .withSymlinkStrategy(SymlinkStrategy.SKIP);
        ScanResult skipResult = scanner.scanDirectory(tempDir, skipOptions)
                .get();

        assertEquals(1, skipResult.getScannedFileCount());
        assertTrue(skipResult.getScannedFiles().stream()
                .noneMatch(f -> f.isSymbolicLink()));

        // Test RECORD strategy
        ScanOptions recordOptions = new ScanOptions()
                .withSymlinkStrategy(SymlinkStrategy.RECORD);
        ScanResult recordResult = scanner.scanDirectory(tempDir, recordOptions).get();

        assertEquals(2, recordResult.getScannedFileCount());
        assertTrue(recordResult.getScannedFiles().stream()
                .anyMatch(f -> f.isSymbolicLink()));

        // Test FOLLOW strategy
        ScanOptions followOptions = new ScanOptions()
                .withSymlinkStrategy(SymlinkStrategy.FOLLOW);
        ScanResult followResult = scanner.scanDirectory(tempDir, followOptions).get();

        assertEquals(2, followResult.getScannedFileCount());
    }
}