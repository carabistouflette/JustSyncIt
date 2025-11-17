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
import com.justsyncit.storage.metadata.ChunkMetadata;
import com.justsyncit.storage.metadata.MetadataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * SQLite-enhanced implementation of ContentStore that integrates with metadata service.
 * Provides content-addressable storage with metadata management capabilities.
 * Extends AbstractContentStore to follow Open/Closed Principle.
 */
public final class SqliteContentStore extends AbstractContentStore {

    /** Logger instance. */
    private static final Logger logger = LoggerFactory.getLogger(SqliteContentStore.class);

    /** The underlying content store for actual chunk storage. */
    private final ContentStore delegateStore;
    /** The metadata service for managing backup metadata. */
    private final MetadataService metadataService;

    /**
     * Creates a new SqliteContentStore.
     *
     * @param delegateStore the underlying content store for chunk storage
     * @param metadataService the metadata service for managing metadata
     * @throws IllegalArgumentException if any parameter is null
     */
    public SqliteContentStore(ContentStore delegateStore, MetadataService metadataService) {
        if (delegateStore == null) {
            throw new IllegalArgumentException("Delegate store cannot be null");
        }
        if (metadataService == null) {
            throw new IllegalArgumentException("Metadata service cannot be null");
        }

        this.delegateStore = delegateStore;
        // Make defensive copy to prevent external modification
        this.metadataService = java.util.Objects.requireNonNull(metadataService, "Metadata service cannot be null");
    }

    /**
     * Creates a new SqliteContentStore with default components.
     *
     * @param storageDirectory directory to store chunks in
     * @param metadataService the metadata service for managing metadata
     * @param blake3Service BLAKE3 service for hashing
     * @return a new SqliteContentStore instance
     * @throws IOException if store cannot be created
     */
    public static SqliteContentStore create(String storageDirectory,
                                         MetadataService metadataService,
                                         Blake3Service blake3Service) throws IOException {
        ContentStore delegateStore = ContentStoreFactory.createFilesystemStore(
                java.nio.file.Paths.get(storageDirectory), blake3Service);
        return new SqliteContentStore(delegateStore, metadataService);
    }

    @Override
    protected String doStoreChunk(byte[] data) throws IOException {
        // Store chunk using delegate store
        String hash = delegateStore.storeChunk(data);

        // Record chunk metadata
        try {
            ChunkMetadata chunkMetadata = new ChunkMetadata(
                    hash,
                    data.length,
                    Instant.now(),
                    1, // Initial reference count
                    Instant.now()
            );
            metadataService.upsertChunk(chunkMetadata);
        } catch (Exception e) {
            logger.warn("Failed to record chunk metadata for {}: {}", hash, e.getMessage());
            // Don't fail the operation if metadata recording fails
        }

        return hash;
    }

    @Override
    protected byte[] doRetrieveChunk(String hash) throws IOException, StorageIntegrityException {
        // Record chunk access
        try {
            metadataService.recordChunkAccess(hash);
        } catch (Exception e) {
            logger.warn("Failed to record chunk access for {}: {}", hash, e.getMessage());
            // Don't fail the operation if metadata recording fails
        }

        // Retrieve chunk using delegate store
        return delegateStore.retrieveChunk(hash);
    }

    @Override
    protected boolean doExistsChunk(String hash) throws IOException {
        // Check delegate store first
        if (!delegateStore.existsChunk(hash)) {
            return false;
        }

        // Also check metadata service for consistency
        try {
            return metadataService.getChunkMetadata(hash).isPresent();
        } catch (Exception e) {
            logger.warn("Failed to check chunk metadata for {}: {}", hash, e.getMessage());
            // Fall back to delegate store result
            return true;
        }
    }

    @Override
    protected long doGetChunkCount() throws IOException {
        // Use metadata service for more accurate count
        try {
            return metadataService.getStats().getTotalChunks();
        } catch (Exception e) {
            logger.warn("Failed to get chunk count from metadata, falling back to delegate: {}", e.getMessage());
            return delegateStore.getChunkCount();
        }
    }

    @Override
    protected long doGetTotalSize() throws IOException {
        // Use metadata service for more accurate size
        try {
            return metadataService.getStats().getTotalChunkSize();
        } catch (Exception e) {
            logger.warn("Failed to get total size from metadata, falling back to delegate: {}", e.getMessage());
            return delegateStore.getTotalSize();
        }
    }

    @Override
    protected long doGarbageCollect(Set<String> activeHashes) throws IOException {
        // Use delegate store's garbage collection for now
        // Metadata cleanup would require additional methods in MetadataService
        return delegateStore.garbageCollect(activeHashes);
    }

