package com.justsyncit.storage;

import java.io.IOException;
import java.util.Set;

/**
 * Interface for garbage collection functionality.
 * Follows Interface Segregation Principle by focusing only on garbage
 * collection.
 */
public interface GarbageCollectible {

    /**
     * Performs garbage collection to remove orphaned chunks.
     * Orphaned chunks are those not referenced in the provided active hashes set.
     *
     * @param activeHashes set of hashes that are currently referenced
     * @return the number of chunks removed during garbage collection
     * @throws IOException              if an I/O error occurs during garbage
     *                                  collection
     * @throws IllegalArgumentException if activeHashes is null
     */
    long garbageCollect(Set<String> activeHashes) throws IOException;
}