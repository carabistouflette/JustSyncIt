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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for MemoryContentStore.
 */
class MemoryContentStoreTest {

    /** Mock BLAKE3 service. */
    @Mock
    private Blake3Service mockBlake3Service;

    /** Mock integrity verifier. */
    @Mock
    private IntegrityVerifier mockIntegrityVerifier;

    /** Content store under test. */
    private MemoryContentStore contentStore;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        contentStore = new MemoryContentStore(mockIntegrityVerifier);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (contentStore != null) {
            contentStore.close();
        }
    }

    @Test
    void testConstructorWithNullVerifier() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> new MemoryContentStore(null));
    }

    @Test
    void testStoreChunkWithValidData() throws Exception {
        // Arrange
        byte[] data = "test data".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String expectedHash = "abcdef1234567890";

        when(mockIntegrityVerifier.calculateHash(data)).thenReturn(expectedHash);

        // Act
        String result = contentStore.storeChunk(data);

        // Assert
        assertEquals(expectedHash, result);
        assertTrue(contentStore.existsChunk(expectedHash));
    }

    @Test
    void testStoreChunkWithNullData() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> contentStore.storeChunk(null));
    }

    @Test
    void testStoreChunkWithEmptyData() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> contentStore.storeChunk(new byte[0]));
    }

    @Test
    void testStoreDuplicateChunk() throws Exception {
        // Arrange
        byte[] data = "test data".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String expectedHash = "abcdef1234567890";

        when(mockIntegrityVerifier.calculateHash(data)).thenReturn(expectedHash);

        // Act
        String result1 = contentStore.storeChunk(data);
        String result2 = contentStore.storeChunk(data);

        // Assert
        assertEquals(expectedHash, result1);
        assertEquals(expectedHash, result2);
        assertEquals(1L, contentStore.getChunkCount()); // Only one chunk stored
    }

    @Test
    void testRetrieveChunkWithValidHash() throws Exception {
        // Arrange
        byte[] data = "test data".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash = "abcdef1234567890";

        when(mockIntegrityVerifier.calculateHash(data)).thenReturn(hash);

        // Store the chunk first
        contentStore.storeChunk(data);

        // Act
        byte[] result = contentStore.retrieveChunk(hash);

        // Assert
        assertNotNull(result);
        assertArrayEquals(data, result);
    }

    @Test
    void testRetrieveChunkWithNonExistentHash() throws Exception {
        // Arrange
        String nonExistentHash = "nonexistent123456";

        // Act
        byte[] result = contentStore.retrieveChunk(nonExistentHash);

        // Assert
        assertEquals(0, result.length); // Expect empty array, not null
    }

    @Test
    void testRetrieveChunkWithNullHash() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> contentStore.retrieveChunk(null));
    }

    @Test
    void testRetrieveChunkWithEmptyHash() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> contentStore.retrieveChunk(""));
    }

    @Test
    void testExistsChunkWithExistingHash() throws Exception {
        // Arrange
        byte[] data = "test data".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash = "abcdef1234567890";

        when(mockIntegrityVerifier.calculateHash(data)).thenReturn(hash);

        // Store the chunk first
        contentStore.storeChunk(data);

        // Act
        boolean result = contentStore.existsChunk(hash);

        // Assert
        assertTrue(result);
    }

    @Test
    void testExistsChunkWithNonExistentHash() throws Exception {
        // Arrange
        String nonExistentHash = "nonexistent123456";

        // Act
        boolean result = contentStore.existsChunk(nonExistentHash);

        // Assert
        assertFalse(result);
    }

    @Test
    void testExistsChunkWithNullHash() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> contentStore.existsChunk(null));
    }

    @Test
    void testGetChunkCountWithEmptyStore() throws Exception {
        // Act
        long result;
        try {
            result = contentStore.getChunkCount();
        } catch (IOException e) {
            throw new RuntimeException("Unexpected IOException", e);
        }

        // Assert
        assertEquals(0L, result);
    }

    @Test
    void testGetChunkCountWithStoredChunks() throws Exception {
        // Arrange
        byte[] data1 = "data1".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] data2 = "data2".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] data3 = "data3".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        when(mockIntegrityVerifier.calculateHash(any()))
                .thenReturn("hash1", "hash2", "hash3");

        // Store chunks
        contentStore.storeChunk(data1);
        contentStore.storeChunk(data2);
        contentStore.storeChunk(data3);

        // Act
        long result = contentStore.getChunkCount();

        // Assert
        assertEquals(3L, result);
    }

    @Test
    void testGetTotalSizeWithEmptyStore() throws Exception {
        // Act
        long result;
        try {
            result = contentStore.getTotalSize();
        } catch (IOException e) {
            throw new RuntimeException("Unexpected IOException", e);
        }

        // Assert
        assertEquals(0L, result);
    }

    @Test
    void testGetTotalSizeWithStoredChunks() throws Exception {
        // Arrange
        byte[] data1 = "data1".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] data2 = "data2".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        when(mockIntegrityVerifier.calculateHash(any()))
                .thenReturn("hash1", "hash2");

        // Store chunks
        contentStore.storeChunk(data1);
        contentStore.storeChunk(data2);

        // Act
        long result = contentStore.getTotalSize();

        // Assert
        assertEquals(data1.length + data2.length, result);
    }

    @Test
    void testGarbageCollectWithActiveHashes() throws Exception {
        // Arrange
        byte[] data1 = "data1".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] data2 = "data2".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] data3 = "data3".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        when(mockIntegrityVerifier.calculateHash(any()))
                .thenReturn("hash1", "hash2", "hash3");

        // Store chunks
        contentStore.storeChunk(data1);
        contentStore.storeChunk(data2);
        contentStore.storeChunk(data3);

        Set<String> activeHashes = new HashSet<>();
        activeHashes.add("hash1");
        activeHashes.add("hash3"); // hash2 should be garbage collected

        // Act
        long result = contentStore.garbageCollect(activeHashes);

        // Assert
        assertEquals(1L, result); // One chunk removed
        assertTrue(contentStore.existsChunk("hash1"));
        assertFalse(contentStore.existsChunk("hash2"));
        assertTrue(contentStore.existsChunk("hash3"));
    }

    @Test
    void testGarbageCollectWithNullActiveHashes() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> contentStore.garbageCollect(null));
    }

    @Test
    void testGetStats() throws Exception {
        // Arrange
        byte[] data1 = "data1".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] data2 = "data2".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        when(mockIntegrityVerifier.calculateHash(any()))
                .thenReturn("hash1", "hash2");

        // Store chunks
        contentStore.storeChunk(data1);
        contentStore.storeChunk(data2);

        // Act
        ContentStoreStats result = contentStore.getStats();

        // Assert
        assertNotNull(result);
        assertEquals(2L, result.getTotalChunks());
        assertEquals(data1.length + data2.length, result.getTotalSizeBytes());
    }

    @Test
    void testClose() throws Exception {
        // Arrange
        byte[] data = "test data".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(mockIntegrityVerifier.calculateHash(data)).thenReturn("hash1");

        // Store a chunk
        contentStore.storeChunk(data);

        // Verify chunk exists before close
        assertTrue(contentStore.existsChunk("hash1"));

        // Act
        contentStore.close();

        // Assert - after close, operations should fail
        assertThrows(java.io.IOException.class, () -> contentStore.storeChunk(data));
        assertThrows(java.io.IOException.class, () -> contentStore.retrieveChunk("hash1"));
        assertThrows(java.io.IOException.class, () -> contentStore.existsChunk("hash1"));
        assertThrows(java.io.IOException.class, () -> contentStore.getChunkCount());
        assertThrows(java.io.IOException.class, () -> contentStore.getTotalSize());
        assertThrows(java.io.IOException.class, () -> contentStore.garbageCollect(new HashSet<>()));
        assertThrows(java.io.IOException.class, () -> contentStore.getStats());
    }

    @Test
    void testGetCurrentChunkCount() throws Exception {
        // Arrange
        byte[] data1 = "data1".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] data2 = "data2".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        when(mockIntegrityVerifier.calculateHash(any()))
                .thenReturn("hash1", "hash2");

        // Store chunks
        contentStore.storeChunk(data1);
        contentStore.storeChunk(data2);

        // Act
        int result = contentStore.getCurrentChunkCount();

        // Assert
        assertEquals(2, result);
    }

    @Test
    void testIsEmptyWithEmptyStore() {
        // Act
        boolean result = contentStore.isEmpty();

        // Assert
        assertTrue(result);
    }

    @Test
    void testIsEmptyWithStoredChunks() throws Exception {
        // Arrange
        byte[] data = "test data".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(mockIntegrityVerifier.calculateHash(data)).thenReturn("hash1");

        // Store a chunk
        contentStore.storeChunk(data);

        // Act
        boolean result = contentStore.isEmpty();

        // Assert
        assertFalse(result);
    }
}