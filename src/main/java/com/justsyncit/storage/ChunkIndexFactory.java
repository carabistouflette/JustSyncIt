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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating ChunkIndex instances.
 * Follows Dependency Inversion Principle by depending on abstractions rather than concrete classes.
 * Provides a clean interface for creating different types of chunk indexes.
 */
public final class ChunkIndexFactory {

    /** Logger instance. */
    private static final Logger logger = LoggerFactory.getLogger(ChunkIndexFactory.class);

    /** Private constructor to prevent instantiation. */
    private ChunkIndexFactory() {
        // Utility class
    }

    /**
     * Creates a filesystem-based chunk index.
     *
     * @param storageDirectory directory where chunks are stored
     * @param indexFile file to use for the index
     * @return a new ChunkIndex instance
     * @throws IOException if the index cannot be created
     * @throws IllegalArgumentException if any parameter is null
     */
    public static ChunkIndex createFilesystemIndex(Path storageDirectory, Path indexFile)
            throws IOException {
        validateParameters(storageDirectory, indexFile);

        logger.info("Creating filesystem chunk index at {}", indexFile);

        return FilesystemChunkIndex.create(storageDirectory, indexFile);
    }

    /**
     * Creates an in-memory chunk index for testing or temporary use.
     *
     * @return a new ChunkIndex instance
     */
    public static ChunkIndex createMemoryIndex() {
        logger.info("Creating memory chunk index");

        return new MemoryChunkIndex();
    }

    /**
     * Validates parameters for filesystem index creation.
     *
     * @param storageDirectory storage directory
     * @param indexFile index file
     * @throws IllegalArgumentException if any parameter is null
     */
    private static void validateParameters(Path storageDirectory, Path indexFile) {
        if (storageDirectory == null) {
            throw new IllegalArgumentException("Storage directory cannot be null");
        }
        if (indexFile == null) {
            throw new IllegalArgumentException("Index file cannot be null");
        }
    }

    /**
     * In-memory implementation of ChunkIndex for testing and development.
     * This is a private implementation class used by the factory.
     */
    private static final class MemoryChunkIndex implements ChunkIndex {

        /** In-memory storage for the index. */
        private final ConcurrentHashMap<String, Path> indexMap;
        /** Flag indicating if the index has been closed. */
        private volatile boolean closed;

        /**
         * Creates a new MemoryChunkIndex.
         */
        MemoryChunkIndex() {
            this.indexMap = new ConcurrentHashMap<>();
            this.closed = false;
        }

        @Override
        public void putChunk(String hash, Path filePath) throws IOException {
            validateNotClosed();
            validateHash(hash);

            if (filePath == null) {
                throw new IllegalArgumentException("File path cannot be null");
            }

            indexMap.put(hash, filePath);
        }

        @Override
        public Path getChunkPath(String hash) throws IOException {
            validateNotClosed();
            validateHash(hash);

            return indexMap.get(hash);
        }

        @Override
        public boolean containsChunk(String hash) throws IOException {
            validateNotClosed();
            validateHash(hash);

            return indexMap.containsKey(hash);
        }

        @Override
        public boolean removeChunk(String hash) throws IOException {
            validateNotClosed();
            validateHash(hash);

            return indexMap.remove(hash) != null;
        }

        @Override
        public java.util.Set<String> getAllHashes() throws IOException {
            validateNotClosed();

            return new java.util.HashSet<>(indexMap.keySet());
        }

        @Override
        public long getChunkCount() throws IOException {
            validateNotClosed();

            return indexMap.size();
        }

        @Override
        public long retainAll(java.util.Set<String> activeHashes) throws IOException {
            validateNotClosed();

            if (activeHashes == null) {
                throw new IllegalArgumentException("Active hashes set cannot be null");
            }

            long removedCount = 0;
            java.util.Set<String> toRemove = new java.util.HashSet<>();

            for (String hash : indexMap.keySet()) {
                if (!activeHashes.contains(hash)) {
                    toRemove.add(hash);
                }
            }

            for (String hash : toRemove) {
                indexMap.remove(hash);
                removedCount++;
            }

            return removedCount;
        }

        @Override
        public void close() throws IOException {
            if (!closed) {
                indexMap.clear();
                closed = true;
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
}