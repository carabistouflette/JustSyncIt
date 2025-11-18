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

package com.justsyncit.storage.metadata;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for SqliteMetadataService.
 * Tests all CRUD operations and transaction management.
 */
@DisplayName("SqliteMetadataService Tests")
class MetadataServiceTest {

    /** Metadata service instance for testing. */
    private MetadataService metadataService;
    /** Temporary directory for test database. */
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("metadata-test");
        DatabaseConnectionManager connectionManager = new SqliteConnectionManager(
                tempDir.resolve("test.db").toString(), 5);
        SchemaMigrator schemaMigrator = SqliteSchemaMigrator.create();
        metadataService = new SqliteMetadataService(connectionManager, schemaMigrator);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (metadataService != null) {
            metadataService.close();
        }
        // Clean up temp directory
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore cleanup errors
                        }
                    });
        }
    }

    @Nested
    @DisplayName("Snapshot Management")
    class SnapshotManagement {

        @Test
        @DisplayName("Should create snapshot successfully")
        void shouldCreateSnapshot() throws IOException {
            // Given
            String name = "test-snapshot";
            String description = "Test snapshot description";

            // When
            Snapshot snapshot = metadataService.createSnapshot(name, description);

            // Then
            assertNotNull(snapshot.getId());
            assertEquals(name, snapshot.getName());
            assertEquals(description, snapshot.getDescription());
            assertNotNull(snapshot.getCreatedAt());
            assertEquals(0, snapshot.getTotalFiles());
            assertEquals(0, snapshot.getTotalSize());
        }

        @Test
        @DisplayName("Should reject null snapshot name")
        void shouldRejectNullSnapshotName() {
            // When/Then
            assertThrows(IllegalArgumentException.class, () ->
                    metadataService.createSnapshot(null, "description"));
        }

        @Test
        @DisplayName("Should reject empty snapshot name")
        void shouldRejectEmptySnapshotName() {
            // When/Then
            assertThrows(IllegalArgumentException.class, () ->
                    metadataService.createSnapshot("", "description"));
        }

        @Test
        @DisplayName("Should retrieve existing snapshot")
        void shouldRetrieveExistingSnapshot() throws IOException {
            // Given
            Snapshot created = metadataService.createSnapshot("test", "description");

            // When
            Optional<Snapshot> retrieved = metadataService.getSnapshot(created.getId());

            // Then
            assertTrue(retrieved.isPresent());
            Snapshot snapshot = retrieved.get();
            assertEquals(created.getId(), snapshot.getId());
            assertEquals(created.getName(), snapshot.getName());
            assertEquals(created.getDescription(), snapshot.getDescription());
            assertEquals(created.getCreatedAt().toEpochMilli(), snapshot.getCreatedAt().toEpochMilli());
        }

        @Test
        @DisplayName("Should return empty for non-existent snapshot")
        void shouldReturnEmptyForNonExistentSnapshot() throws IOException {
            // When
            Optional<Snapshot> retrieved = metadataService.getSnapshot("non-existent-id");

            // Then
            assertFalse(retrieved.isPresent());
        }

        @Test
        @DisplayName("Should list all snapshots")
        void shouldListAllSnapshots() throws IOException {
            // Given
            Snapshot snapshot1 = metadataService.createSnapshot("snapshot1", "desc1");
            Snapshot snapshot2 = metadataService.createSnapshot("snapshot2", "desc2");

            // When
            List<Snapshot> snapshots = metadataService.listSnapshots();

            // Then
            assertEquals(2, snapshots.size());
            assertTrue(snapshots.contains(snapshot1));
            assertTrue(snapshots.contains(snapshot2));
        }

        @Test
        @DisplayName("Should delete snapshot successfully")
        void shouldDeleteSnapshot() throws IOException {
            // Given
            Snapshot snapshot = metadataService.createSnapshot("test", "description");

            // When
            metadataService.deleteSnapshot(snapshot.getId());

            // Then
            Optional<Snapshot> retrieved = metadataService.getSnapshot(snapshot.getId());
            assertFalse(retrieved.isPresent());
        }
    }

    @Nested
    @DisplayName("File Management")
    class FileManagement {

        /** Snapshot ID for file management tests. */
        private String snapshotId;

        @BeforeEach
        void setUpSnapshot() throws IOException {
            Snapshot snapshot = metadataService.createSnapshot("test-snapshot", "description");
            snapshotId = snapshot.getId();
        }

        @Test
        @DisplayName("Should insert file successfully")
        void shouldInsertFile() throws IOException {
            // Given
            // Create chunks first to satisfy foreign key constraint
            ChunkMetadata chunk1 = new ChunkMetadata("chunk1", 512, Instant.now(), 1, Instant.now());
            ChunkMetadata chunk2 = new ChunkMetadata("chunk2", 512, Instant.now(), 1, Instant.now());
            metadataService.upsertChunk(chunk1);
            metadataService.upsertChunk(chunk2);
            
            FileMetadata file = new FileMetadata(
                    "file-id", snapshotId, "/path/to/file.txt",
                    1024, Instant.now(), "file-hash",
                    Arrays.asList("chunk1", "chunk2"));

            // When
            String fileId = metadataService.insertFile(file);

            // Then
            assertEquals(file.getId(), fileId);
            Optional<FileMetadata> retrieved = metadataService.getFile(fileId);
            assertTrue(retrieved.isPresent());
            FileMetadata retrievedFile = retrieved.get();
            assertEquals(file.getId(), retrievedFile.getId());
            assertEquals(file.getSnapshotId(), retrievedFile.getSnapshotId());
            assertEquals(file.getPath(), retrievedFile.getPath());
            assertEquals(file.getSize(), retrievedFile.getSize());
            assertEquals(file.getFileHash(), retrievedFile.getFileHash());
            assertEquals(file.getChunkHashes(), retrievedFile.getChunkHashes());
        }

        @Test
        @DisplayName("Should reject null file metadata")
        void shouldRejectNullFileMetadata() {
            // When/Then
            assertThrows(IllegalArgumentException.class, () ->
                    metadataService.insertFile(null));
        }

        @Test
        @DisplayName("Should retrieve existing file")
        void shouldRetrieveExistingFile() throws IOException {
            // Given
            // Create chunks first to satisfy foreign key constraint
            ChunkMetadata chunk1 = new ChunkMetadata("chunk1", 512, Instant.now(), 1, Instant.now());
            ChunkMetadata chunk2 = new ChunkMetadata("chunk2", 512, Instant.now(), 1, Instant.now());
            metadataService.upsertChunk(chunk1);
            metadataService.upsertChunk(chunk2);
            
            FileMetadata file = new FileMetadata(
                    "file-id", snapshotId, "/path/to/file.txt",
                    1024, Instant.now(), "file-hash",
                    Arrays.asList("chunk1", "chunk2"));
            metadataService.insertFile(file);

            // When
            Optional<FileMetadata> retrieved = metadataService.getFile(file.getId());

            // Then
            assertTrue(retrieved.isPresent());
            FileMetadata retrievedFile = retrieved.get();
            assertEquals(file.getId(), retrievedFile.getId());
            assertEquals(file.getSnapshotId(), retrievedFile.getSnapshotId());
            assertEquals(file.getPath(), retrievedFile.getPath());
            assertEquals(file.getSize(), retrievedFile.getSize());
            assertEquals(file.getFileHash(), retrievedFile.getFileHash());
            assertEquals(file.getChunkHashes(), retrievedFile.getChunkHashes());
        }

        @Test
        @DisplayName("Should return empty for non-existent file")
        void shouldReturnEmptyForNonExistentFile() throws IOException {
            // When
            Optional<FileMetadata> retrieved = metadataService.getFile("non-existent-id");

            // Then
            assertFalse(retrieved.isPresent());
        }

        @Test
        @DisplayName("Should list files in snapshot")
        void shouldListFilesInSnapshot() throws IOException {
            // Given
            // Create chunks first to satisfy foreign key constraint
            ChunkMetadata chunk1 = new ChunkMetadata("chunk1", 100, Instant.now(), 1, Instant.now());
            ChunkMetadata chunk2 = new ChunkMetadata("chunk2", 200, Instant.now(), 1, Instant.now());
            metadataService.upsertChunk(chunk1);
            metadataService.upsertChunk(chunk2);
            
            FileMetadata file1 = new FileMetadata(
                    "file1", snapshotId, "/path1", 100, Instant.now(), "hash1",
                    Arrays.asList("chunk1"));
            FileMetadata file2 = new FileMetadata(
                    "file2", snapshotId, "/path2", 200, Instant.now(), "hash2",
                    Arrays.asList("chunk2"));
            metadataService.insertFile(file1);
            metadataService.insertFile(file2);

            // When
            List<FileMetadata> files = metadataService.getFilesInSnapshot(snapshotId);

            // Then
            assertEquals(2, files.size());
            assertTrue(files.stream().anyMatch(f -> f.getId().equals(file1.getId())));
            assertTrue(files.stream().anyMatch(f -> f.getId().equals(file2.getId())));
        }

        @Test
        @DisplayName("Should update file successfully")
        void shouldUpdateFile() throws IOException {
            // Given
            // Create chunks first to satisfy foreign key constraint
            ChunkMetadata chunk1 = new ChunkMetadata("chunk1", 512, Instant.now(), 1, Instant.now());
            ChunkMetadata chunk2 = new ChunkMetadata("chunk2", 512, Instant.now(), 1, Instant.now());
            ChunkMetadata chunk3 = new ChunkMetadata("chunk3", 1024, Instant.now(), 1, Instant.now());
            metadataService.upsertChunk(chunk1);
            metadataService.upsertChunk(chunk2);
            metadataService.upsertChunk(chunk3);
            
            FileMetadata file = new FileMetadata(
                    "file-id", snapshotId, "/path/to/file.txt",
                    1024, Instant.now(), "file-hash",
                    Arrays.asList("chunk1", "chunk2"));
            metadataService.insertFile(file);

            FileMetadata updatedFile = new FileMetadata(
                    file.getId(), snapshotId, "/updated/path.txt",
                    2048, Instant.now(), "updated-hash",
                    Arrays.asList("chunk1", "chunk2", "chunk3"));

            // When
            metadataService.updateFile(updatedFile);

            // Then
            Optional<FileMetadata> retrieved = metadataService.getFile(file.getId());
            assertTrue(retrieved.isPresent());
            FileMetadata result = retrieved.get();
            assertEquals(updatedFile.getId(), result.getId());
            assertEquals(updatedFile.getPath(), result.getPath());
            assertEquals(updatedFile.getSize(), result.getSize());
            assertEquals(updatedFile.getFileHash(), result.getFileHash());
            assertEquals(updatedFile.getChunkHashes(), result.getChunkHashes());
        }

        @Test
        @DisplayName("Should delete file successfully")
        void shouldDeleteFile() throws IOException {
            // Given
            // Create chunks first to satisfy foreign key constraint
            ChunkMetadata chunk1 = new ChunkMetadata("chunk1", 512, Instant.now(), 1, Instant.now());
            ChunkMetadata chunk2 = new ChunkMetadata("chunk2", 512, Instant.now(), 1, Instant.now());
            metadataService.upsertChunk(chunk1);
            metadataService.upsertChunk(chunk2);
            
            FileMetadata file = new FileMetadata(
                    "file-id", snapshotId, "/path/to/file.txt",
                    1024, Instant.now(), "file-hash",
                    Arrays.asList("chunk1", "chunk2"));
            metadataService.insertFile(file);

            // When
            metadataService.deleteFile(file.getId());

            // Then
            Optional<FileMetadata> retrieved = metadataService.getFile(file.getId());
            assertFalse(retrieved.isPresent());
        }
    }

    @Nested
    @DisplayName("Chunk Management")
    class ChunkManagement {

        @Test
        @DisplayName("Should record chunk access")
        void shouldRecordChunkAccess() throws IOException {
            // Given
            String chunkHash = "test-chunk-hash";
            Instant originalTime = Instant.now();
            ChunkMetadata chunk = new ChunkMetadata(
                    chunkHash, 1024, originalTime, 1, originalTime);
            metadataService.upsertChunk(chunk);

            // Add a small delay to ensure different timestamp
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // When
            metadataService.recordChunkAccess(chunkHash);

            // Then
            Optional<ChunkMetadata> retrieved = metadataService.getChunkMetadata(chunkHash);
            assertTrue(retrieved.isPresent());
            ChunkMetadata result = retrieved.get();
            assertEquals(chunkHash, result.getHash());
            assertEquals(1024, result.getSize());
            assertTrue(result.getLastAccessed().isAfter(originalTime));
        }

        @Test
        @DisplayName("Should reject null chunk hash for access recording")
        void shouldRejectNullChunkHashForAccessRecording() {
            // When/Then
            assertThrows(IllegalArgumentException.class, () ->
                    metadataService.recordChunkAccess(null));
        }

        @Test
        @DisplayName("Should reject empty chunk hash for access recording")
        void shouldRejectEmptyChunkHashForAccessRecording() {
            // When/Then
            assertThrows(IllegalArgumentException.class, () ->
                    metadataService.recordChunkAccess(""));
        }

        @Test
        @DisplayName("Should upsert chunk metadata")
        void shouldUpsertChunkMetadata() throws IOException {
            // Given
            ChunkMetadata chunk = new ChunkMetadata(
                    "test-chunk", 1024, Instant.now(), 1, Instant.now());

            // When
            metadataService.upsertChunk(chunk);

            // Then
            Optional<ChunkMetadata> retrieved = metadataService.getChunkMetadata(chunk.getHash());
            assertTrue(retrieved.isPresent());
            ChunkMetadata result = retrieved.get();
            assertEquals(chunk.getHash(), result.getHash());
            assertEquals(chunk.getSize(), result.getSize());
            assertEquals(chunk.getFirstSeen().toEpochMilli(), result.getFirstSeen().toEpochMilli());
            assertEquals(chunk.getReferenceCount(), result.getReferenceCount());
        }

        @Test
        @DisplayName("Should reject null chunk metadata")
        void shouldRejectNullChunkMetadata() {
            // When/Then
            assertThrows(IllegalArgumentException.class, () ->
                    metadataService.upsertChunk(null));
        }

        @Test
        @DisplayName("Should delete chunk successfully")
        void shouldDeleteChunk() throws IOException {
            // Given
            ChunkMetadata chunk = new ChunkMetadata(
                    "test-chunk", 1024, Instant.now(), 1, Instant.now());
            metadataService.upsertChunk(chunk);

            // When
            boolean deleted = metadataService.deleteChunk(chunk.getHash());

            // Then
            assertTrue(deleted);
            Optional<ChunkMetadata> retrieved = metadataService.getChunkMetadata(chunk.getHash());
            assertFalse(retrieved.isPresent());
        }

        @Test
        @DisplayName("Should return false when deleting non-existent chunk")
        void shouldReturnFalseWhenDeletingNonExistentChunk() throws IOException {
            // When
            boolean deleted = metadataService.deleteChunk("non-existent-chunk");

            // Then
            assertFalse(deleted);
        }
    }

    @Nested
    @DisplayName("Transaction Management")
    class TransactionManagement {

        @Test
        @DisplayName("Should begin transaction")
        void shouldBeginTransaction() throws IOException {
            // When
            Transaction transaction = metadataService.beginTransaction();

            // Then
            assertNotNull(transaction);
            assertTrue(transaction.isActive());
        }

        @Test
        @DisplayName("Should commit transaction")
        void shouldCommitTransaction() throws IOException {
            // Given
            Transaction transaction = metadataService.beginTransaction();

            // When
            transaction.commit();

            // Then
            assertFalse(transaction.isActive());
        }

        @Test
        @DisplayName("Should rollback transaction")
        void shouldRollbackTransaction() throws IOException {
            // Given
            Transaction transaction = metadataService.beginTransaction();

            // When
            transaction.rollback();

            // Then
            assertFalse(transaction.isActive());
        }
    }

    @Nested
    @DisplayName("Statistics")
    class Statistics {

        @Test
        @DisplayName("Should get metadata statistics")
        void shouldGetMetadataStatistics() throws IOException {
            // Given
            Snapshot snapshot = metadataService.createSnapshot("test", "description");
            // Create chunks first to satisfy foreign key constraint
            ChunkMetadata chunk1 = new ChunkMetadata("chunk1", 512, Instant.now(), 1, Instant.now());
            ChunkMetadata chunk2 = new ChunkMetadata("chunk2", 512, Instant.now(), 1, Instant.now());
            metadataService.upsertChunk(chunk1);
            metadataService.upsertChunk(chunk2);
            
            FileMetadata file = new FileMetadata(
                    "file-id", snapshot.getId(), "/path/to/file.txt",
                    1024, Instant.now(), "file-hash",
                    Arrays.asList("chunk1", "chunk2"));
            metadataService.insertFile(file);

            // When
            MetadataStats stats = metadataService.getStats();

            // Then
            assertEquals(1, stats.getTotalSnapshots());
            assertEquals(1, stats.getTotalFiles());
            assertEquals(2, stats.getTotalChunks()); // We created chunk1 and chunk2
            assertEquals(1024, stats.getTotalChunkSize()); // 512 + 512
            assertEquals(2.0, stats.getAvgChunksPerFile()); // 2 chunks / 1 file
            assertEquals(512.0, stats.getAvgChunkSize()); // 1024 / 2 chunks
            assertEquals(1.0, stats.getDeduplicationRatio());
        }
    }

    @Nested
    @DisplayName("Resource Management")
    class ResourceManagement {

        @Test
        @DisplayName("Should close service gracefully")
        void shouldCloseServiceGracefully() throws IOException {
            // Given
            MetadataService service = metadataService;

            // When
            service.close();

            // Then
            assertDoesNotThrow(() -> service.close());
        }

        @Test
        @DisplayName("Should handle operations after close")
        void shouldHandleOperationsAfterClose() throws IOException {
            // Given
            metadataService.close();

            // When/Then
            assertThrows(IOException.class, () ->
                    metadataService.createSnapshot("test", "description"));
        }
    }
}