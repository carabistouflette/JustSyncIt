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

import com.justsyncit.hash.Blake3Service;
import com.justsyncit.hash.HashingException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for FilesystemContentStore.
 */
class FilesystemContentStoreTest {

    /** Temporary directory for tests. */
    @TempDir
    Path tempDir;

    /** Mock BLAKE3 service. */
    @Mock
    private Blake3Service mockBlake3Service;

    /** Mock chunk index. */
    @Mock
    private ChunkIndex mockChunkIndex;

    /** Content store under test. */
    private FilesystemContentStore contentStore;

    /** AutoCloseable for mock cleanup. */
    private AutoCloseable closeable;

    @BeforeEach
    void setUp() throws IOException {
        closeable = MockitoAnnotations.openMocks(this);

        Path storageDir = tempDir.resolve("storage");
        contentStore = FilesystemContentStore.create(storageDir, mockChunkIndex, mockBlake3Service);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (contentStore != null) {
            contentStore.close();
        }
        if (closeable != null) {
            closeable.close();
        }
    }

    @Test
    void testStoreChunkNewChunk() throws IOException, HashingException {
        // Arrange
        byte[] data = "test data".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String expectedHash = "abcdef1234567890";

        when(mockBlake3Service.hashBuffer(data)).thenReturn(expectedHash);
        when(mockChunkIndex.containsChunk(expectedHash)).thenReturn(false);

        // Act
        String actualHash = contentStore.storeChunk(data);

        // Assert
        assertEquals(expectedHash, actualHash);
        verify(mockBlake3Service).hashBuffer(data);
        verify(mockChunkIndex, times(2)).containsChunk(expectedHash); // Called twice due to double-check pattern
        verify(mockChunkIndex).putChunk(eq(expectedHash), any(Path.class));
    }

    @Test
    void testStoreChunkExistingChunk() throws IOException, HashingException {
        // Arrange
        byte[] data = "test data".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String expectedHash = "abcdef1234567890";

        when(mockBlake3Service.hashBuffer(data)).thenReturn(expectedHash);
        when(mockChunkIndex.containsChunk(expectedHash)).thenReturn(true);

        // Act
        String actualHash = contentStore.storeChunk(data);

        // Assert
        assertEquals(expectedHash, actualHash);
        verify(mockBlake3Service).hashBuffer(data);
        verify(mockChunkIndex).containsChunk(expectedHash);
        verify(mockChunkIndex, never()).putChunk(any(), any());
    }

