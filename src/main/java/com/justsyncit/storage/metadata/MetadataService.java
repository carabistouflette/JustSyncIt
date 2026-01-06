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

import com.justsyncit.storage.ClosableResource;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Interface for managing backup metadata.
 * Follows Interface Segregation Principle by providing focused metadata
 * operations.
 * Extends ClosableResource for proper resource management.
 */
public interface MetadataService extends ClosableResource {

        /**
         * Searches for files using full-text search on file path.
         *
         * @param query search query
         * @return list of matching file metadata
         * @throws IOException if search fails
         */
        List<FileMetadata> searchFiles(String query) throws IOException;

        /**
         * Closes the metadata service and releases resources.
         *
         * @throws IOException if close fails
         */
        void close() throws IOException;

        /**
         * Begins a new database transaction.
         *
         * @return a new transaction instance
         * @throws IOException if the transaction cannot be started
         */
        Transaction beginTransaction() throws IOException;

        // Snapshot operations

        /**
         * Creates a new snapshot.
         *
         * @param name        human-readable name for the snapshot
         * @param description optional description of the snapshot
         * @return the created snapshot
         * @throws IOException              if the snapshot cannot be created
         * @throws IllegalArgumentException if name is null or empty
         */
        Snapshot createSnapshot(String name, String description) throws IOException;

        /**
         * Gets a snapshot by its ID.
         *
         * @param id the snapshot ID
         * @return the snapshot if found, empty otherwise
         * @throws IOException              if an error occurs during retrieval
         * @throws IllegalArgumentException if id is null or empty
         */
        Optional<Snapshot> getSnapshot(String id) throws IOException;

        /**
         * Updates an existing snapshot.
         *
         * @param snapshot the snapshot to update
         * @throws IOException              if the snapshot cannot be updated
         * @throws IllegalArgumentException if snapshot is null
         */
        void updateSnapshot(Snapshot snapshot) throws IOException;

        /**
         * Lists all snapshots in the system.
         *
         * @return list of all snapshots, ordered by creation time (newest first)
         * @throws IOException if an error occurs during listing
         */
        List<Snapshot> listSnapshots() throws IOException;

        /**
         * Deletes a snapshot and all its associated file metadata.
         *
         * @param id the snapshot ID to delete
         * @throws IOException              if the snapshot cannot be deleted
         * @throws IllegalArgumentException if id is null or empty
         */
        void deleteSnapshot(String id) throws IOException;

        // File operations

        /**
         * Inserts file metadata into the database.
         *
         * @param file the file metadata to insert
         * @return the ID of the inserted file
         * @throws IOException              if the file cannot be inserted
         * @throws IllegalArgumentException if file is null
         */
        String insertFile(FileMetadata file) throws IOException;

        /**
         * Inserts multiple files in a batch operation for better performance.
         *
         * @param files the list of files to insert
         * @return the IDs of the inserted files
         * @throws IOException              if an I/O error occurs
         * @throws IllegalArgumentException if files list is null or contains null
         *                                  elements
         */
        List<String> insertFiles(List<FileMetadata> files) throws IOException;

        /**
         * Gets file metadata by its ID.
         *
         * @param id the file ID
         * @return the file metadata if found, empty otherwise
         * @throws IOException              if an error occurs during retrieval
         * @throws IllegalArgumentException if id is null or empty
         */
        Optional<FileMetadata> getFile(String id) throws IOException;

        /**
         * Gets all files in a snapshot.
         * Default implementation includes chunks for backward compatibility.
         *
         * @param snapshotId the snapshot ID
         * @return list of files in the snapshot
         * @throws IOException              if an error occurs during retrieval
         * @throws IllegalArgumentException if snapshotId is null or empty
         */
        default List<FileMetadata> getFilesInSnapshot(String snapshotId) throws IOException {
                return getFilesInSnapshot(snapshotId, true);
        }

        /**
         * Gets all files in a snapshot, optionally excluding chunks for performance.
         *
         * @param snapshotId    the snapshot ID
         * @param includeChunks whether to include chunk metadata
         * @return list of files in the snapshot
         * @throws IOException              if an error occurs during retrieval
         * @throws IllegalArgumentException if snapshotId is null or empty
         */
        List<FileMetadata> getFilesInSnapshot(String snapshotId, boolean includeChunks) throws IOException;

