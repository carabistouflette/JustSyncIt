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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Abstract base class for ContentStore implementations.
 * Provides common functionality for thread safety, state management, and statistics.
 * Follows the Open/Closed Principle by allowing extension without modification.
 */
public abstract class AbstractContentStore implements ContentStore {

    /** Logger instance. */
    protected static final Logger logger = LoggerFactory.getLogger(AbstractContentStore.class);

    /** Lock for thread-safe access. */
    protected final ReadWriteLock lock;
    /** Flag indicating if the store has been closed. */
    protected volatile boolean closed;
    /** Timestamp of the last garbage collection. */
    protected volatile Instant lastGcTime;

    /**
     * Creates a new AbstractContentStore.
     */
    protected AbstractContentStore() {
        this.lock = new ReentrantReadWriteLock();
        this.closed = false;
        this.lastGcTime = null;
    }

    @Override
    public final String storeChunk(byte[] data) throws IOException {
        validateNotClosed();
        validateData(data);
        return doStoreChunk(data);
    }

    @Override
    public final byte[] retrieveChunk(String hash) throws IOException, StorageIntegrityException {
        validateNotClosed();
        validateHash(hash);
        return doRetrieveChunk(hash);
    }

    @Override
    public final boolean existsChunk(String hash) throws IOException {
        validateNotClosed();
        validateHash(hash);
        return doExistsChunk(hash);
    }

    @Override
    public final long getChunkCount() throws IOException {
        validateNotClosed();
        return doGetChunkCount();
    }

    @Override
    public final long getTotalSize() throws IOException {
        validateNotClosed();
        return doGetTotalSize();
    }

    @Override
    public final long garbageCollect(Set<String> activeHashes) throws IOException {
        validateNotClosed();
        validateActiveHashes(activeHashes);
        long removedCount = doGarbageCollect(activeHashes);
        if (removedCount > 0) {
            lastGcTime = Instant.now();
            logger.info("Garbage collection completed. Removed {} orphaned chunks", removedCount);
        }
        return removedCount;
    }

    @Override
    public final ContentStoreStats getStats() throws IOException {
        validateNotClosed();
        return doGetStats();
    }

    @Override
    public final void close() throws IOException {
        lock.writeLock().lock();
        try {
            if (!closed) {
                doClose();
                closed = true;
                logger.info("Closed content store");
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Template method for storing a chunk.
     * Subclasses must implement this method to provide specific storage logic.
     *
     * @param data the chunk data to store
     * @return the hash of the stored chunk
     * @throws IOException if an I/O error occurs during storage
     */
    protected abstract String doStoreChunk(byte[] data) throws IOException;

    /**
     * Template method for retrieving a chunk.
     * Subclasses must implement this method to provide specific retrieval logic.
     *
     * @param hash the hash of the chunk to retrieve
     * @return the chunk data, or null if not found
     * @throws IOException if an I/O error occurs during retrieval
     * @throws StorageIntegrityException if the retrieved data fails integrity verification
     */
    protected abstract byte[] doRetrieveChunk(String hash) throws IOException, StorageIntegrityException;

    /**
     * Template method for checking if a chunk exists.
     * Subclasses must implement this method to provide specific existence check logic.
     *
     * @param hash the hash to check
     * @return true if the chunk exists, false otherwise
     * @throws IOException if an I/O error occurs during the check
     */
    protected abstract boolean doExistsChunk(String hash) throws IOException;

    /**
     * Template method for getting the chunk count.
     * Subclasses must implement this method to provide specific count logic.
     *
     * @return the number of stored chunks
     * @throws IOException if an I/O error occurs
     */
    protected abstract long doGetChunkCount() throws IOException;

    /**
     * Template method for getting the total storage size.
     * Subclasses must implement this method to provide specific size calculation logic.
     *
     * @return the total storage size
     * @throws IOException if an I/O error occurs
     */
    protected abstract long doGetTotalSize() throws IOException;

    /**
     * Template method for garbage collection.
     * Subclasses must implement this method to provide specific GC logic.
     *
     * @param activeHashes set of hashes that are currently referenced
     * @return the number of chunks removed during garbage collection
     * @throws IOException if an I/O error occurs during garbage collection
     */
    protected abstract long doGarbageCollect(Set<String> activeHashes) throws IOException;

    /**
     * Template method for getting statistics.
     * Subclasses must implement this method to provide specific stats logic.
     *
     * @return storage statistics
     * @throws IOException if an I/O error occurs
     */
    protected abstract ContentStoreStats doGetStats() throws IOException;

    /**
     * Template method for closing the store.
     * Subclasses must implement this method to provide specific cleanup logic.
     *
     * @throws IOException if an I/O error occurs during closing
     */
    protected abstract void doClose() throws IOException;

    /**
     * Validates that the store is not closed.
     *
     * @throws IOException if the store has been closed
     */
    protected final void validateNotClosed() throws IOException {
        if (closed) {
            throw new IOException("Content store has been closed");
        }
    }

    /**
     * Validates that data is not null or empty.
     *
     * @param data the data to validate
     * @throws IllegalArgumentException if data is null or empty
     */
    protected void validateData(byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Data cannot be null or empty");
        }
    }

    /**
     * Validates that a hash is not null or empty.
     *
     * @param hash the hash to validate
     * @throws IllegalArgumentException if hash is null or empty
     */
    protected void validateHash(String hash) {
        if (hash == null || hash.trim().isEmpty()) {
            throw new IllegalArgumentException("Hash cannot be null or empty");
        }
    }

    /**
     * Validates that active hashes set is not null.
     *
     * @param activeHashes the active hashes set to validate
     * @throws IllegalArgumentException if activeHashes is null
     */
    protected void validateActiveHashes(Set<String> activeHashes) {
        if (activeHashes == null) {
            throw new IllegalArgumentException("Active hashes set cannot be null");
        }
    }
}