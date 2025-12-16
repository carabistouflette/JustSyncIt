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

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Represents metadata for a file in a backup snapshot.
 */
public final class FileMetadata {

    /** Unique identifier for the file metadata. */
    private final String id;
    /** ID of the snapshot this file belongs to. */
    private final String snapshotId;
    /** Path of the file relative to backup root. */
    private final String path;
    /** Size of the file in bytes. */
    private final long size;
    /** Last modification time of the file. */
    private final Instant modifiedTime;
    /** BLAKE3 hash of the entire file. */
    private final String fileHash;
    /** List of chunk hashes that make up this file, in order. */
    private final List<String> chunkHashes;

    /**
     * Creates a new FileMetadata instance.
     *
     * @param id           unique identifier for the file metadata
     * @param snapshotId   ID of the snapshot this file belongs to
     * @param path         path of the file relative to backup root
     * @param size         size of the file in bytes
     * @param modifiedTime last modification time of the file
     * @param fileHash     BLAKE3 hash of the entire file
     * @param chunkHashes  list of chunk hashes that make up this file, in order
     * @throws IllegalArgumentException if any parameter is null or invalid
     */
    public FileMetadata(String id, String snapshotId, String path, long size,
            Instant modifiedTime, String fileHash, List<String> chunkHashes) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("File ID cannot be null or empty");
        }
        if (snapshotId == null || snapshotId.trim().isEmpty()) {
            throw new IllegalArgumentException("Snapshot ID cannot be null or empty");
        }
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty");
        }
        if (size < 0) {
            throw new IllegalArgumentException("File size cannot be negative");
        }
        if (modifiedTime == null) {
            throw new IllegalArgumentException("Modified time cannot be null");
        }
        if (fileHash == null || fileHash.trim().isEmpty()) {
            throw new IllegalArgumentException("File hash cannot be null or empty");
        }
        if (chunkHashes == null) {
            throw new IllegalArgumentException("Chunk hashes list cannot be null");
        }
        if (size > 0 && chunkHashes.isEmpty()) {
            throw new IllegalArgumentException("Chunk hashes list cannot be empty for non-empty file");
        }

        this.id = id;
        this.snapshotId = snapshotId;
        this.path = path;
        this.size = size;
        this.modifiedTime = modifiedTime;
        this.fileHash = fileHash;
        this.chunkHashes = List.copyOf(chunkHashes); // Create immutable copy
    }

    /**
     * Gets the unique identifier for the file metadata.
     *
     * @return the file ID
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the ID of the snapshot this file belongs to.
     *
     * @return the snapshot ID
     */
    public String getSnapshotId() {
        return snapshotId;
    }

    /**
     * Gets the path of the file relative to backup root.
     *
     * @return the file path
     */
    public String getPath() {
        return path;
    }

    /**
     * Gets the size of the file in bytes.
     *
     * @return the file size
     */
    public long getSize() {
        return size;
    }

    /**
     * Gets the last modification time of the file.
     *
     * @return the modification time
     */
    public Instant getModifiedTime() {
        return modifiedTime;
    }

    /**
     * Gets the BLAKE3 hash of the entire file.
     *
     * @return the file hash
     */
    public String getFileHash() {
        return fileHash;
    }

    /**
     * Gets the list of chunk hashes that make up this file, in order.
     *
     * @return immutable list of chunk hashes
     */
    public List<String> getChunkHashes() {
        return chunkHashes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FileMetadata that = (FileMetadata) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "FileMetadata{"
                + "id='" + id + '\''
                + ", snapshotId='" + snapshotId + '\''
                + ", path='" + path + '\''
                + ", size=" + size
                + ", modifiedTime=" + modifiedTime
                + ", fileHash='" + fileHash + '\''
                + ", chunkCount=" + chunkHashes.size()
                + '}';
    }
}