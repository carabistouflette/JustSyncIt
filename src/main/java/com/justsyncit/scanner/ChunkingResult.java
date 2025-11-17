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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.justsyncit.scanner;

import java.nio.file.Path;
import java.util.List;

/**
 * Result of a file chunking operation.
 * Contains metadata about the chunking process and resulting chunks.
 */
public class ChunkingResult {
    
    /** The file that was chunked. */
    private final Path file;
    
    /** Number of chunks created. */
    private final int chunkCount;
    
    /** Total size of the file in bytes. */
    private final long fileSize;
    
    /** Size of sparse regions in bytes (0 if not sparse). */
    private final long sparseSize;
    
    /** Hash of the entire file. */
    private final String fileHash;
    
    /** List of chunk hashes in order. */
    private final List<String> chunkHashes;
    
    /** Error that occurred during chunking, if any. */
    private final Exception error;
    
    /** Whether the operation was successful. */
    private final boolean success;
    
    /**
     * Creates a successful chunking result.
     *
     * @param file the file that was chunked
     * @param chunkCount number of chunks created
     * @param fileSize total size of the file
     * @param sparseSize size of sparse regions
     * @param fileHash hash of the entire file
     * @param chunkHashes list of chunk hashes
     */
    public ChunkingResult(Path file, int chunkCount, long fileSize, long sparseSize,
                       String fileHash, List<String> chunkHashes) {
        this.file = file;
        this.chunkCount = chunkCount;
        this.fileSize = fileSize;
        this.sparseSize = sparseSize;
        this.fileHash = fileHash;
        this.chunkHashes = chunkHashes;
        this.error = null;
        this.success = true;
    }
    
    /**
     * Creates a failed chunking result.
     *
     * @param file the file that failed to chunk
     * @param error the error that occurred
     */
    public ChunkingResult(Path file, Exception error) {
        this.file = file;
        this.error = error;
        this.success = false;
        this.chunkCount = 0;
        this.fileSize = 0;
        this.sparseSize = 0;
        this.fileHash = null;
        this.chunkHashes = null;
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
     * @return chunk count
     */
    public int getChunkCount() {
        return chunkCount;
    }
    
    /**
     * Gets the total size of the file.
     *
     * @return file size in bytes
     */
    public long getFileSize() {
        return fileSize;
    }
    
    /**
     * Gets the size of sparse regions.
     *
     * @return sparse size in bytes (0 if not sparse)
     */
    public long getSparseSize() {
        return sparseSize;
    }
    
    /**
     * Gets the hash of the entire file.
     *
     * @return file hash
     */
    public String getFileHash() {
        return fileHash;
    }
    
    /**
     * Gets the list of chunk hashes.
     *
     * @return immutable list of chunk hashes
     */
    public List<String> getChunkHashes() {
        return chunkHashes;
    }
    
    /**
     * Gets the error that occurred during chunking.
     *
     * @return the error, or null if successful
     */
    public Exception getError() {
        return error;
    }
    
    /**
     * Gets whether the chunking operation was successful.
     *
     * @return true if successful, false otherwise
     */
    public boolean isSuccess() {
        return success;
    }
    
    /**
     * Gets whether the file is sparse.
     *
     * @return true if sparse, false otherwise
     */
    public boolean isSparse() {
        return sparseSize > 0;
    }
    
    @Override
    public String toString() {
        if (success) {
            return "ChunkingResult{" +
                    "file=" + file +
                    ", chunkCount=" + chunkCount +
                    ", fileSize=" + fileSize +
                    ", sparseSize=" + sparseSize +
                    ", fileHash='" + fileHash + '\'' +
                    ", chunkCount=" + chunkHashes.size() +
                    '}';
        } else {
            return "ChunkingResult{" +
                    "file=" + file +
                    ", error=" + error +
                    ", success=false" +
                    '}';
        }
    }
}