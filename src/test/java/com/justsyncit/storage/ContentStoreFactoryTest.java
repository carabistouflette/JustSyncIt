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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for ContentStoreFactory.
 */
class ContentStoreFactoryTest {

    /** Mock BLAKE3 service. */
    @Mock
    private Blake3Service mockBlake3Service;

    /** Temporary directory for tests. */
    @TempDir
    Path tempDir;

    @Test
    void testCreateFilesystemStoreWithValidParameters() throws java.io.IOException {
        // Arrange
        MockitoAnnotations.openMocks(this);

        // Act
        ContentStore result = ContentStoreFactory.createFilesystemStore(tempDir, mockBlake3Service);

        // Assert
        assertNotNull(result);
    }

    @Test
    void testCreateFilesystemStoreWithNullStorageDirectory() {
        // Arrange
        MockitoAnnotations.openMocks(this);

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> ContentStoreFactory.createFilesystemStore(null, mockBlake3Service));
    }

    @Test
    void testCreateFilesystemStoreWithNullBlake3Service() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> ContentStoreFactory.createFilesystemStore(tempDir, null));
    }

    @Test
    void testCreateMemoryStoreWithValidVerifier() {
        // Arrange
        IntegrityVerifier mockVerifier = new IntegrityVerifier() {
            @Override
            public void verifyIntegrity(byte[] data, String expectedHash) throws StorageIntegrityException {
                // No-op
            }

            @Override
            public String calculateHash(byte[] data) {
                return "test_hash";
            }

            @Override
            public void validateHash(String hash) {
                // No-op
            }
        };

        // Act
        ContentStore result = ContentStoreFactory.createMemoryStore(mockVerifier);

        // Assert
        assertNotNull(result);
    }

    @Test
    void testCreateMemoryStoreWithNullVerifier() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> ContentStoreFactory.createMemoryStore((IntegrityVerifier) null));
    }

    @Test
    void testCreateMemoryStoreWithValidBlake3Service() {
        // Arrange
        MockitoAnnotations.openMocks(this);

        // Act
        ContentStore result = ContentStoreFactory.createMemoryStore(mockBlake3Service);

        // Assert
        assertNotNull(result);
    }

    @Test
    void testCreateMemoryStoreWithNullBlake3Service() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> ContentStoreFactory.createMemoryStore((Blake3Service) null));
    }

    @Test
    void testCreateFilesystemStoreWithCustomComponents() throws java.io.IOException {
        // Arrange
        MockitoAnnotations.openMocks(this);
        IntegrityVerifier mockVerifier = new IntegrityVerifier() {
            @Override
            public void verifyIntegrity(byte[] data, String expectedHash) throws StorageIntegrityException {
                // No-op
            }

            @Override
            public String calculateHash(byte[] data) {
                return "test_hash";
            }

            @Override
            public void validateHash(String hash) {
                // No-op
            }
        };
        ChunkPathGenerator mockPathGenerator = new ChunkPathGenerator() {
            @Override
            public Path generatePath(Path storageDirectory, String hash) {
                return storageDirectory.resolve(hash);
            }

            @Override
            public void validateHash(String hash) {
                // No-op
            }
        };
        ChunkIndex mockIndex = new ChunkIndex() {
            @Override
            public void putChunk(String hash, Path filePath) throws java.io.IOException {
                // No-op
            }

            @Override
            public Path getChunkPath(String hash) throws java.io.IOException {
                return null;
            }

            @Override
            public boolean containsChunk(String hash) throws java.io.IOException {
                return false;
            }

            @Override
            public boolean removeChunk(String hash) throws java.io.IOException {
                return false;
            }

            @Override
            public java.util.Set<String> getAllHashes() throws java.io.IOException {
                return new java.util.HashSet<>();
            }

            @Override
            public long getChunkCount() throws java.io.IOException {
                return 0;
            }

            @Override
            public long retainAll(java.util.Set<String> activeHashes) throws java.io.IOException {
                return 0;
            }

            @Override
            public void close() throws java.io.IOException {
                // No-op
            }
        };

        // Act
        ContentStore result = ContentStoreFactory.createFilesystemStore(
                tempDir, mockIndex, mockVerifier, mockPathGenerator);

        // Assert
        assertNotNull(result);
    }

    @Test
    void testCreateFilesystemStoreWithCustomComponentsNullStorageDirectory() {
        // Arrange
        IntegrityVerifier mockVerifier = new IntegrityVerifier() {
            @Override
            public void verifyIntegrity(byte[] data, String expectedHash) throws StorageIntegrityException {
                // No-op
            }

            @Override
            public String calculateHash(byte[] data) {
                return "test_hash";
            }

            @Override
            public void validateHash(String hash) {
                // No-op
            }
        };
        ChunkPathGenerator mockPathGenerator = new ChunkPathGenerator() {
            @Override
            public Path generatePath(Path storageDirectory, String hash) {
                return storageDirectory.resolve(hash);
            }

            @Override
            public void validateHash(String hash) {
                // No-op
            }
        };
        ChunkIndex mockIndex = new ChunkIndex() {
            @Override
            public void putChunk(String hash, Path filePath) throws java.io.IOException {
                // No-op
            }

            @Override
            public Path getChunkPath(String hash) throws java.io.IOException {
                return null;
            }

            @Override
            public boolean containsChunk(String hash) throws java.io.IOException {
                return false;
            }

            @Override
            public boolean removeChunk(String hash) throws java.io.IOException {
                return false;
            }

            @Override
            public java.util.Set<String> getAllHashes() throws java.io.IOException {
                return new java.util.HashSet<>();
            }

            @Override
            public long getChunkCount() throws java.io.IOException {
                return 0;
            }

            @Override
            public long retainAll(java.util.Set<String> activeHashes) throws java.io.IOException {
                return 0;
            }

            @Override
            public void close() throws java.io.IOException {
                // No-op
            }
        };

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> ContentStoreFactory.createFilesystemStore(null, mockIndex, mockVerifier, mockPathGenerator));
    }

    @Test
    void testCreateFilesystemStoreWithCustomComponentsNullChunkIndex() {
        // Arrange
        IntegrityVerifier mockVerifier = new IntegrityVerifier() {
            @Override
            public void verifyIntegrity(byte[] data, String expectedHash) throws StorageIntegrityException {
                // No-op
            }

            @Override
            public String calculateHash(byte[] data) {
                return "test_hash";
            }

            @Override
            public void validateHash(String hash) {
                // No-op
            }
        };
        ChunkPathGenerator mockPathGenerator = new ChunkPathGenerator() {
            @Override
            public Path generatePath(Path storageDirectory, String hash) {
                return storageDirectory.resolve(hash);
            }

            @Override
            public void validateHash(String hash) {
                // No-op
            }
        };

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> ContentStoreFactory.createFilesystemStore(tempDir, null, mockVerifier, mockPathGenerator));
    }

    @Test
    void testCreateFilesystemStoreWithCustomComponentsNullIntegrityVerifier() {
        // Arrange
        ChunkPathGenerator mockPathGenerator = new ChunkPathGenerator() {
            @Override
            public Path generatePath(Path storageDirectory, String hash) {
                return storageDirectory.resolve(hash);
            }

            @Override
            public void validateHash(String hash) {
                // No-op
            }
        };
        ChunkIndex mockIndex = new ChunkIndex() {
            @Override
            public void putChunk(String hash, Path filePath) throws java.io.IOException {
                // No-op
            }

            @Override
            public Path getChunkPath(String hash) throws java.io.IOException {
                return null;
            }

            @Override
            public boolean containsChunk(String hash) throws java.io.IOException {
                return false;
            }

            @Override
            public boolean removeChunk(String hash) throws java.io.IOException {
                return false;
            }

            @Override
            public java.util.Set<String> getAllHashes() throws java.io.IOException {
                return new java.util.HashSet<>();
            }

            @Override
            public long getChunkCount() throws java.io.IOException {
                return 0;
            }

            @Override
            public long retainAll(java.util.Set<String> activeHashes) throws java.io.IOException {
                return 0;
            }

            @Override
            public void close() throws java.io.IOException {
                // No-op
            }
        };

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> ContentStoreFactory.createFilesystemStore(tempDir, mockIndex, null, mockPathGenerator));
    }

    @Test
    void testCreateFilesystemStoreWithCustomComponentsNullPathGenerator() {
        // Arrange
        IntegrityVerifier mockVerifier = new IntegrityVerifier() {
            @Override
            public void verifyIntegrity(byte[] data, String expectedHash) throws StorageIntegrityException {
                // No-op
            }

            @Override
            public String calculateHash(byte[] data) {
                return "test_hash";
            }

            @Override
            public void validateHash(String hash) {
                // No-op
            }
        };
        ChunkIndex mockIndex = new ChunkIndex() {
            @Override
            public void putChunk(String hash, Path filePath) throws java.io.IOException {
                // No-op
            }

            @Override
            public Path getChunkPath(String hash) throws java.io.IOException {
                return null;
            }

            @Override
            public boolean containsChunk(String hash) throws java.io.IOException {
                return false;
            }

            @Override
            public boolean removeChunk(String hash) throws java.io.IOException {
                return false;
            }

            @Override
            public java.util.Set<String> getAllHashes() throws java.io.IOException {
                return new java.util.HashSet<>();
            }

            @Override
            public long getChunkCount() throws java.io.IOException {
                return 0;
            }

            @Override
            public long retainAll(java.util.Set<String> activeHashes) throws java.io.IOException {
                return 0;
            }

            @Override
            public void close() throws java.io.IOException {
                // No-op
            }
        };

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> ContentStoreFactory.createFilesystemStore(tempDir, mockIndex, mockVerifier, null));
    }
}