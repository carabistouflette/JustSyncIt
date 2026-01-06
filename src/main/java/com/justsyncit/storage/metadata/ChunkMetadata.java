package com.justsyncit.storage.metadata;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents metadata for a stored chunk in the backup system.
 */
public final class ChunkMetadata {

    /** BLAKE3 hash of the chunk content. */
    private final String hash;
    /** Size of the chunk in bytes. */
    private final long size;
    /** Timestamp when the chunk was first stored. */
    private final Instant firstSeen;
    /** Number of files that reference this chunk. */
    private final long referenceCount;
    /** Timestamp of the last access to this chunk. */
    private final Instant lastAccessed;

    /**
     * Creates a new ChunkMetadata instance.
     *
     * @param hash           BLAKE3 hash of the chunk content
     * @param size           size of the chunk in bytes
     * @param firstSeen      timestamp when the chunk was first stored
     * @param referenceCount number of files that reference this chunk
     * @param lastAccessed   timestamp of the last access to this chunk
     * @throws IllegalArgumentException if any parameter is null or invalid
     */
    public ChunkMetadata(String hash, long size, Instant firstSeen,
            long referenceCount, Instant lastAccessed) {
        if (hash == null || hash.trim().isEmpty()) {
            throw new IllegalArgumentException("Chunk hash cannot be null or empty");
        }
        if (size < 0) {
            throw new IllegalArgumentException("Chunk size cannot be negative");
        }
        if (firstSeen == null) {
            throw new IllegalArgumentException("First seen timestamp cannot be null");
        }
        if (referenceCount < 0) {
            throw new IllegalArgumentException("Reference count cannot be negative");
        }
        if (lastAccessed == null) {
            throw new IllegalArgumentException("Last accessed timestamp cannot be null");
        }

        this.hash = hash;
        this.size = size;
        this.firstSeen = firstSeen;
        this.referenceCount = referenceCount;
        this.lastAccessed = lastAccessed;
    }

    /**
     * Gets the BLAKE3 hash of the chunk content.
     *
     * @return the chunk hash
     */
    public String getHash() {
        return hash;
    }

    /**
     * Gets the size of the chunk in bytes.
     *
     * @return the chunk size
     */
    public long getSize() {
        return size;
    }

    /**
     * Gets the timestamp when the chunk was first stored.
     *
     * @return the first seen timestamp
     */
    public Instant getFirstSeen() {
        return firstSeen;
    }

    /**
     * Gets the number of files that reference this chunk.
     *
     * @return the reference count
     */
    public long getReferenceCount() {
        return referenceCount;
    }

    /**
     * Gets the timestamp of the last access to this chunk.
     *
     * @return the last accessed timestamp
     */
    public Instant getLastAccessed() {
        return lastAccessed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ChunkMetadata that = (ChunkMetadata) o;
        return Objects.equals(hash, that.hash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hash);
    }

    @Override
    public String toString() {
        return "ChunkMetadata{"
                + "hash='" + hash + '\''
                + ", size=" + size
                + ", firstSeen=" + firstSeen
                + ", referenceCount=" + referenceCount
                + ", lastAccessed=" + lastAccessed
                + '}';
    }
}