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

import com.justsyncit.ServiceFactory;
import com.justsyncit.ServiceException;
import com.justsyncit.hash.Blake3Service;
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
 * Unit tests for FixedSizeFileChunker.
 */
class FixedSizeFileChunkerTest {
    
    @TempDir
    Path tempDir;
    
    private FixedSizeFileChunker chunker;
    private Blake3Service blake3Service;
    
    @BeforeEach
    void setUp() throws ServiceException {
        ServiceFactory serviceFactory = new ServiceFactory();
        blake3Service = serviceFactory.createBlake3Service();
        chunker = new FixedSizeFileChunker(blake3Service);
    }
    
    @Test
    void testChunkEmptyFile() throws Exception {
        Path emptyFile = tempDir.resolve("empty.txt");
        Files.createFile(emptyFile);
        
        FileChunker.ChunkingResult result = chunker.chunkFile(emptyFile, new FileChunker.ChunkingOptions()).get();
        
        assertTrue(result.isSuccess());
        assertEquals(0, result.getChunkCount());
        assertEquals(0, result.getTotalSize());
        assertEquals(0, result.getSparseSize());
        assertNotNull(result.getFileHash());
        assertTrue(result.getChunkHashes().isEmpty());
    }
    
    @Test
    void testChunkSmallFile() throws Exception {
        Path smallFile = tempDir.resolve("small.txt");
        byte[] data = "Hello, World!".getBytes();
        Files.write(smallFile, data);
        
        FileChunker.ChunkingResult result = chunker.chunkFile(smallFile, new FileChunker.ChunkingOptions()).get();
        
        assertTrue(result.isSuccess());
        assertEquals(1, result.getChunkCount());
        assertEquals(data.length, result.getTotalSize());
        assertEquals(0, result.getSparseSize());
        assertNotNull(result.getFileHash());
        assertEquals(1, result.getChunkHashes().size());
    }
    
    @Test
    void testChunkLargeFile() throws Exception {
        Path largeFile = tempDir.resolve("large.txt");
        // Create file larger than default chunk size (64KB)
        byte[] data = new byte[100 * 1024]; // 100KB
        Files.write(largeFile, data);
        
        FileChunker.ChunkingResult result = chunker.chunkFile(largeFile, new FileChunker.ChunkingOptions()).get();
        
        assertTrue(result.isSuccess());
        assertEquals(2, result.getChunkCount()); // 100KB / 64KB = 2 chunks
        assertEquals(data.length, result.getTotalSize());
        assertEquals(0, result.getSparseSize());
        assertNotNull(result.getFileHash());
        assertEquals(2, result.getChunkHashes().size());
    }
    
    @Test
    void testChunkWithCustomChunkSize() throws Exception {
        Path file = tempDir.resolve("custom.txt");
        byte[] data = new byte[32 * 1024]; // 32KB
        Files.write(file, data);
        
        FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions().withChunkSize(16 * 1024); // 16KB chunks
        FileChunker.ChunkingResult result = chunker.chunkFile(file, options).get();
        
        assertTrue(result.isSuccess());
        assertEquals(2, result.getChunkCount()); // 32KB / 16KB = 2 chunks
        assertEquals(data.length, result.getTotalSize());
        assertEquals(0, result.getSparseSize());
        assertNotNull(result.getFileHash());
        assertEquals(2, result.getChunkHashes().size());
    }
    
    @Test
    void testChunkWithAsyncIO() throws Exception {
        Path file = tempDir.resolve("async.txt");
        byte[] data = new byte[64 * 1024]; // Exactly 64KB
        Files.write(file, data);
        
        FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions().withUseAsyncIO(true);
        FileChunker.ChunkingResult result = chunker.chunkFile(file, options).get();
        
        assertTrue(result.isSuccess());
        assertEquals(1, result.getChunkCount());
        assertEquals(data.length, result.getTotalSize());
        assertEquals(0, result.getSparseSize());
        assertNotNull(result.getFileHash());
        assertEquals(1, result.getChunkHashes().size());
    }
    
    @Test
    void testChunkWithSyncIO() throws Exception {
        Path file = tempDir.resolve("sync.txt");
        byte[] data = new byte[64 * 1024]; // Exactly 64KB
        Files.write(file, data);
        
        FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions().withUseAsyncIO(false);
        FileChunker.ChunkingResult result = chunker.chunkFile(file, options).get();
        
        assertTrue(result.isSuccess());
        assertEquals(1, result.getChunkCount());
        assertEquals(data.length, result.getTotalSize());
        assertEquals(0, result.getSparseSize());
        assertNotNull(result.getFileHash());
        assertEquals(1, result.getChunkHashes().size());
    }
    
