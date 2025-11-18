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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for sparse file handling in filesystem scanner and chunking system.
 */
class SparseFileHandlingTest {

    /** Temporary directory for test files. */
    @TempDir
    Path tempDir;

    /** The filesystem scanner under test. */
    private FilesystemScanner scanner;
    /** Scan options with sparse file detection enabled. */
    private ScanOptions options;

    @BeforeEach
    void setUp() {
        scanner = new NioFilesystemScanner();
        options = new ScanOptions().withDetectSparseFiles(true);
    }

    @Test
    void testDetectSparseFileWithHoles() throws Exception {
        // Create a sparse file with holes
        Path sparseFile = createSparseFileWithHoles("sparse_test.dat", 1024 * 1024); // 1MB with holes
        
        ScanResult result = scanner.scanDirectory(tempDir, options).get();
        
        assertEquals(1, result.getScannedFileCount());
        ScanResult.ScannedFile scannedFile = result.getScannedFiles().get(0);
        assertTrue(scannedFile.isSparse(), "File should be detected as sparse");
        assertEquals(1024 * 1024, scannedFile.getSize());
        
        // Clean up
        Files.deleteIfExists(sparseFile);
    }

    @Test
    void testDetectNonSparseFile() throws Exception {
        // Create a regular (non-sparse) file
        Path regularFile = createRegularFile("regular_test.dat", 1024 * 1024); // 1MB
        
        ScanResult result = scanner.scanDirectory(tempDir, options).get();
        
        assertEquals(1, result.getScannedFileCount());
        ScanResult.ScannedFile scannedFile = result.getScannedFiles().get(0);
        assertFalse(scannedFile.isSparse(), "Regular file should not be detected as sparse");
        assertEquals(1024 * 1024, scannedFile.getSize());
        
        // Clean up
        Files.deleteIfExists(regularFile);
    }

    @Test
    void testSparseFileChunking() throws Exception {
        ServiceFactory serviceFactory = new ServiceFactory();
        com.justsyncit.hash.Blake3Service blake3Service = serviceFactory.createBlake3Service();
        FileChunker chunker = new FixedSizeFileChunker(blake3Service);
        
        // Create a sparse file
        Path sparseFile = createSparseFileWithHoles("chunk_sparse_test.dat", 2 * 1024 * 1024); // 2MB
        
        FileChunker.ChunkingOptions chunkingOptions = new FileChunker.ChunkingOptions()
            .withDetectSparseFiles(true)
            .withUseAsyncIO(true);
            
        FileChunker.ChunkingResult result = chunker.chunkFile(sparseFile, chunkingOptions).get();
        
        assertTrue(result.isSuccess(), "Chunking sparse file should succeed");
        assertTrue(result.getChunkCount() > 0, "Should create chunks");
        assertEquals(2 * 1024 * 1024, result.getTotalSize());
        
        // Clean up
        Files.deleteIfExists(sparseFile);
    }

    @Test
    void testSparseFileDetectionDisabled() throws Exception {
        // Create a sparse file but disable sparse detection
        ScanOptions disabledOptions = new ScanOptions().withDetectSparseFiles(false);
        Path sparseFile = createSparseFileWithHoles("sparse_disabled_test.dat", 1024 * 1024);
        
        ScanResult result = scanner.scanDirectory(tempDir, disabledOptions).get();
        
        assertEquals(1, result.getScannedFileCount());
        ScanResult.ScannedFile scannedFile = result.getScannedFiles().get(0);
        assertFalse(scannedFile.isSparse(), "Sparse detection disabled - should not be marked as sparse");
        
        // Clean up
        Files.deleteIfExists(sparseFile);
    }

    @Test
    void testLargeSparseFile() throws Exception {
        // Create a large sparse file (10MB logical size, small actual size)
        Path largeSparseFile = createSparseFileWithHoles("large_sparse_test.dat", 10 * 1024 * 1024);
        
        ScanResult result = scanner.scanDirectory(tempDir, options).get();
        
        assertEquals(1, result.getScannedFileCount());
        ScanResult.ScannedFile scannedFile = result.getScannedFiles().get(0);
        assertTrue(scannedFile.isSparse(), "Large sparse file should be detected as sparse");
        assertEquals(10 * 1024 * 1024, scannedFile.getSize());
        
        // Clean up
        Files.deleteIfExists(largeSparseFile);
    }

    /**
     * Creates a sparse file with holes by seeking and writing at specific positions.
     */
    private Path createSparseFileWithHoles(String fileName, int logicalSize) throws IOException {
        Path file = tempDir.resolve(fileName);
        
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
            // Write data at the beginning
            raf.write(new byte[4096]); // 4KB at start
            
            // Create a hole by seeking forward
            raf.seek(64 * 1024); // Seek to 64KB
            raf.write(new byte[4096]); // Write 4KB at 64KB
            
            // Create another hole
            raf.seek(512 * 1024); // Seek to 512KB
            raf.write(new byte[4096]); // Write 4KB at 512KB
            
            // Set the file length to create sparse regions
            raf.setLength(logicalSize);
        }
        
        return file;
    }

    /**
     * Creates a regular (non-sparse) file with continuous data.
     */
    private Path createRegularFile(String fileName, int size) throws IOException {
        Path file = tempDir.resolve(fileName);
        byte[] data = new byte[size];
        // Fill with pattern to ensure it's not sparse
        for (int i = 0; i < size; i++) {
            data[i] = (byte) (i % 256);
        }
        Files.write(file, data);
        return file;
    }
}