package com.justsyncit.storage;

import java.io.IOException;

/**
 * Interface for storage statistics and monitoring.
 * Follows Interface Segregation Principle by focusing only on statistics
 * functionality.
 */
public interface StorageStatistics {

    /**
     * Gets the total number of chunks stored.
     *
     * @return the number of stored chunks
     * @throws IOException if an I/O error occurs
     */
    long getChunkCount() throws IOException;

    /**
     * Gets the total storage size in bytes.
     *
     * @return the total storage size
     * @throws IOException if an I/O error occurs
     */
    long getTotalSize() throws IOException;

    /**
     * Gets statistics about the content store.
     *
     * @return storage statistics
     * @throws IOException if an I/O error occurs
     */
    ContentStoreStats getStats() throws IOException;
}