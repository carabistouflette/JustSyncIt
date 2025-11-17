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
import com.justsyncit.hash.Blake3Service;
import com.justsyncit.storage.ContentStore;
import com.justsyncit.storage.ContentStoreFactory;
import com.justsyncit.storage.metadata.MetadataService;
import com.justsyncit.storage.metadata.MetadataServiceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.FileSystems;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for FileProcessor with ContentStore and MetadataService.
 * Tests the complete workflow from scanning to storage.
 */
class FileProcessorIntegrationTest {

    @TempDir
    Path tempDir;
    
    private FileProcessor fileProcessor;
    private ContentStore contentStore;
    private MetadataService metadataService;
    private Blake3Service blake3Service;
    private Path testDb;
    private ServiceFactory serviceFactory;

    @BeforeEach
    void setUp() throws Exception {
        // Create test database
        testDb = tempDir.resolve("test.db");
        
        // Initialize services
        serviceFactory = new ServiceFactory();
        blake3Service = serviceFactory.createBlake3Service();
        contentStore = ContentStoreFactory.createMemoryStore(blake3Service);
        metadataService = MetadataServiceFactory.createFileBasedService(testDb.toString());
        
        // Create scanner and chunker
        FilesystemScanner scanner = new NioFilesystemScanner();
        FileChunker chunker = new FixedSizeFileChunker(blake3Service);
        
        // Create file processor
        fileProcessor = new FileProcessor(scanner, chunker, contentStore, metadataService);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (fileProcessor != null) {
            fileProcessor.stop();
        }
        if (metadataService != null) {
            metadataService.close();
        }
    }

    @Test
    void testProcessDirectoryWithMultipleFiles() throws Exception {
        // Create test files
        Path file1 = tempDir.resolve("file1.txt");
        Path file2 = tempDir.resolve("file2.txt");
        Path subdir = tempDir.resolve("subdir");
        Files.createDirectories(subdir);
        Path file3 = subdir.resolve("file3.txt");
        
        Files.writeString(file1, "Hello World 1");
        Files.writeString(file2, "Hello World 2");
        Files.writeString(file3, "Hello World 3");
        
        // Process directory
        PathMatcher includePattern = FileSystems.getDefault().getPathMatcher("glob:*.txt");
        ScanOptions options = new ScanOptions()
                .withIncludePattern(includePattern);
        
        CompletableFuture<FileProcessor.ProcessingResult> future = fileProcessor.processDirectory(tempDir, options);
        FileProcessor.ProcessingResult result = future.get();
        
        // Verify results
        assertNotNull(result);
        assertEquals(3, result.getProcessedFiles());
        assertEquals(0, result.getErrorFiles());
        
        // Verify metadata was created - get files from the scan result
        List<ScanResult.ScannedFile> scannedFiles = result.getScanResult().getScannedFiles();
        assertEquals(3, scannedFiles.size());
        
        // Verify content was processed
        assertTrue(result.getTotalBytes() > 0, "Should have processed bytes");
    }

    @Test
    void testProcessEmptyDirectory() throws Exception {
        // Process empty directory
        ScanOptions options = new ScanOptions();
        CompletableFuture<FileProcessor.ProcessingResult> future = fileProcessor.processDirectory(tempDir, options);
        FileProcessor.ProcessingResult result = future.get();
        
        // Verify results
        assertNotNull(result);
        assertEquals(0, result.getProcessedFiles());
        assertEquals(0, result.getErrorFiles());
    }

    @Test
    void testProcessDirectoryWithLargeFile() throws Exception {
        // Create a large file (>64KB to test chunking)
        Path largeFile = tempDir.resolve("large.txt");
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            content.append("This is line ").append(i).append(" of the large file.\n");
        }
        Files.writeString(largeFile, content.toString());
        
        // Process directory
        ScanOptions options = new ScanOptions();
        CompletableFuture<FileProcessor.ProcessingResult> future = fileProcessor.processDirectory(tempDir, options);
        FileProcessor.ProcessingResult result = future.get();
        
        // Verify results
        assertNotNull(result);
        assertEquals(1, result.getProcessedFiles());
        assertEquals(0, result.getErrorFiles());
        
        // Verify file was processed
        List<ScanResult.ScannedFile> scannedFiles = result.getScanResult().getScannedFiles();
        assertEquals(1, scannedFiles.size());
        
        // Get the file metadata from the database to verify chunking
        List<com.justsyncit.storage.metadata.Snapshot> snapshots = metadataService.listSnapshots();
        assertEquals(1, snapshots.size());
        
        List<com.justsyncit.storage.metadata.FileMetadata> files = metadataService.getFilesInSnapshot(snapshots.get(0).getId());
        assertEquals(1, files.size());
        
