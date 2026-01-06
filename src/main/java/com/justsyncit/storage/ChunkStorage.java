package com.justsyncit.storage;

import java.io.IOException;

/**
 * Interface for basic chunk storage operations.
 * Follows Interface Segregation Principle by focusing only on storage
 * functionality.
 */
public interface ChunkStorage {

    /**
     * Stores a chunk of data and returns its hash.
     * If the chunk already exists (same hash), it will not be stored again.
     *
     * @param data the chunk data to store
     * @return the hash of the stored chunk
     * @throws IOException              if an I/O error occurs during storage
     * @throws IllegalArgumentException if data is null or empty
     */
    String storeChunk(byte[] data) throws IOException;

    /**
     * Retrieves a chunk by its hash.
     * The integrity of the retrieved data is verified against the hash.
     *
     * @param hash the hash of the chunk to retrieve
     * @return the chunk data, or null if not found
     * @throws IOException               if an I/O error occurs during retrieval
     * @throws StorageIntegrityException if the retrieved data fails integrity
     *                                   verification
     * @throws IllegalArgumentException  if hash is null or invalid
     */
    byte[] retrieveChunk(String hash) throws IOException, StorageIntegrityException;

    /**
     * Checks if a chunk with the given hash exists in storage.
     *
     * @param hash the hash to check
     * @return true if the chunk exists, false otherwise
     * @throws IOException              if an I/O error occurs during the check
     * @throws IllegalArgumentException if hash is null or invalid
     */
    boolean existsChunk(String hash) throws IOException;

    /**
     * Deletes a chunk from storage.
     * This operation is idempotent - if the chunk does not exist, it should
     * succeed.
     *
     * @param hash the hash of the chunk to delete
     * @throws IOException              if an I/O error occurs during deletion
     * @throws IllegalArgumentException if hash is null or invalid
     */
    void deleteChunk(String hash) throws IOException;
}