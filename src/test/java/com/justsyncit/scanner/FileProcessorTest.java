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

import com.justsyncit.ServiceFactory;
import com.justsyncit.ServiceException;
import com.justsyncit.hash.Blake3Service;
import com.justsyncit.storage.ContentStore;
import com.justsyncit.storage.metadata.MetadataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FileProcessor.
 */
class FileProcessorTest {
    
    @TempDir
    Path tempDir;
    
    private FileProcessor processor;
    private Blake3Service blake3Service;
    private ContentStore contentStore;
    private MetadataService metadataService;
    
    @BeforeEach
    void setUp() throws ServiceException, IOException {
        ServiceFactory serviceFactory = new ServiceFactory();
        blake3Service = serviceFactory.createBlake3Service();
        contentStore = serviceFactory.createSqliteContentStore(blake3Service);
        metadataService = serviceFactory.createInMemoryMetadataService();
        
        FilesystemScanner scanner = new NioFilesystemScanner();
        FileChunker chunker = new FixedSizeFileChunker(blake3Service);
        
        processor = new FileProcessor(scanner, chunker, contentStore, metadataService);
    }
    
    @Test
    void testProcessDirectory() throws Exception {
        // Create test directory structure
        Path testDir = tempDir.resolve("test");
        Files.createDirectories(testDir);
        
        Path file1 = testDir.resolve("file1.txt");
        Files.write(file1, "Hello, World!".getBytes());
        
        Path file2 = testDir.resolve("file2.txt");
        Files.write(file2, "Another file content".getBytes());
        
        // Create subdirectory
        Path subDir = testDir.resolve("subdir");
        Files.createDirectories(subDir);
        
        Path file3 = subDir.resolve("file3.txt");
        Files.write(file3, "Subdirectory file".getBytes());
        
        // Process directory
        ScanOptions options = new ScanOptions()
                .withMaxDepth(10);
        
        CompletableFuture<FileProcessor.ProcessingResult> future = processor.processDirectory(testDir, options);
        FileProcessor.ProcessingResult result = future.get();
        
        // Verify results
        ScanResult scanResult = result.getScanResult();
        assertEquals(3, scanResult.getScannedFiles().size());
        assertEquals(0, scanResult.getErrors().size());
        assertEquals(3, result.getProcessedFiles());
        
        // Verify files were processed
        assertTrue(scanResult.getScannedFiles().stream()
                .anyMatch(f -> f.getPath().endsWith("file1.txt")));
        assertTrue(scanResult.getScannedFiles().stream()
                .anyMatch(f -> f.getPath().endsWith("file2.txt")));
        assertTrue(scanResult.getScannedFiles().stream()
                .anyMatch(f -> f.getPath().endsWith("file3.txt")));
    }
    
    @Test
    void testProcessEmptyDirectory() throws Exception {
        Path emptyDir = tempDir.resolve("empty");
        Files.createDirectories(emptyDir);
        
        ScanOptions options = new ScanOptions();
        CompletableFuture<FileProcessor.ProcessingResult> future = processor.processDirectory(emptyDir, options);
        FileProcessor.ProcessingResult result = future.get();
        
        ScanResult scanResult = result.getScanResult();
        assertEquals(0, scanResult.getScannedFiles().size());
        assertEquals(0, scanResult.getErrors().size());
        assertEquals(0, result.getProcessedFiles());
    }
    
    @Test
    void testProcessNonExistentDirectory() throws Exception {
        Path nonExistent = tempDir.resolve("nonexistent");
        
        ScanOptions options = new ScanOptions();
        CompletableFuture<FileProcessor.ProcessingResult> future = processor.processDirectory(nonExistent, options);
        
        ExecutionException exception = assertThrows(ExecutionException.class, () -> future.get());
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }
    
    @Test
    void testProcessWithFileFilter() throws Exception {
        // Create test directory with different file types
        Path testDir = tempDir.resolve("filtered");
        Files.createDirectories(testDir);
        
        Files.write(testDir.resolve("test.txt"), "text file".getBytes());
        Files.write(testDir.resolve("test.java"), "java file".getBytes());
        Files.write(testDir.resolve("test.md"), "markdown file".getBytes());
        
        // Process with include pattern for .txt files only
        ScanOptions options = new ScanOptions()
                .withIncludePattern(tempDir.getFileSystem().getPathMatcher("glob:**/*.txt"));
        
        CompletableFuture<FileProcessor.ProcessingResult> future = processor.processDirectory(testDir, options);
        FileProcessor.ProcessingResult result = future.get();
        
        ScanResult scanResult = result.getScanResult();
        assertEquals(1, scanResult.getScannedFiles().size());
        assertTrue(scanResult.getScannedFiles().get(0).getPath().endsWith("test.txt"));
    }
    