        /**
         * Gets a page of files in a snapshot, optionally filtered by path prefix.
         * 
         * @param snapshotId the snapshot ID
         * @param pathPrefix optional path prefix filter (can be null or empty)
         * @param limit      maximum number of files to return
         * @param offset     number of files to skip
         * @return filtered and paginated list of files
         * @throws IOException if retrieval fails
         */
        default List<FileMetadata> getFilesInSnapshot(String snapshotId, String pathPrefix, int limit, int offset)
                        throws IOException {
                return getFilesInSnapshot(snapshotId, pathPrefix, limit, offset, true);
        }

        /**
         * Gets a page of files in a snapshot, optionally filtered by path prefix and
         * optionally excluding chunks.
         * 
         * @param snapshotId    the snapshot ID
         * @param pathPrefix    optional path prefix filter (can be null or empty)
         * @param limit         maximum number of files to return
         * @param offset        number of files to skip
         * @param includeChunks whether to include chunk metadata
         * @return filtered and paginated list of files
         * @throws IOException if retrieval fails
         */
        default List<FileMetadata> getFilesInSnapshot(String snapshotId, String pathPrefix, int limit, int offset,
                        boolean includeChunks)
                        throws IOException {
                // Default implementation for backward compatibility (inefficient but safe)
                List<FileMetadata> all = getFilesInSnapshot(snapshotId, includeChunks);
                java.util.stream.Stream<FileMetadata> stream = all.stream();

                if (pathPrefix != null && !pathPrefix.isEmpty()) {
                        stream = stream.filter(f -> f.getPath().startsWith(pathPrefix));
                }

                return stream.sorted((f1, f2) -> f1.getPath().compareToIgnoreCase(f2.getPath()))
                                .skip(offset)
                                .limit(limit)
                                .toList();
        }

        /**
         * Counts files in a snapshot, optionally filtered by path prefix.
         * 
         * @param snapshotId the snapshot ID
         * @param pathPrefix optional path prefix filter
         * @return count of matching files
         * @throws IOException if counting fails
         */
        default int countFilesInSnapshot(String snapshotId, String pathPrefix) throws IOException {
                // Default implementation
                List<FileMetadata> all = getFilesInSnapshot(snapshotId);
                if (pathPrefix != null && !pathPrefix.isEmpty()) {
                        return (int) all.stream()
                                        .filter(f -> f.getPath().startsWith(pathPrefix))
                                        .count();
                }
                return all.size();
        }

        /**
         * Updates file metadata.
         *
         * @param file the file metadata to update
         * @throws IOException              if the file cannot be updated
         * @throws IllegalArgumentException if file is null
         */
        void updateFile(FileMetadata file) throws IOException;

        /**
         * Deletes file metadata.
         *
         * @param id the file ID to delete
         * @throws IOException              if the file cannot be deleted
         * @throws IllegalArgumentException if id is null or empty
         */
        void deleteFile(String id) throws IOException;

        // Chunk operations

        /**
         * Streams all chunk metadata.
         * Useful for maintenance tasks like integrity checks.
         * 
         * @return stream of all chunk metadata
         * @throws IOException if retrieval fails
         */
        java.util.stream.Stream<ChunkMetadata> streamAllChunks() throws IOException;

        /**
         * Records an access to a chunk (updates last_accessed).
         *
         * @param chunkHash the hash of the chunk
         * @throws IOException if the update fails
         */
        /**
         * Records an access to a chunk (updates last_accessed).
         *
         * @param chunkHash the hash of the chunk
         * @throws IOException              if the update fails
         * @throws IllegalArgumentException if chunkHash is null or empty
         */

        void recordChunkAccess(String chunkHash) throws IOException;

        /**
         * Gets chunk metadata by its hash.
         *
         * @param hash the chunk hash
         * @return the chunk metadata if found, empty otherwise
         * @throws IOException              if an error occurs during retrieval
         * @throws IllegalArgumentException if hash is null or empty
         */
        Optional<ChunkMetadata> getChunkMetadata(String hash) throws IOException;

        /**
         * Inserts or updates chunk metadata.
         *
         * @param chunk the chunk metadata to insert or update
         * @throws IOException              if the chunk cannot be inserted or updated
         * @throws IllegalArgumentException if chunk is null
         */
        void upsertChunk(ChunkMetadata chunk) throws IOException;

        /**
         * Deletes chunk metadata.
         *
         * @param hash the chunk hash to delete
         * @return true if the chunk was deleted, false if it didn't exist
         * @throws IOException              if the chunk cannot be deleted
         * @throws IllegalArgumentException if hash is null or empty
         */
        boolean deleteChunk(String hash) throws IOException;

