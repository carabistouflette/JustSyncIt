package com.justsyncit.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

/**
 * Interface for managing the chunk index that maps hashes to file paths.
 * Provides efficient lookup and management of chunk locations.
 */
public interface ChunkIndex {

    /**
     * Adds or updates a chunk entry in the index.
     *
     * @param hash     the chunk hash
     * @param filePath the path where the chunk is stored
     * @throws IOException              if an I/O error occurs
     * @throws IllegalArgumentException if hash or filePath is null
     */
    void putChunk(String hash, Path filePath) throws IOException;

    /**
     * Gets the file path for a chunk by its hash.
     *
     * @param hash the chunk hash
     * @return the file path, or null if not found
     * @throws IOException              if an I/O error occurs
     * @throws IllegalArgumentException if hash is null
     */
    Path getChunkPath(String hash) throws IOException;

    /**
     * Checks if a chunk exists in the index.
     *
     * @param hash the chunk hash
     * @return true if the chunk exists, false otherwise
     * @throws IOException              if an I/O error occurs
     * @throws IllegalArgumentException if hash is null
     */
    boolean containsChunk(String hash) throws IOException;

    /**
     * Removes a chunk from the index.
     *
     * @param hash the chunk hash to remove
     * @return true if the chunk was removed, false if it didn't exist
     * @throws IOException              if an I/O error occurs
     * @throws IllegalArgumentException if hash is null
     */
    boolean removeChunk(String hash) throws IOException;

    /**
     * Gets all chunk hashes in the index.
     *
     * @return a set of all chunk hashes
     * @throws IOException if an I/O error occurs
     */
    Set<String> getAllHashes() throws IOException;

    /**
     * Gets the total number of chunks in the index.
     *
     * @return the chunk count
     * @throws IOException if an I/O error occurs
     */
    long getChunkCount() throws IOException;

    /**
     * Removes chunks from the index that are not in the provided active hashes set.
     *
     * @param activeHashes set of hashes that should remain in the index
     * @return the number of chunks removed
     * @throws IOException if an I/O error occurs
     */
    long retainAll(Set<String> activeHashes) throws IOException;

    /**
     * Closes the index and releases any resources.
     *
     * @throws IOException if an I/O error occurs during closing
     */
    void close() throws IOException;
}