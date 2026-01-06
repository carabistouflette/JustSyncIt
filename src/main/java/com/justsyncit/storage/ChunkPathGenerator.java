package com.justsyncit.storage;

import com.justsyncit.ServiceException;
import java.nio.file.Path;

/**
 * Interface for generating file paths for chunks based on their hash.
 * Different implementations can use different strategies for organizing chunks.
 */
public interface ChunkPathGenerator {

    /**
     * Generates the file path for a chunk based on its hash.
     *
     * @param storageDirectory the base storage directory
     * @param hash             the chunk hash
     * @return the file path for the chunk
     * @throws IllegalArgumentException if hash is null or invalid
     * @throws ServiceException         if an error occurs while generating the path
     */
    Path generatePath(Path storageDirectory, String hash) throws ServiceException;

    /**
     * Validates that a hash is compatible with this path generator.
     *
     * @param hash the hash to validate
     * @throws IllegalArgumentException if hash is null or invalid
     */
    void validateHash(String hash);
}