    @Test
    void testStoreChunkNullData() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> contentStore.storeChunk(null));
    }

    @Test
    void testStoreChunkEmptyData() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> contentStore.storeChunk(new byte[0]));
    }

    @Test
    void testRetrieveChunkExistingChunk() throws IOException, StorageIntegrityException {
        // Arrange
        String hash = "abcdef1234567890";
        byte[] expectedData = "test data".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path chunkPath = tempDir.resolve("storage").resolve("ab").resolve("cdef1234567890");

        when(mockChunkIndex.getChunkPath(hash)).thenReturn(chunkPath);
        try {
            when(mockBlake3Service.hashBuffer(expectedData)).thenReturn(hash);
        } catch (HashingException e) {
            // This shouldn't happen in test
        }

        // Create the chunk file and parent directory
        Path parentDir = chunkPath.getParent();
        if (parentDir != null) {
            java.nio.file.Files.createDirectories(parentDir);
        }
        java.nio.file.Files.write(chunkPath, expectedData);

        // Act
        byte[] actualData = contentStore.retrieveChunk(hash);

        // Assert
        assertNotNull(actualData, "Retrieved data should not be null");
        assertArrayEquals(expectedData, actualData);
        verify(mockChunkIndex).getChunkPath(hash);
        try {
            verify(mockBlake3Service).hashBuffer(expectedData);
        } catch (HashingException e) {
            // This shouldn't happen in test
        }
    }

    @Test
    void testRetrieveChunkNonExistentChunk() throws IOException, StorageIntegrityException {
        // Arrange
        String hash = "nonexistent";

        when(mockChunkIndex.getChunkPath(hash)).thenReturn(null);

        // Act
        byte[] result = contentStore.retrieveChunk(hash);

        // Assert
        assertNull(result);
        verify(mockChunkIndex).getChunkPath(hash);
        try {
            verify(mockBlake3Service, never()).hashBuffer(any());
        } catch (HashingException e) {
            // This shouldn't happen in test
        }
    }

    @Test
    void testRetrieveChunkIntegrityFailure() throws IOException {
        // Arrange
        String hash = "abcdef1234567890";
        byte[] data = "test data".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path chunkPath = tempDir.resolve("storage").resolve("ab").resolve("cdef1234567890");

        when(mockChunkIndex.getChunkPath(hash)).thenReturn(chunkPath);
        try {
            when(mockBlake3Service.hashBuffer(data)).thenReturn("differenthash");
        } catch (HashingException e) {
            // This shouldn't happen in test
        }

        // Create the chunk file and parent directory
        Path parentDir = chunkPath.getParent();
        if (parentDir != null) {
            java.nio.file.Files.createDirectories(parentDir);
        }
        java.nio.file.Files.write(chunkPath, data);

        // Act & Assert
        assertThrows(StorageIntegrityException.class, () -> contentStore.retrieveChunk(hash));
        verify(mockChunkIndex).getChunkPath(hash);
        try {
            verify(mockBlake3Service).hashBuffer(data);
        } catch (HashingException e) {
            // This shouldn't happen in test
        }
    }

    @Test
    void testExistsChunkTrue() throws IOException {
        // Arrange
        String hash = "abcdef1234567890";

        when(mockChunkIndex.containsChunk(hash)).thenReturn(true);

        // Act
        boolean result = contentStore.existsChunk(hash);

        // Assert
        assertTrue(result);
        verify(mockChunkIndex).containsChunk(hash);
    }

    @Test
    void testExistsChunkFalse() throws IOException {
        // Arrange
        String hash = "nonexistent";

        when(mockChunkIndex.containsChunk(hash)).thenReturn(false);

        // Act
        boolean result = contentStore.existsChunk(hash);

        // Assert
        assertFalse(result);
        verify(mockChunkIndex).containsChunk(hash);
    }

    @Test
    void testGetChunkCount() throws IOException {
        // Arrange
        long expectedCount = 42L;

        when(mockChunkIndex.getChunkCount()).thenReturn(expectedCount);

        // Act
        long actualCount = contentStore.getChunkCount();

        // Assert
        assertEquals(expectedCount, actualCount);
        verify(mockChunkIndex).getChunkCount();
    }

    @Test
    void testGarbageCollect() throws IOException {
        // Arrange
        Set<String> activeHashes = new HashSet<>();
        activeHashes.add("hash1");
        activeHashes.add("hash2");

        Set<String> allHashes = new HashSet<>();
        allHashes.add("hash1");
        allHashes.add("hash2");
        allHashes.add("hash3"); // This should be garbage collected

        when(mockChunkIndex.getAllHashes()).thenReturn(allHashes);
        when(mockChunkIndex.getChunkPath("hash3")).thenReturn(tempDir.resolve("hash3"));

        // Act
        long removedCount = contentStore.garbageCollect(activeHashes);

        // Assert
        assertEquals(1L, removedCount);
        verify(mockChunkIndex).getAllHashes();
        verify(mockChunkIndex).getChunkPath("hash3");
        verify(mockChunkIndex).removeChunk("hash3");
    }

    @Test
    void testGetStats() throws IOException {
        // Arrange
        long expectedChunkCount = 10L;
        long expectedTotalSize = 1024L;

        when(mockChunkIndex.getChunkCount()).thenReturn(expectedChunkCount);

        // Act
        ContentStoreStats stats = contentStore.getStats();

        // Assert
        assertNotNull(stats);
        assertEquals(expectedChunkCount, stats.getTotalChunks());
        verify(mockChunkIndex).getChunkCount();
    }

    @Test
    void testClose() throws IOException {
        // Act
        contentStore.close();

        // Assert
        verify(mockChunkIndex).close();
    }

    @Test
    void testOperationsAfterClose() {
        // Arrange
        try {
            contentStore.close();
        } catch (IOException e) {
            fail("Close should not throw exception");
        }

        // Act & Assert
        assertThrows(IOException.class, () -> contentStore.storeChunk(
                "test".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThrows(IOException.class, () -> contentStore.retrieveChunk("hash"));
        assertThrows(IOException.class, () -> contentStore.existsChunk("hash"));
        assertThrows(IOException.class, () -> contentStore.getChunkCount());
        assertThrows(IOException.class, () -> contentStore.getTotalSize());
        assertThrows(IOException.class, () -> contentStore.garbageCollect(new HashSet<>()));
        assertThrows(IOException.class, () -> contentStore.getStats());
    }
}