        com.justsyncit.storage.metadata.FileMetadata fileMeta = files.get(0);
        assertTrue(fileMeta.getChunkHashes().size() > 1, "Large file should be chunked");
    }

    @Test
    void testProcessDirectoryWithFiltering() throws Exception {
        // Create test files with different extensions
        Files.writeString(tempDir.resolve("file1.txt"), "content1");
        Files.writeString(tempDir.resolve("file2.log"), "content2");
        Files.writeString(tempDir.resolve("file3.txt"), "content3");
        Files.writeString(tempDir.resolve("file4.dat"), "content4");
        
        // Process with filter
        PathMatcher includePattern = FileSystems.getDefault().getPathMatcher("glob:*.txt");
        ScanOptions options = new ScanOptions()
                .withIncludePattern(includePattern);
        
        CompletableFuture<FileProcessor.ProcessingResult> future = fileProcessor.processDirectory(tempDir, options);
        FileProcessor.ProcessingResult result = future.get();
        
        // Verify only .txt files were processed
        assertEquals(2, result.getProcessedFiles());
        assertEquals(0, result.getErrorFiles());
        
        List<ScanResult.ScannedFile> scannedFiles = result.getScanResult().getScannedFiles();
        assertEquals(2, scannedFiles.size());
        assertTrue(scannedFiles.stream().allMatch(f -> f.getPath().toString().endsWith(".txt")));
    }

    @Test
    void testProcessDirectoryWithSymlinks() throws Exception {
        // Create test files and symlinks
        Path targetFile = tempDir.resolve("target.txt");
        Path symlinkFile = tempDir.resolve("symlink.txt");
        
        Files.writeString(targetFile, "target content");
        Files.createSymbolicLink(symlinkFile, targetFile);
        
        // Process with symlink recording
        ScanOptions options = new ScanOptions()
                .withSymlinkStrategy(SymlinkStrategy.RECORD);
        
        CompletableFuture<FileProcessor.ProcessingResult> future = fileProcessor.processDirectory(tempDir, options);
        FileProcessor.ProcessingResult result = future.get();
        
        // Verify both file and symlink were processed
        assertEquals(2, result.getProcessedFiles());
        assertEquals(0, result.getErrorFiles());
        
        List<ScanResult.ScannedFile> scannedFiles = result.getScanResult().getScannedFiles();
        assertEquals(2, scannedFiles.size());
    }

    @Test
    void testProcessDirectoryWithHiddenFiles() throws Exception {
        // Create hidden and regular files
        Files.writeString(tempDir.resolve("regular.txt"), "regular content");
        Files.writeString(tempDir.resolve(".hidden.txt"), "hidden content");
        
        // Process without hidden files
        ScanOptions options = new ScanOptions()
                .withIncludeHiddenFiles(false);
        
        CompletableFuture<FileProcessor.ProcessingResult> future = fileProcessor.processDirectory(tempDir, options);
        FileProcessor.ProcessingResult result = future.get();
        
        // Verify only regular file was processed
        assertEquals(1, result.getProcessedFiles());
        assertEquals(0, result.getErrorFiles());
        
        List<ScanResult.ScannedFile> scannedFiles = result.getScanResult().getScannedFiles();
        assertEquals(1, scannedFiles.size());
        assertTrue(scannedFiles.get(0).getPath().toString().endsWith("regular.txt"));
    }

    @Test
    void testProcessDirectoryErrorHandling() throws Exception {
        // Create a file and make it unreadable
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "content");
        testFile.toFile().setReadable(false);
        
        // Process directory
        ScanOptions options = new ScanOptions();
        CompletableFuture<FileProcessor.ProcessingResult> future = fileProcessor.processDirectory(tempDir, options);
        FileProcessor.ProcessingResult result = future.get();
        
        // Verify error was handled
        assertEquals(0, result.getProcessedFiles());
        assertTrue(result.getErrorFiles() > 0 || result.getSkippedFiles() > 0);
        
        // Restore permissions for cleanup
        testFile.toFile().setReadable(true);
    }

    @Test
    void testAsyncVsSyncProcessing() throws Exception {
        // Create test files
        Path file1 = tempDir.resolve("file1.txt");
        Path file2 = tempDir.resolve("file2.txt");
        
        Files.writeString(file1, "Hello World 1");
        Files.writeString(file2, "Hello World 2");
        
        // Test async processing
        ScanOptions asyncOptions = new ScanOptions()
                .withDetectSparseFiles(true); // This enables async IO
        
        CompletableFuture<FileProcessor.ProcessingResult> asyncFuture = fileProcessor.processDirectory(tempDir, asyncOptions);
        FileProcessor.ProcessingResult asyncResult = asyncFuture.get();
        
        // Clean up metadata for sync test
        metadataService.close();
        testDb = tempDir.resolve("test_sync.db");
        metadataService = MetadataServiceFactory.createFileBasedService(testDb.toString());
        
        // Recreate file processor with new metadata service
        FilesystemScanner scanner = new NioFilesystemScanner();
        FileChunker chunker = new FixedSizeFileChunker(blake3Service);
        fileProcessor = new FileProcessor(scanner, chunker, contentStore, metadataService);
        
        // Test sync processing
        ScanOptions syncOptions = new ScanOptions()
                .withDetectSparseFiles(false); // This disables async IO
        
        CompletableFuture<FileProcessor.ProcessingResult> syncFuture = fileProcessor.processDirectory(tempDir, syncOptions);
        FileProcessor.ProcessingResult syncResult = syncFuture.get();
        
        // Verify both methods produce same results
        assertEquals(asyncResult.getProcessedFiles(), syncResult.getProcessedFiles());
        assertEquals(asyncResult.getErrorFiles(), syncResult.getErrorFiles());
    }
}