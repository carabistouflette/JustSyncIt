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

package com.justsyncit.storage;

import com.justsyncit.hash.Blake3BufferHasher;
import com.justsyncit.hash.Blake3FileHasher;
import com.justsyncit.hash.Blake3IncrementalHasherFactory;
import com.justsyncit.hash.Blake3Service;
import com.justsyncit.hash.Blake3ServiceImpl;
import com.justsyncit.hash.Blake3StreamHasher;
import com.justsyncit.hash.BufferHasher;
import com.justsyncit.hash.FileHasher;
import com.justsyncit.hash.HashAlgorithm;
import com.justsyncit.hash.HashingException;
import com.justsyncit.hash.IncrementalHasherFactory;
import com.justsyncit.hash.Sha256HashAlgorithm;
import com.justsyncit.hash.StreamHasher;
import com.justsyncit.simd.SimdDetectionService;
import com.justsyncit.simd.SimdDetectionServiceImpl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integration tests for ContentStore with real Blake3Service.
 */
class ContentStoreIntegrationTest {

    /** Temporary directory for tests. */
    @TempDir
    Path tempDir;

    /** BLAKE3 service instance. */
    private Blake3Service blake3Service;

    /** Content store under test. */
    private ContentStore contentStore;

    /** Storage directory for test files. */
    private Path storageDir;

    /** Index file for chunk metadata. */
    private Path indexFile;

    @BeforeEach
    void setUp() throws IOException, com.justsyncit.hash.HashingException {
        // Create real Blake3Service
        HashAlgorithm hashAlgorithm = Sha256HashAlgorithm.create();
        BufferHasher bufferHasher = new Blake3BufferHasher(hashAlgorithm);
        IncrementalHasherFactory incrementalHasherFactory = new Blake3IncrementalHasherFactory(hashAlgorithm);
        StreamHasher streamHasher = new Blake3StreamHasher(incrementalHasherFactory);
        FileHasher fileHasher = new Blake3FileHasher(streamHasher, bufferHasher);
        SimdDetectionService simdDetectionService = new SimdDetectionServiceImpl();

        blake3Service = new Blake3ServiceImpl(
                fileHasher, bufferHasher, streamHasher,
                incrementalHasherFactory, simdDetectionService);

        // Setup storage directories
        storageDir = tempDir.resolve("storage");
        indexFile = tempDir.resolve("index.txt");

        // Create content store
        FilesystemChunkIndex chunkIndex = FilesystemChunkIndex.create(storageDir, indexFile);
        contentStore = FilesystemContentStore.create(storageDir, chunkIndex, blake3Service);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (contentStore != null) {
            contentStore.close();
        }
    }

    @Test
    void testDeduplication() throws IOException, StorageIntegrityException {
        // Arrange
        byte[] data1 = "Hello, World!".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] data2 = "Hello, World!".getBytes(java.nio.charset.StandardCharsets.UTF_8); // Same content
        byte[] data3 = "Different content".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        // Act
        String hash1 = contentStore.storeChunk(data1);
        String hash2 = contentStore.storeChunk(data2);
        String hash3 = contentStore.storeChunk(data3);

        // Assert
        assertEquals(hash1, hash2); // Same content should have same hash
        assertNotEquals(hash1, hash3); // Different content should have different hash

        // Verify retrieval
        byte[] retrieved1 = contentStore.retrieveChunk(hash1);
        byte[] retrieved2 = contentStore.retrieveChunk(hash2);
        byte[] retrieved3 = contentStore.retrieveChunk(hash3);

        assertArrayEquals(data1, retrieved1);
        assertArrayEquals(data2, retrieved2);
        assertArrayEquals(data3, retrieved3);