    @Test
    void testChunkNonExistentFile() throws Exception {
        Path nonExistentFile = tempDir.resolve("nonexistent.txt");
        
        CompletableFuture<FileChunker.ChunkingResult> future = chunker.chunkFile(nonExistentFile, new FileChunker.ChunkingOptions());
        
        ExecutionException exception = assertThrows(ExecutionException.class, () -> future.get());
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }
    
    @Test
    void testChunkDirectory() throws Exception {
        Path dir = tempDir.resolve("directory");
        Files.createDirectories(dir);
        
        CompletableFuture<FileChunker.ChunkingResult> future = chunker.chunkFile(dir, new FileChunker.ChunkingOptions());
        
        ExecutionException exception = assertThrows(ExecutionException.class, () -> future.get());
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }
    
    @Test
    void testStoreChunk() throws Exception {
        byte[] data = "test data".getBytes();
        
        String hash = chunker.storeChunk(data);
        
        assertNotNull(hash);
        assertFalse(hash.isEmpty());
    }
    
    @Test
    void testRetrieveChunkNotImplemented() {
        assertThrows(UnsupportedOperationException.class, 
            () -> chunker.retrieveChunk("testhash"));
    }
    
    @Test
    void testExistsChunkNotImplemented() {
        assertThrows(UnsupportedOperationException.class, 
            () -> chunker.existsChunk("testhash"));
    }
    
    @Test
    void testSetChunkSize() {
        int newChunkSize = 32 * 1024; // 32KB
        chunker.setChunkSize(newChunkSize);
        
        assertEquals(newChunkSize, chunker.getChunkSize());
    }
    
    @Test
    void testSetInvalidChunkSize() {
        assertThrows(IllegalArgumentException.class, 
            () -> chunker.setChunkSize(0));
        assertThrows(IllegalArgumentException.class, 
            () -> chunker.setChunkSize(-1));
    }
    
    @Test
    void testSetBufferPool() {
        BufferPool newPool = new ByteBufferPool();
        chunker.setBufferPool(newPool);
        
        // No exception should be thrown
        assertDoesNotThrow(() -> chunker.setChunkSize(1024));
    }
    
    @Test
    void testSetNullBufferPool() {
        assertThrows(IllegalArgumentException.class, 
            () -> chunker.setBufferPool(null));
    }
    
    @Test
    void testClose() throws Exception {
        Path file = tempDir.resolve("close.txt");
        Files.write(file, "test".getBytes());
        
        // Chunk file successfully
        FileChunker.ChunkingResult result = chunker.chunkFile(file, new FileChunker.ChunkingOptions()).get();
        assertTrue(result.isSuccess());
        
        // Close chunker
        chunker.close();
        
        // Try to chunk again - should fail
        CompletableFuture<FileChunker.ChunkingResult> future = chunker.chunkFile(file, new FileChunker.ChunkingOptions());
        ExecutionException exception = assertThrows(ExecutionException.class, () -> future.get());
        assertTrue(exception.getCause() instanceof IllegalStateException);
    }
    
    @Test
    void testChunkingOptions() {
        FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions()
            .withChunkSize(32 * 1024)
            .withUseAsyncIO(false)
            .withDetectSparseFiles(false);
        
        assertEquals(32 * 1024, options.getChunkSize());
        assertFalse(options.isUseAsyncIO());
        assertFalse(options.isDetectSparseFiles());
        
        // Test copy constructor
        FileChunker.ChunkingOptions copy = new FileChunker.ChunkingOptions(options);
        assertEquals(options.getChunkSize(), copy.getChunkSize());
        assertEquals(options.isUseAsyncIO(), copy.isUseAsyncIO());
        assertEquals(options.isDetectSparseFiles(), copy.isDetectSparseFiles());
    }
    
    @Test
    void testChunkingOptionsValidation() {
        assertThrows(IllegalArgumentException.class,
            () -> new FileChunker.ChunkingOptions().withChunkSize(0));
        assertThrows(IllegalArgumentException.class,
            () -> new FileChunker.ChunkingOptions().withChunkSize(-1));
        
        // Valid chunk size should not throw
        assertDoesNotThrow(() -> new FileChunker.ChunkingOptions().withChunkSize(1024));
    }
}