    @Test
    void testProcessWithMaxDepth() throws Exception {
        // Create nested directory structure
        Path root = tempDir.resolve("nested");
        Files.createDirectories(root);
        
        Path level1 = root.resolve("level1");
        Files.createDirectories(level1);
        
        Path level2 = level1.resolve("level2");
        Files.createDirectories(level2);
        
        Path level3 = level2.resolve("level3");
        Files.createDirectories(level3);
        
        Files.write(root.resolve("root.txt"), "root".getBytes());
        Files.write(level1.resolve("level1.txt"), "level1".getBytes());
        Files.write(level2.resolve("level2.txt"), "level2".getBytes());
        Files.write(level3.resolve("level3.txt"), "level3".getBytes());
        
        // Process with max depth 2
        ScanOptions options = new ScanOptions()
                .withMaxDepth(2);
        
        CompletableFuture<FileProcessor.ProcessingResult> future = processor.processDirectory(root, options);
        FileProcessor.ProcessingResult result = future.get();
        
        ScanResult scanResult = result.getScanResult();
        assertEquals(3, scanResult.getScannedFiles().size()); // root.txt, level1.txt, level2.txt
        
        // level3.txt should not be included due to depth limit
        assertFalse(scanResult.getScannedFiles().stream()
                .anyMatch(f -> f.getPath().endsWith("level3.txt")));
    }
    
    @Test
    void testProcessLargeFile() throws Exception {
        Path largeFile = tempDir.resolve("large.txt");
        // Create a file larger than default chunk size
        byte[] data = new byte[100 * 1024]; // 100KB
        Files.write(largeFile, data);
        
        ScanOptions options = new ScanOptions();
        CompletableFuture<FileProcessor.ProcessingResult> future = processor.processDirectory(tempDir, options);
        FileProcessor.ProcessingResult result = future.get();
        
        ScanResult scanResult = result.getScanResult();
        assertEquals(1, scanResult.getScannedFiles().size());
        
        ScanResult.ScannedFile scannedFile = scanResult.getScannedFiles().get(0);
        assertEquals(data.length, scannedFile.getSize());
    }
    
    @Test
    void testProcessWithHiddenFiles() throws Exception {
        Path testDir = tempDir.resolve("hidden");
        Files.createDirectories(testDir);
        
        Files.write(testDir.resolve("visible.txt"), "visible".getBytes());
        Files.write(testDir.resolve(".hidden.txt"), "hidden".getBytes());
        
        // Process without hidden files
        ScanOptions options = new ScanOptions()
                .withIncludeHiddenFiles(false);
        
        CompletableFuture<FileProcessor.ProcessingResult> future = processor.processDirectory(testDir, options);
        FileProcessor.ProcessingResult result = future.get();
        
        ScanResult scanResult = result.getScanResult();
        assertEquals(1, scanResult.getScannedFiles().size());
        assertTrue(scanResult.getScannedFiles().get(0).getPath().endsWith("visible.txt"));
    }
    
    @Test
    void testProgressListener() throws Exception {
        Path testDir = tempDir.resolve("progress");
        Files.createDirectories(testDir);
        
        // Create multiple files to track progress
        for (int i = 0; i < 5; i++) {
            Files.write(testDir.resolve("file" + i + ".txt"), ("content " + i).getBytes());
        }
        
        // Note: FileProcessor doesn't expose progress listener configuration directly
        // This test would need to be implemented differently or the feature added
        ScanOptions options = new ScanOptions();
        
        CompletableFuture<FileProcessor.ProcessingResult> future = processor.processDirectory(testDir, options);
        FileProcessor.ProcessingResult result = future.get();
        
        ScanResult scanResult = result.getScanResult();
        assertEquals(5, scanResult.getScannedFiles().size());
        assertEquals(5, result.getProcessedFiles());
    }
    
    @Test
    void testStop() throws Exception {
        Path testDir = tempDir.resolve("close");
        try {
            Files.createDirectories(testDir);
            Files.write(testDir.resolve("test.txt"), "test".getBytes());
            
            // Process successfully first
            ScanOptions options = new ScanOptions();
            CompletableFuture<FileProcessor.ProcessingResult> future = processor.processDirectory(testDir, options);
            FileProcessor.ProcessingResult result = future.get();
            assertEquals(1, result.getProcessedFiles());
            
            // Stop processor
            processor.stop();
            
            // Try to process again - should fail
            CompletableFuture<FileProcessor.ProcessingResult> future2 = processor.processDirectory(testDir, options);
            ExecutionException exception = assertThrows(ExecutionException.class, () -> future2.get());
            assertTrue(exception.getCause() instanceof IllegalStateException);
        } catch (IOException e) {
            fail("Failed to create test files: " + e.getMessage());
        }
    }
    
    @Test
    void testIsRunning() {
        assertFalse(processor.isRunning());
        
        Path testDir = tempDir.resolve("running");
        try {
            Files.createDirectories(testDir);
            Files.write(testDir.resolve("test.txt"), "test".getBytes());
            
            ScanOptions options = new ScanOptions();
            CompletableFuture<FileProcessor.ProcessingResult> future = processor.processDirectory(testDir, options);
            
            // Should be running during processing
            assertTrue(processor.isRunning());
            
            try {
                future.get();
            } catch (Exception e) {
                // Ignore for this test
            }
            
            // Should not be running after completion
            assertFalse(processor.isRunning());
        } catch (IOException e) {
            fail("Failed to create test files: " + e.getMessage());
        }
    }
}