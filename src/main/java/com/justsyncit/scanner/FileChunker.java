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

package com.justsyncit.scanner;

import com.justsyncit.storage.ChunkStorage;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for chunking files into fixed-size pieces.
 * Extends ChunkStorage to maintain compatibility with existing storage system.
 * Follows Interface Segregation Principle by providing focused chunking
 * operations.
 */
public interface FileChunker extends ChunkStorage {

    /**
     * Chunks a file into fixed-size pieces.
     *
     * @param file    the file to chunk
     * @param options the chunking options
     * @return a CompletableFuture that completes with the chunking result
     * @throws IllegalArgumentException if file is null or invalid
     */
    CompletableFuture<ChunkingResult> chunkFile(Path file, ChunkingOptions options);

    /**
     * Sets the buffer pool for memory management.
     * If no pool is set, a default pool will be used.
     *
     * @param bufferPool the buffer pool to use
     */
    void setBufferPool(BufferPool bufferPool);

    /**
     * Sets the chunk size for chunking operations.
     * Default is 64KB.
     *
     * @param chunkSize the chunk size in bytes
     * @throws IllegalArgumentException if chunkSize is not positive
     */
    void setChunkSize(int chunkSize);

    /**
     * Gets the current chunk size.
     *
     * @return the chunk size in bytes
     */
    int getChunkSize();

    /**
     * Result of a chunking operation.
     */
    class ChunkingResult {
        /** File that was chunked. */
        private final Path file;
        /** Number of chunks created. */
        private final int chunkCount;
        /** Total file size in bytes. */
        private final long totalSize;
        /** Size of sparse regions in bytes. */
        private final long sparseSize;
        /** Hash of the entire file. */
        private final String fileHash;
        /** List of chunk hashes in order. */
        private final java.util.List<String> chunkHashes;
        /** Error if chunking failed. */
        private final Exception error;

        /**
         * Creates a successful ChunkingResult.
         *
         * @param file        the file that was chunked
         * @param chunkCount  number of chunks created
         * @param totalSize   total file size in bytes
         * @param sparseSize  size of sparse regions in bytes
         * @param fileHash    hash of the entire file
         * @param chunkHashes list of chunk hashes in order
         */
        public ChunkingResult(Path file, int chunkCount, long totalSize, long sparseSize,
                String fileHash, java.util.List<String> chunkHashes) {
            this.file = file;
            this.chunkCount = chunkCount;
            this.totalSize = totalSize;
            this.sparseSize = sparseSize;
            this.fileHash = fileHash;
            this.chunkHashes = chunkHashes != null ? new java.util.ArrayList<>(chunkHashes) : null;
            this.error = null;
        }

        /**
         * Creates a failed ChunkingResult.
         *
         * @param file  the file that failed to chunk
         * @param error the exception that occurred
         * @deprecated Use {@link #createFailed(Path, Exception)} instead
         */
        @Deprecated
        public ChunkingResult(Path file, Exception error) {
            this.file = file;
            this.chunkCount = 0;
            this.totalSize = 0;
            this.sparseSize = 0;
            this.fileHash = null;
            this.chunkHashes = java.util.Collections.emptyList();
            this.error = error != null ? createExceptionCopy(error) : null;
        }

        /**
         * Creates a failed ChunkingResult.
         *
         * @param file  the file that failed to chunk
         * @param error the exception that occurred
         * @return a new failed ChunkingResult
         */
        public static ChunkingResult createFailed(Path file, Exception error) {
            return new ChunkingResult(file, error);
        }

        /**
         * Gets the file that was chunked.
         *
         * @return the file path
         */
        public Path getFile() {
            return file;
        }

        /**
         * Gets the number of chunks created.
         *
         * @return the chunk count
         */
        public int getChunkCount() {
            return chunkCount;
        }

        /**
         * Gets the total file size in bytes.
         *
         * @return the total size
         */
        public long getTotalSize() {
            return totalSize;
        }

        /**
         * Gets the size of sparse regions in bytes.
         *
         * @return the sparse size
         */
        public long getSparseSize() {
            return sparseSize;
        }

        /**
         * Gets the hash of the entire file.
         *
         * @return the file hash
         */
        public String getFileHash() {
            return fileHash;
        }

        /**
         * Gets the list of chunk hashes in order.
         *
         * @return immutable list of chunk hashes
         */
        public java.util.List<String> getChunkHashes() {
            return chunkHashes != null ? new java.util.ArrayList<>(chunkHashes) : null;
        }

        /**
         * Gets the error if chunking failed.
         *
         * @return the error, or null if successful
         */
        public Exception getError() {
            return error != null ? createExceptionCopy(error) : null;
        }

        /**
         * Creates a copy of an exception to avoid exposing internal representation.
         *
         * @param original the original exception
         * @return a copy of the exception
         */
        private static Exception createExceptionCopy(Exception original) {
            try {
                return (Exception) original.getClass()
                        .getConstructor(String.class)
                        .newInstance(original.getMessage());
            } catch (ReflectiveOperationException e) {
                // Fallback to a generic exception if copying fails
                return new RuntimeException(original.getMessage(), original.getCause());
            }
        }

        /**
         * Checks if the chunking was successful.
         *
         * @return true if successful, false otherwise
         */
        public boolean isSuccess() {
            return error == null;
        }
    }

    // Inner class ChunkingOptions removed to use top-level
    // com.justsyncit.scanner.ChunkingOptions

    /**
     * Callback for chunking progress.
     */
    @FunctionalInterface
    public interface ChunkProgressCallback {
        /**
         * Called when bytes are processed.
         *
         * @param bytesProcessed number of bytes processed in this step
         */
        void onProgress(long bytesProcessed);
    }

    /**
     * Callback for chunking status updates.
     */
    @FunctionalInterface
    public interface ChunkStatusCallback {
        /**
         * Called when the status changes.
         *
         * @param status the new status description
         */
        void onStatus(String status);
    }
}