        // Verify deduplication - only 2 unique chunks should be stored
        assertEquals(2L, contentStore.getChunkCount());
    }

    @Test
    void testLargeChunkStorage() throws IOException, StorageIntegrityException {
        // Arrange
        byte[] largeData = new byte[1024 * 1024]; // 1MB
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }

        // Act
        String hash = contentStore.storeChunk(largeData);

        // Assert
        assertNotNull(hash);
        assertTrue(contentStore.existsChunk(hash));

        byte[] retrieved = contentStore.retrieveChunk(hash);
        assertArrayEquals(largeData, retrieved);
        assertEquals(largeData.length, contentStore.getTotalSize());
    }

    @Test
    void testGarbageCollection() throws IOException {
        // Arrange
        String hash1 = contentStore.storeChunk("data1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String hash2 = contentStore.storeChunk("data2".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String hash3 = contentStore.storeChunk("data3".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        Set<String> activeHashes = new HashSet<>();
        assertTrue(activeHashes.add(hash1));
        assertTrue(activeHashes.add(hash3)); // hash2 should be garbage collected

        // Act
        long removedCount = contentStore.garbageCollect(activeHashes);

        // Assert
        assertEquals(1L, removedCount);
        assertTrue(contentStore.existsChunk(hash1));
        assertFalse(contentStore.existsChunk(hash2));
        assertTrue(contentStore.existsChunk(hash3));
        assertEquals(2L, contentStore.getChunkCount());
    }

    @Test
    @Disabled("Temporarily disabled due to test environment limitations")
    void testConcurrentStorage() throws InterruptedException, IOException {
        // Arrange
        int threadCount = 10;
        int chunksPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        Set<String> allHashes = new HashSet<>();

        // Act
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.execute(() -> {
                try {
                    for (int j = 0; j < chunksPerThread; j++) {
                        byte[] data = ("thread" + threadId + "_chunk" + j)
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        String hash = contentStore.storeChunk(data);

                        synchronized (allHashes) {
                            assertTrue(allHashes.add(hash));
                        }

                        successCount.incrementAndGet();
                    }
                } catch (IOException e) {
                    fail("Concurrent storage failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        // Assert
        assertEquals(threadCount * chunksPerThread, successCount.get());

        // Note: Due to potential hash collisions in test data (same strings),
        // actual chunk count may be less than the number of operations
        long actualChunkCount = contentStore.getChunkCount();
        assertTrue(actualChunkCount <= allHashes.size());
        assertTrue(actualChunkCount > 0);

        // Verify all unique chunks can be retrieved
        for (String hash : allHashes) {
            try {
                byte[] data = contentStore.retrieveChunk(hash);
                assertNotNull(data, "Chunk " + hash + " should not be null");
            } catch (StorageIntegrityException e) {
                fail("Failed to retrieve chunk " + hash + ": " + e.getMessage());
            }
        }
    }

    @Test
    @Disabled("Temporarily disabled due to test environment limitations")
    void testConcurrentReadsAndWrites() throws InterruptedException, IOException {
        // Arrange
        String initialHash = contentStore.storeChunk("initial".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        int readerThreads = 5;
        int writerThreads = 3;
        int operationsPerThread = 20;
        ExecutorService executor = Executors.newFixedThreadPool(readerThreads + writerThreads);
        CountDownLatch latch = new CountDownLatch(readerThreads + writerThreads);
        AtomicInteger readSuccessCount = new AtomicInteger(0);
        AtomicInteger writeSuccessCount = new AtomicInteger(0);

        // Act - Start reader threads
        for (int i = 0; i < readerThreads; i++) {
            executor.execute(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        byte[] data = contentStore.retrieveChunk(initialHash);
                        assertNotNull(data);
                        readSuccessCount.incrementAndGet();
                        Thread.sleep(1); // Small delay to increase contention
                    }
                } catch (IOException | StorageIntegrityException | InterruptedException e) {
                    fail("Concurrent read failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // Act - Start writer threads
        for (int i = 0; i < writerThreads; i++) {
            final int threadId = i;
            executor.execute(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        byte[] data = ("writer" + threadId + "_data" + j)
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        String hash = contentStore.storeChunk(data);
                        assertNotNull(hash);
                        writeSuccessCount.incrementAndGet();
                        Thread.sleep(1); // Small delay to increase contention
                    }
                } catch (IOException | InterruptedException e) {
                    fail("Concurrent write failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        // Assert
        assertEquals(readerThreads * operationsPerThread, readSuccessCount.get());
        assertEquals(writerThreads * operationsPerThread, writeSuccessCount.get());
        assertTrue(contentStore.existsChunk(initialHash));

        // Verify all written chunks exist
        long finalChunkCount = contentStore.getChunkCount();
        assertTrue(finalChunkCount >= 1, "Should have at least the initial chunk");
    }

    @Test
    void testPersistenceAcrossRestarts() throws IOException, StorageIntegrityException {
        // Arrange
        String hash1 = contentStore.storeChunk("persistent1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String hash2 = contentStore.storeChunk("persistent2".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // Close and recreate content store
        contentStore.close();

        FilesystemChunkIndex chunkIndex = FilesystemChunkIndex.create(storageDir, indexFile);
        contentStore = FilesystemContentStore.create(storageDir, chunkIndex, blake3Service);

        // Act & Assert
        assertTrue(contentStore.existsChunk(hash1));
        assertTrue(contentStore.existsChunk(hash2));
        assertEquals(2L, contentStore.getChunkCount());

        byte[] retrieved1 = contentStore.retrieveChunk(hash1);
        byte[] retrieved2 = contentStore.retrieveChunk(hash2);

        assertArrayEquals("persistent1".getBytes(java.nio.charset.StandardCharsets.UTF_8), retrieved1);
        assertArrayEquals("persistent2".getBytes(java.nio.charset.StandardCharsets.UTF_8), retrieved2);
    }

    @Test
    void testStats() throws IOException {
        // Arrange
        contentStore.storeChunk("data1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        contentStore.storeChunk("data2".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        contentStore.storeChunk("data3".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // Act
        ContentStoreStats stats = contentStore.getStats();

        // Assert
        assertNotNull(stats);
        assertEquals(3L, stats.getTotalChunks());
        assertTrue(stats.getTotalSizeBytes() > 0);
    }

    @Test
    void testIntegrityVerification() throws IOException, HashingException {
        // Arrange
        byte[] originalData = "integrity test".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash = contentStore.storeChunk(originalData);

        // Act
        byte[] retrievedData;
        try {
            retrievedData = contentStore.retrieveChunk(hash);
        } catch (StorageIntegrityException e) {
            fail("Integrity verification failed: " + e.getMessage());
            return;
        }

        // Assert
        assertArrayEquals(originalData, retrievedData);

        // Verify hash matches
        String retrievedHash = blake3Service.hashBuffer(retrievedData);
        assertEquals(hash, retrievedHash);
    }
}