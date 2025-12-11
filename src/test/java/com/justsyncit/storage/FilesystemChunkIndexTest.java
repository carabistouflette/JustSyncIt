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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit tests for FilesystemChunkIndex.
 */
class FilesystemChunkIndexTest {

    /** Temporary directory for tests. */
    @TempDir
    Path tempDir;

    /** Storage directory for test files. */
    private Path storageDir;

    /** Index file for chunk metadata. */
    private Path indexFile;

    /** Chunk index under test. */
    private FilesystemChunkIndex chunkIndex;

    @BeforeEach
    void setUp() throws IOException {
        storageDir = tempDir.resolve("storage");
        indexFile = tempDir.resolve("index.txt");
        chunkIndex = FilesystemChunkIndex.create(storageDir, indexFile);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (chunkIndex != null) {
            chunkIndex.close();
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testPutChunk() throws IOException {
        // Arrange
        String hash = "abcdef1234567890";
        Path filePath = storageDir.resolve("test.txt");

        // Act
        chunkIndex.putChunk(hash, filePath);

        // Assert
        assertTrue(chunkIndex.containsChunk(hash));
        assertEquals(filePath, chunkIndex.getChunkPath(hash));
        assertEquals(1L, chunkIndex.getChunkCount());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testPutChunkNullHash() {
        // Arrange
        Path filePath = storageDir.resolve("test.txt");

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> chunkIndex.putChunk(null, filePath));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testPutChunkNullFilePath() {
        // Arrange
        String hash = "abcdef1234567890";

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> chunkIndex.putChunk(hash, null));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testGetChunkPathExistingChunk() throws IOException {
        // Arrange
        String hash = "abcdef1234567890";
        Path filePath = storageDir.resolve("test.txt");
        chunkIndex.putChunk(hash, filePath);

        // Act
        Path result = chunkIndex.getChunkPath(hash);

        // Assert
        assertEquals(filePath, result);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testGetChunkPathNonExistentChunk() throws IOException {
        // Arrange
        String hash = "nonexistent";

        // Act
        Path result = chunkIndex.getChunkPath(hash);

        // Assert
        assertNull(result);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testContainsChunkTrue() throws IOException {
        // Arrange
        String hash = "abcdef1234567890";
        Path filePath = storageDir.resolve("test.txt");
        chunkIndex.putChunk(hash, filePath);

        // Act
        boolean result = chunkIndex.containsChunk(hash);

        // Assert
        assertTrue(result);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testContainsChunkFalse() throws IOException {
        // Arrange
        String hash = "nonexistent";

        // Act
        boolean result = chunkIndex.containsChunk(hash);

        // Assert
        assertFalse(result);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testRemoveChunkExistingChunk() throws IOException {
        // Arrange
        String hash = "abcdef1234567890";
        Path filePath = storageDir.resolve("test.txt");
        chunkIndex.putChunk(hash, filePath);

        // Act
        boolean result = chunkIndex.removeChunk(hash);

        // Assert
        assertTrue(result);
        assertFalse(chunkIndex.containsChunk(hash));
        assertNull(chunkIndex.getChunkPath(hash));
        assertEquals(0L, chunkIndex.getChunkCount());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testRemoveChunkNonExistentChunk() throws IOException {
        // Arrange
        String hash = "nonexistent";

        // Act
        boolean result = chunkIndex.removeChunk(hash);

        // Assert
        assertFalse(result);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testGetAllHashes() throws IOException {
        // Arrange
        String hash1 = "hash1";
        String hash2 = "hash2";
        String hash3 = "hash3";

        chunkIndex.putChunk(hash1, storageDir.resolve("file1"));
        chunkIndex.putChunk(hash2, storageDir.resolve("file2"));
        chunkIndex.putChunk(hash3, storageDir.resolve("file3"));

        // Act
        Set<String> result = chunkIndex.getAllHashes();

        // Assert
        assertEquals(3, result.size());
        assertTrue(result.contains(hash1));
        assertTrue(result.contains(hash2));
        assertTrue(result.contains(hash3));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testGetChunkCount() throws IOException {
        // Arrange
        chunkIndex.putChunk("hash1", storageDir.resolve("file1"));
        chunkIndex.putChunk("hash2", storageDir.resolve("file2"));

        // Act
        long count = chunkIndex.getChunkCount();

        // Assert
        assertEquals(2L, count);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testRetainAll() throws IOException {
        // Arrange
        String hash1 = "hash1";
        String hash2 = "hash2";
        String hash3 = "hash3";

        chunkIndex.putChunk(hash1, storageDir.resolve("file1"));
        chunkIndex.putChunk(hash2, storageDir.resolve("file2"));
        chunkIndex.putChunk(hash3, storageDir.resolve("file3"));

        Set<String> activeHashes = new HashSet<>();
        assertTrue(activeHashes.add(hash1));
        assertTrue(activeHashes.add(hash3));

        // Act
        long removedCount = chunkIndex.retainAll(activeHashes);

        // Assert
        assertEquals(1L, removedCount);
        assertTrue(chunkIndex.containsChunk(hash1));
        assertFalse(chunkIndex.containsChunk(hash2));
        assertTrue(chunkIndex.containsChunk(hash3));
        assertEquals(2L, chunkIndex.getChunkCount());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testRetainAllNullActiveHashes() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> chunkIndex.retainAll(null));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testPersistence() throws IOException {
        // Arrange
        String hash1 = "hash1";
        String hash2 = "hash2";
        Path filePath1 = storageDir.resolve("file1");
        Path filePath2 = storageDir.resolve("file2");

        chunkIndex.putChunk(hash1, filePath1);
        chunkIndex.putChunk(hash2, filePath2);

        // Close and reopen the index
        chunkIndex.close();
        chunkIndex = FilesystemChunkIndex.create(storageDir, indexFile);

        // Act & Assert
        assertTrue(chunkIndex.containsChunk(hash1));
        assertTrue(chunkIndex.containsChunk(hash2));
        assertEquals(filePath1, chunkIndex.getChunkPath(hash1));
        assertEquals(filePath2, chunkIndex.getChunkPath(hash2));
        assertEquals(2L, chunkIndex.getChunkCount());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testPersistenceWithEmptyIndex() throws IOException {
        // Close and reopen the index
        chunkIndex.close();
        chunkIndex = FilesystemChunkIndex.create(storageDir, indexFile);

        // Act & Assert
        assertEquals(0L, chunkIndex.getChunkCount());
        assertTrue(chunkIndex.getAllHashes().isEmpty());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testOperationsAfterClose() throws IOException {
        // Arrange
        chunkIndex.close();

        // Act & Assert
        assertThrows(IOException.class, () -> chunkIndex.putChunk("hash", storageDir.resolve("file")));
        assertThrows(IOException.class, () -> chunkIndex.getChunkPath("hash"));
        assertThrows(IOException.class, () -> chunkIndex.containsChunk("hash"));
        assertThrows(IOException.class, () -> chunkIndex.removeChunk("hash"));
        assertThrows(IOException.class, () -> chunkIndex.getAllHashes());
        assertThrows(IOException.class, () -> chunkIndex.getChunkCount());
        assertThrows(IOException.class, () -> chunkIndex.retainAll(new HashSet<>()));
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testConcurrentOperations() throws InterruptedException, IOException {
        // Arrange
        int threadCount = 10;
        int operationsPerThread = 100;
        Thread[] threads = new Thread[threadCount];

        // Act
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    try {
                        String hash = "thread" + threadId + "_hash" + j;
                        Path filePath = storageDir.resolve("thread" + threadId + "_file" + j);

                        chunkIndex.putChunk(hash, filePath);
                        assertTrue(chunkIndex.containsChunk(hash));
                        assertEquals(filePath, chunkIndex.getChunkPath(hash));

                        if (j % 2 == 0) {
                            chunkIndex.removeChunk(hash);
                            assertFalse(chunkIndex.containsChunk(hash));
                        }
                    } catch (IOException e) {
                        fail("Concurrent operation failed: " + e.getMessage());
                    }
                }
            });
        }

        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Assert
        long expectedCount = (threadCount * operationsPerThread) / 2; // Half were removed
        assertEquals(expectedCount, chunkIndex.getChunkCount());
    }
}