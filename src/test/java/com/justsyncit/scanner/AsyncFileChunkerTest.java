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
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AsyncFileChunker.
 * Tests the CompletionHandler pattern and async chunking operations.
 */
class AsyncFileChunkerTest {
    /** Temporary directory for test files. */
    @TempDir
    Path tempDir;

    /** The async file chunker under test. */
    private AsyncFileChunker asyncChunker;

    /** The BLAKE3 hashing service. */
    private Blake3Service blake3Service;

    /** Mock async buffer pool. */
    @Mock
    private AsyncByteBufferPool mockAsyncBufferPool;

    /** Mock async chunk handler. */
    @Mock
    private AsyncChunkHandler mockAsyncChunkHandler;

    @BeforeEach
    void setUp() throws ServiceException {
        MockitoAnnotations.openMocks(this);
        ServiceFactory serviceFactory = new ServiceFactory();
        blake3Service = serviceFactory.createBlake3Service();

        // Create async chunker with real dependencies for integration tests
        asyncChunker = AsyncFileChunkerImpl.create(blake3Service);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        if (asyncChunker != null) {
            try {
                // Close async to clean up resources (executor service, etc.)
                asyncChunker.closeAsync().get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                System.err.println("Failed to close asyncChunker in tearDown: " + e.getMessage());
            }
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testChunkEmptyFileWithCompletionHandler() throws IOException, InterruptedException {
        Path emptyFile = tempDir.resolve("empty.txt");
        Files.createFile(emptyFile);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<FileChunker.ChunkingResult> resultRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        CompletionHandler<FileChunker.ChunkingResult, Exception> handler = new CompletionHandler<>() {
            @Override
            public void completed(FileChunker.ChunkingResult result) {
                resultRef.set(result);
                latch.countDown();
            }

            @Override
            public void failed(Exception exception) {
                errorRef.set(exception);
                latch.countDown();
            }
        };

        asyncChunker.chunkFileAsync(emptyFile, new ChunkingOptions(), handler);

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Operation should complete within timeout");
        assertNull(errorRef.get(), "Should not have any errors");

        FileChunker.ChunkingResult result = resultRef.get();
        assertNotNull(result, "Result should not be null");
        assertTrue(result.isSuccess(), "Chunking should be successful");
        assertEquals(0, result.getChunkCount(), "Empty file should have 0 chunks");
        assertEquals(0, result.getTotalSize(), "Empty file should have 0 size");
        assertNotNull(result.getFileHash(), "File hash should not be null");
        assertTrue(result.getChunkHashes().isEmpty(), "Chunk hashes should be empty");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testChunkSmallFileWithCompletionHandler() throws IOException, InterruptedException {
        Path smallFile = tempDir.resolve("small.txt");
        byte[] data = "Hello, Async World!".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(smallFile, data);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<FileChunker.ChunkingResult> resultRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        CompletionHandler<FileChunker.ChunkingResult, Exception> handler = new CompletionHandler<>() {
            @Override
            public void completed(FileChunker.ChunkingResult result) {
                resultRef.set(result);
                latch.countDown();
            }

            @Override
            public void failed(Exception exception) {
                errorRef.set(exception);
                latch.countDown();
            }
        };

        asyncChunker.chunkFileAsync(smallFile, new ChunkingOptions(), handler);

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Operation should complete within timeout");
        assertNull(errorRef.get(), "Should not have any errors");

        FileChunker.ChunkingResult result = resultRef.get();
        assertNotNull(result, "Result should not be null");
        assertTrue(result.isSuccess(), "Chunking should be successful");
        assertEquals(1, result.getChunkCount(), "Small file should have 1 chunk");
        assertEquals(data.length, result.getTotalSize(), "File size should match");
        assertNotNull(result.getFileHash(), "File hash should not be null");
        assertEquals(1, result.getChunkHashes().size(), "Should have 1 chunk hash");
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testChunkLargeFileWithCompletionHandler() throws IOException, InterruptedException {
        Path largeFile = tempDir.resolve("large.txt");
        // Create file larger than default chunk size (64KB)
        byte[] data = new byte[200 * 1024]; // 200KB
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 256);
        }
        Files.write(largeFile, data);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<FileChunker.ChunkingResult> resultRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        CompletionHandler<FileChunker.ChunkingResult, Exception> handler = new CompletionHandler<>() {
            @Override
            public void completed(FileChunker.ChunkingResult result) {
                resultRef.set(result);
                latch.countDown();
            }

            @Override
            public void failed(Exception exception) {
                errorRef.set(exception);
                latch.countDown();
            }
        };

        ChunkingOptions options = new ChunkingOptions()
                .withChunkSize(64 * 1024); // 64KB chunks
        asyncChunker.chunkFileAsync(largeFile, options, handler);

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Operation should complete within timeout");
        assertNull(errorRef.get(), "Should not have any errors");

        FileChunker.ChunkingResult result = resultRef.get();
        assertNotNull(result, "Result should not be null");
        assertTrue(result.isSuccess(), "Chunking should be successful");
        assertEquals(4, result.getChunkCount(), "200KB / 64KB = 4 chunks");
        assertEquals(data.length, result.getTotalSize(), "File size should match");
        assertNotNull(result.getFileHash(), "File hash should not be null");
        assertEquals(4, result.getChunkHashes().size(), "Should have 4 chunk hashes");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testChunkFileAsyncWithCompletableFuture()
            throws IOException, ExecutionException, InterruptedException, TimeoutException {
        Path file = tempDir.resolve("future.txt");
        byte[] data = "Test CompletableFuture integration".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(file, data);

        CompletableFuture<FileChunker.ChunkingResult> future = asyncChunker.chunkFileAsync(file,
                new ChunkingOptions());

        FileChunker.ChunkingResult result = future.get(5, TimeUnit.SECONDS);

        assertNotNull(result, "Result should not be null");
        assertTrue(result.isSuccess(), "Chunking should be successful");
        assertEquals(1, result.getChunkCount(), "Should have 1 chunk");
        assertEquals(data.length, result.getTotalSize(), "File size should match");
        assertNotNull(result.getFileHash(), "File hash should not be null");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testChunkNonExistentFileWithCompletionHandler() throws InterruptedException {
        Path nonExistentFile = tempDir.resolve("nonexistent.txt");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<FileChunker.ChunkingResult> resultRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        CompletionHandler<FileChunker.ChunkingResult, Exception> handler = new CompletionHandler<>() {
            @Override
            public void completed(FileChunker.ChunkingResult result) {
                resultRef.set(result);
                latch.countDown();
            }

            @Override
            public void failed(Exception exception) {
                errorRef.set(exception);
                latch.countDown();
            }
        };

        asyncChunker.chunkFileAsync(nonExistentFile, new ChunkingOptions(), handler);

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Operation should complete within timeout");
        assertNull(resultRef.get(), "Should not have a successful result");
        assertNotNull(errorRef.get(), "Should have an error");
        assertTrue(errorRef.get() instanceof IllegalArgumentException, "Should be IllegalArgumentException");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testChunkDirectoryWithCompletionHandler() throws InterruptedException {
        Path dir = tempDir.resolve("directory");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            fail("Failed to create test directory: " + e.getMessage());
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<FileChunker.ChunkingResult> resultRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        CompletionHandler<FileChunker.ChunkingResult, Exception> handler = new CompletionHandler<>() {
            @Override
            public void completed(FileChunker.ChunkingResult result) {
                resultRef.set(result);
                latch.countDown();
            }

            @Override
            public void failed(Exception exception) {
                errorRef.set(exception);
                latch.countDown();
            }
        };

        asyncChunker.chunkFileAsync(dir, new ChunkingOptions(), handler);

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Operation should complete within timeout");
        assertNull(resultRef.get(), "Should not have a successful result");
        assertNotNull(errorRef.get(), "Should have an error");
        assertTrue(errorRef.get() instanceof IllegalArgumentException, "Should be IllegalArgumentException");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testSetAsyncBufferPool() {
        AsyncByteBufferPool newPool = mock(AsyncByteBufferPool.class);
        when(mockAsyncBufferPool.acquireAsync(anyInt()))
                .thenReturn(CompletableFuture.completedFuture(ByteBuffer.allocate(1024)));

        assertDoesNotThrow(() -> asyncChunker.setAsyncBufferPool(newPool));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testSetNullAsyncBufferPool() {
        assertThrows(IllegalArgumentException.class,
                () -> asyncChunker.setAsyncBufferPool(null));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testSetChunkSize() {
        int newChunkSize = 32 * 1024; // 32KB
        asyncChunker.setChunkSize(newChunkSize);
        assertEquals(newChunkSize, asyncChunker.getChunkSize());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testSetInvalidChunkSize() {
        assertThrows(IllegalArgumentException.class,
                () -> asyncChunker.setChunkSize(0));
        assertThrows(IllegalArgumentException.class,
                () -> asyncChunker.setChunkSize(-1));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testSetAsyncChunkHandler() {
        assertDoesNotThrow(() -> asyncChunker.setAsyncChunkHandler(mockAsyncChunkHandler));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testSetNullAsyncChunkHandler() {
        assertThrows(IllegalArgumentException.class,
                () -> asyncChunker.setAsyncChunkHandler(null));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testCloseWithCompletionHandler()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        Path file = tempDir.resolve("close.txt");
        Files.write(file, "test".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // First chunk successfully
        CountDownLatch successLatch = new CountDownLatch(1);
        asyncChunker.chunkFileAsync(file, new ChunkingOptions(), new CompletionHandler<>() {
            @Override
            public void completed(FileChunker.ChunkingResult result) {
                successLatch.countDown();
            }

            @Override
            public void failed(Exception exception) {
                successLatch.countDown();
            }
        });

        assertTrue(successLatch.await(5, TimeUnit.SECONDS), "First operation should succeed");

        // Close the chunker
        asyncChunker.closeAsync().get(5, TimeUnit.SECONDS);

        // Try to chunk again - should fail
        CountDownLatch failureLatch = new CountDownLatch(1);
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        asyncChunker.chunkFileAsync(file, new ChunkingOptions(), new CompletionHandler<>() {
            @Override
            public void completed(FileChunker.ChunkingResult result) {
                failureLatch.countDown();
            }

            @Override
            public void failed(Exception exception) {
                errorRef.set(exception);
                failureLatch.countDown();
            }
        });

        assertTrue(failureLatch.await(5, TimeUnit.SECONDS), "Second operation should complete");
        assertNotNull(errorRef.get(), "Should have an error");
        assertTrue(errorRef.get() instanceof IllegalStateException, "Should be IllegalStateException");
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testConcurrentAsyncOperations() throws IOException, InterruptedException {
        int fileCount = 5;
        CountDownLatch latch = new CountDownLatch(fileCount);
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        CompletionHandler<FileChunker.ChunkingResult, Exception> handler = new CompletionHandler<>() {
            @Override
            public void completed(FileChunker.ChunkingResult result) {
                latch.countDown();
            }

            @Override
            public void failed(Exception exception) {
                errorRef.set(exception);
                latch.countDown();
            }
        };

        // Create multiple files and chunk them concurrently
        for (int i = 0; i < fileCount; i++) {
            Path file = tempDir.resolve("concurrent_" + i + ".txt");
            byte[] data = ("Concurrent test data " + i).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            Files.write(file, data);

            asyncChunker.chunkFileAsync(file, new ChunkingOptions(), handler);
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "All operations should complete within timeout");
        assertNull(errorRef.get(), "Should not have any errors during concurrent operations");
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testAsyncChunkingWithCustomOptions() throws IOException, InterruptedException {
        Path file = tempDir.resolve("custom_options.txt");
        byte[] data = new byte[96 * 1024]; // 96KB
        Files.write(file, data);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<FileChunker.ChunkingResult> resultRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        CompletionHandler<FileChunker.ChunkingResult, Exception> handler = new CompletionHandler<>() {
            @Override
            public void completed(FileChunker.ChunkingResult result) {
                resultRef.set(result);
                latch.countDown();
            }

            @Override
            public void failed(Exception exception) {
                errorRef.set(exception);
                latch.countDown();
            }
        };

        ChunkingOptions options = new ChunkingOptions()
                .withChunkSize(32 * 1024) // 32KB chunks
                .withMaxConcurrentChunks(2)
                .withUseAsyncIO(true);

        asyncChunker.chunkFileAsync(file, options, handler);

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Operation should complete within timeout");
        assertNull(errorRef.get(), "Should not have any errors");

        FileChunker.ChunkingResult result = resultRef.get();
        assertNotNull(result, "Result should not be null");
        assertTrue(result.isSuccess(), "Chunking should be successful");
        assertEquals(3, result.getChunkCount(), "96KB / 32KB = 3 chunks");
        assertEquals(data.length, result.getTotalSize(), "File size should match");
    }
}