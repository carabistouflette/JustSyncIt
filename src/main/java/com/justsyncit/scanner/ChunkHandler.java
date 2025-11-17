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

import com.justsyncit.storage.metadata.FileMetadata;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for handling chunks during file processing.
 * Follows Interface Segregation Principle by providing focused chunk handling operations.
 */
public interface ChunkHandler {

    /**
     * Handles a chunk of data from a file.
     *
     * @param chunkData the chunk data
     * @param metadata metadata about the chunk
     * @return a CompletableFuture that completes with the chunk hash when stored
     */
    CompletableFuture<String> handleChunk(byte[] chunkData, ChunkMetadata metadata);

    /**
     * Called when all chunks from a file have been processed.
     *
     * @param fileMetadata complete metadata for the processed file
     */
    void onFileComplete(FileMetadata fileMetadata);

    /**
     * Called when an error occurs during file processing.
     *
     * @param file the file being processed when error occurred
     * @param error the exception that occurred
     */
    void onError(Path file, Exception error);

    /**
     * Metadata for a chunk being processed.
     */
    class ChunkMetadata {
        private final long offset;
        private final int size;
        private final boolean isSparse;
        private final String fileHash;

        /**
         * Creates new ChunkMetadata.
         *
         * @param offset offset of chunk in file
         * @param size size of chunk in bytes
         * @param isSparse whether this is a sparse chunk
         * @param fileHash hash of the entire file
         */
        public ChunkMetadata(long offset, int size, boolean isSparse, String fileHash) {
            this.offset = offset;
            this.size = size;
            this.isSparse = isSparse;
            this.fileHash = fileHash;
        }

        /**
         * Gets the offset of this chunk in the file.
         *
         * @return the chunk offset
         */
        public long getOffset() {
            return offset;
        }

        /**
         * Gets the size of this chunk.
         *
         * @return the chunk size
         */
        public int getSize() {
            return size;
        }

        /**
         * Checks if this is a sparse chunk.
         *
         * @return true if sparse, false otherwise
         */
        public boolean isSparse() {
            return isSparse;
        }

        /**
         * Gets the hash of the entire file.
         *
         * @return the file hash
         */
        public String getFileHash() {
            return fileHash;
        }
    }
}