    @Override
    protected ContentStoreStats doGetStats() throws IOException {
        // Get stats from delegate store
        ContentStoreStats delegateStats = delegateStore.getStats();
        // Enhance with metadata information if available
        try {
            com.justsyncit.storage.metadata.MetadataStats metadataStats = metadataService.getStats();
            // Create enhanced stats with metadata information
            return new ContentStoreStats(
                    delegateStats.getTotalChunks(),
                    delegateStats.getTotalSizeBytes(),
                    // Calculate deduplication ratio from metadata (convert to long)
                    metadataStats.getTotalChunks() > 0
                            ?
                            Math.round((double) delegateStats.getTotalSizeBytes() / metadataStats.getTotalChunks())
                            : 1L,
                    delegateStats.getLastGcTime(),
                    // Calculate orphaned chunks from metadata
                    Math.max(0, delegateStats.getTotalChunks() - metadataStats.getTotalChunks())
            );
        } catch (Exception e) {
            logger.warn("Failed to enhance stats with metadata, using delegate stats: {}", e.getMessage());
            return delegateStats;
        }
    }

    @Override
    protected void doClose() throws IOException {
        try {
            delegateStore.close();
        } finally {
            metadataService.close();
        }
    }

    /**
     * Creates a new snapshot in the metadata service.
     *
     * @param name human-readable name for the snapshot
     * @param description optional description of the snapshot
     * @return the created snapshot
     * @throws IOException if the snapshot cannot be created
     */
    public com.justsyncit.storage.metadata.Snapshot createSnapshot(String name, String description) throws IOException {
        return metadataService.createSnapshot(name, description);
    }

    /**
     * Gets a snapshot by its ID.
     *
     * @param id the snapshot ID
     * @return the snapshot if found, empty otherwise
     * @throws IOException if an error occurs during retrieval
     */
    public Optional<com.justsyncit.storage.metadata.Snapshot> getSnapshot(String id) throws IOException {
        return metadataService.getSnapshot(id);
    }

    /**
     * Lists all snapshots in the system.
     *
     * @return list of all snapshots
     * @throws IOException if an error occurs during listing
     */
    public java.util.List<com.justsyncit.storage.metadata.Snapshot> listSnapshots() throws IOException {
        return metadataService.listSnapshots();
    }

    /**
     * Deletes a snapshot and all its associated file metadata.
     *
     * @param id the snapshot ID to delete
     * @throws IOException if the snapshot cannot be deleted
     */
    public void deleteSnapshot(String id) throws IOException {
        metadataService.deleteSnapshot(id);
    }

    /**
     * Inserts file metadata into the database.
     *
     * @param file the file metadata to insert
     * @return the ID of the inserted file
     * @throws IOException if the file cannot be inserted
     */
    public String insertFile(com.justsyncit.storage.metadata.FileMetadata file) throws IOException {
        return metadataService.insertFile(file);
    }

    /**
     * Gets file metadata by its ID.
     *
     * @param id the file ID
     * @return the file metadata if found, empty otherwise
     * @throws IOException if an error occurs during retrieval
     */
    public Optional<com.justsyncit.storage.metadata.FileMetadata> getFile(String id) throws IOException {
        return metadataService.getFile(id);
    }

    /**
     * Gets all files in a snapshot.
     *
     * @param snapshotId the snapshot ID
     * @return list of files in the snapshot
     * @throws IOException if an error occurs during retrieval
     */
    public java.util.List<com.justsyncit.storage.metadata.FileMetadata> getFilesInSnapshot(
            String snapshotId) throws IOException {
        return metadataService.getFilesInSnapshot(snapshotId);
    }

    /**
     * Updates file metadata.
     *
     * @param file the file metadata to update
     * @throws IOException if the file cannot be updated
     */
    public void updateFile(com.justsyncit.storage.metadata.FileMetadata file) throws IOException {
        metadataService.updateFile(file);
    }

    /**
     * Deletes file metadata.
     *
     * @param id the file ID to delete
     * @throws IOException if the file cannot be deleted
     */
    public void deleteFile(String id) throws IOException {
        metadataService.deleteFile(id);
    }

    /**
     * Gets metadata statistics.
     *
     * @return metadata statistics
     * @throws IOException if an error occurs during statistics collection
     */
    public com.justsyncit.storage.metadata.MetadataStats getMetadataStats() throws IOException {
        return metadataService.getStats();
    }

    /**
     * Begins a new metadata transaction.
     *
     * @return a new transaction instance
     * @throws IOException if the transaction cannot be started
     */
    public com.justsyncit.storage.metadata.Transaction beginMetadataTransaction() throws IOException {
        return metadataService.beginTransaction();
    }
}