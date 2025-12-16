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
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Filesystem-based implementation of ChunkIndex.
 * Thread-safe implementation using concurrent collections and read-write locks.
 * Delegates persistence operations to IndexPersistence following Single
 * Responsibility Principle.
 */
public final class FilesystemChunkIndex implements ChunkIndex {

    /** Logger instance. */
    private static final Logger logger = LoggerFactory.getLogger(FilesystemChunkIndex.class);

    /** The in-memory index mapping hashes to file paths. */
    private final ConcurrentHashMap<String, Path> indexMap;
    /** Lock for thread-safe access to the index. */
    private final ReadWriteLock lock;
    /** Flag indicating if the index has been closed. */
    private volatile boolean closed;
    /** Persistence handler for loading and saving the index. */
    private final IndexPersistence persistence;

    /** Counter for pending changes since last save. */
    private final java.util.concurrent.atomic.AtomicInteger pendingChanges;
    /** Timestamp of the last index save. */
    private volatile long lastSaveTime;

    /**
     * Creates a new FilesystemChunkIndex.
     *
     * @param persistence the persistence handler for the index
     * @throws IOException if the index cannot be initialized
     */
    private FilesystemChunkIndex(IndexPersistence persistence) throws IOException {
        this.persistence = persistence;
        this.indexMap = new ConcurrentHashMap<>();
        this.lock = new ReentrantReadWriteLock();
        this.closed = false;
        this.pendingChanges = new java.util.concurrent.atomic.AtomicInteger(0);
        this.lastSaveTime = System.currentTimeMillis();

        // Ensure directories exist and load existing index if it exists
        persistence.ensureDirectoriesExist();
        loadIndex();
    }

    /**
     * Creates a new FilesystemChunkIndex.
     *
     * @param storageDirectory directory where chunks are stored
     * @param indexFile        file to use for index
     * @return a new FilesystemChunkIndex instance
     * @throws IOException if index cannot be initialized
     */
    public static FilesystemChunkIndex create(Path storageDirectory, Path indexFile) throws IOException {
        IndexPersistence persistence = new IndexPersistence(storageDirectory, indexFile);
        return new FilesystemChunkIndex(persistence);
    }

    @Override
    public void putChunk(String hash, Path filePath) throws IOException {
        validateNotClosed();
        validateHash(hash);

        if (filePath == null) {
            throw new IllegalArgumentException("File path cannot be null");
        }

        lock.writeLock().lock();
        try {
            indexMap.put(hash, filePath);

            // Check if we need to save the index
            int pending = pendingChanges.incrementAndGet();
            long now = System.currentTimeMillis();
            if (pending >= 1000 || (now - lastSaveTime) > 5000) {
                persistence.saveIndex(indexMap);
                pendingChanges.set(0);
                lastSaveTime = now;
                logger.debug("Saved index with {} pending changes", pending);
            }

            logger.debug("Added chunk {} to index at path {}", hash, filePath);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Path getChunkPath(String hash) throws IOException {
        validateNotClosed();
        validateHash(hash);

        lock.readLock().lock();
        try {
            return indexMap.get(hash);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean containsChunk(String hash) throws IOException {
        validateNotClosed();
        validateHash(hash);

        lock.readLock().lock();
        try {
            return indexMap.containsKey(hash);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean removeChunk(String hash) throws IOException {
        validateNotClosed();
        validateHash(hash);

        lock.writeLock().lock();
        try {
            Path removed = indexMap.remove(hash);
            if (removed != null) {
                // Check if we need to save the index
                int pending = pendingChanges.incrementAndGet();
                long now = System.currentTimeMillis();
                if (pending >= 1000 || (now - lastSaveTime) > 5000) {
                    persistence.saveIndex(indexMap);
                    pendingChanges.set(0);
                    lastSaveTime = now;
                }
                logger.debug("Removed chunk {} from index", hash);
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Set<String> getAllHashes() throws IOException {
        validateNotClosed();

        lock.readLock().lock();
        try {
            return new HashSet<>(indexMap.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public long getChunkCount() throws IOException {
        validateNotClosed();

        lock.readLock().lock();
        try {
            return indexMap.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public long retainAll(Set<String> activeHashes) throws IOException {
        validateNotClosed();

        if (activeHashes == null) {
            throw new IllegalArgumentException("Active hashes set cannot be null");
        }

        lock.writeLock().lock();
        try {
            Set<String> toRemove = new HashSet<>();
            for (String hash : indexMap.keySet()) {
                if (!activeHashes.contains(hash)) {
                    toRemove.add(hash);
                }
            }

            for (String hash : toRemove) {
                indexMap.remove(hash);
            }

            if (!toRemove.isEmpty()) {
                // Force save after bulk removal
                persistence.saveIndex(indexMap);
                pendingChanges.set(0);
                lastSaveTime = System.currentTimeMillis();
                logger.debug("Retained {} chunks, removed {} orphaned chunks",
                        activeHashes.size(), toRemove.size());
            }

            return toRemove.size();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void close() throws IOException {
        lock.writeLock().lock();
        try {
            if (!closed) {
                persistence.saveIndex(indexMap);
                closed = true;
                logger.debug("Closed chunk index");
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Loads the index from disk.
     *
     * @throws IOException if an I/O error occurs
     */
    private void loadIndex() throws IOException {
        lock.writeLock().lock();
        try {
            indexMap.clear();
            Map<String, Path> loadedIndex = persistence.loadIndex();
            indexMap.putAll(loadedIndex);
            logger.debug("Loaded {} chunks from index", indexMap.size());
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void validateNotClosed() throws IOException {
        if (closed) {
            throw new IOException("Chunk index has been closed");
        }
    }

    private void validateHash(String hash) {
        if (hash == null || hash.trim().isEmpty()) {
            throw new IllegalArgumentException("Hash cannot be null or empty");
        }
    }
}