        /**
         * Gets statistics about the metadata database.
         *
         * @return metadata statistics
         * @throws IOException if an error occurs during statistics collection
         */
        MetadataStats getStats() throws IOException;

        // Merkle Tree operations

        /**
         * Inserts or updates a Merkle node.
         *
         * @param node the node to insert or update
         * @throws IOException if the node cannot be stored
         */
        void upsertMerkleNode(com.justsyncit.storage.snapshot.MerkleNode node) throws IOException;

        /**
         * Gets a Merkle node by its hash.
         *
         * @param hash the node hash
         * @return the node if found, empty otherwise
         * @throws IOException if retrieval fails
         */
        Optional<com.justsyncit.storage.snapshot.MerkleNode> getMerkleNode(String hash) throws IOException;

        /**
         * Sets the Merkle root hash for a snapshot.
         *
         * @param snapshotId the snapshot ID
         * @param rootHash   the root node hash
         * @throws IOException if update fails
         */
        void setSnapshotRoot(String snapshotId, String rootHash) throws IOException;

        /**
         * Gets the Merkle root hash for a snapshot.
         *
         * @param snapshotId the snapshot ID
         * @return the root hash if found, empty otherwise
         * @throws IOException if retrieval fails
         */
        Optional<String> getSnapshotRoot(String snapshotId) throws IOException;

        /**
         * Copies file metadata for unchanged files from a previous snapshot to a new
         * one.
         * Efficiently duplicates rows in the database, excluding specified paths.
         *
         * @param sourceSnapshotId the source snapshot ID
         * @param targetSnapshotId the target snapshot ID
         * @param changedPaths     list of paths that have changed and should not be
         *                         copied
         * @throws IOException if the operation fails
         */
        void copyUnchangedFiles(String sourceSnapshotId, String targetSnapshotId, List<String> changedPaths)
                        throws IOException;

        /**
         * Compares two snapshots and returns the differences.
         *
         * @param snapshotId1 The ID of the older snapshot (or source).
         * @param snapshotId2 The ID of the newer snapshot (or target).
         * @return A list of differences.
         * @throws IOException If an I/O error occurs.
         */
        List<com.justsyncit.storage.snapshot.MerkleTreeDiffer.DiffEntry> compareSnapshots(String snapshotId1,
                        String snapshotId2) throws IOException;

        /**
         * Validates the snapshot chain integrity starting from the given snapshot.
         * checks if parent exists (if incremental) and if Merkle Root is present.
         *
         * @param snapshotId the snapshot ID to validate
         * @return true if valid, false otherwise
         * @throws IOException if an I/O error occurs
         */
        boolean validateSnapshotChain(String snapshotId) throws IOException;

        // Parity Group operations

        /**
         * Creates a new parity group.
         * 
         * @param algorithm the algorithm identifier (e.g. "RS-4-2")
         * @return the newly created group ID
         * @throws IOException if creation fails
         */
        long createParityGroup(String algorithm) throws IOException;

        /**
         * Adds a chunk to a parity group.
         * 
         * @param groupId   the group ID
         * @param chunkHash the chunk hash
         * @param index     the index within the group (0..k+m-1)
         * @param isParity  true if this is a parity chunk
         * @throws IOException if operation fails
         */
        void addChunkToParityGroup(long groupId, String chunkHash, int index, boolean isParity) throws IOException;

        /**
         * Gets a parity group by ID.
         * 
         * @param groupId the group ID
         * @return the group metadata if found
         * @throws IOException if retrieval fails
         */
        Optional<ParityGroupMetadata> getParityGroup(long groupId) throws IOException;

        /**
         * Gets all chunks (data and parity) belonging to a parity group.
         * 
         * @param groupId the group ID
         * @return list of chunk entries
         * @throws IOException if retrieval fails
         */
        /**
         * Gets all chunks (data and parity) belonging to a parity group.
         * 
         * @param groupId the group ID
         * @return list of chunk entries
         * @throws IOException if retrieval fails
         */
        List<ChunkParityEntry> getChunksInParityGroup(long groupId) throws IOException;

        /**
         * Gets the parity entry for a specific chunk.
         * 
         * @param chunkHash the chunk hash
         * @return the parity entry if found
         * @throws IOException if retrieval fails
         */
        Optional<ChunkParityEntry> getChunkParityEntry(String chunkHash) throws IOException;
}