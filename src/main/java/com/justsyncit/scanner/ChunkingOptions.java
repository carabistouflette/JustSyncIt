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

/**
 * Configuration options for file chunking operations.
 * Follows Builder pattern for flexible configuration.
 */
public class ChunkingOptions {
    
    /** Default chunk size (64KB). */
    private static final int DEFAULT_CHUNK_SIZE = 64 * 1024;
    
    /** Whether to use asynchronous I/O. */
    private boolean useAsyncIO = true;
    
    /** Chunk size in bytes. */
    private int chunkSize = DEFAULT_CHUNK_SIZE;
    
    /** Whether to detect sparse files. */
    private boolean detectSparseFiles = true;
    
    /**
     * Creates a new ChunkingOptions with default settings.
     */
    public ChunkingOptions() {
        // Default constructor with sensible defaults
    }
    
    /**
     * Creates a new ChunkingOptions as a copy of existing options.
     *
     * @param other options to copy
     */
    public ChunkingOptions(ChunkingOptions other) {
        this.useAsyncIO = other.useAsyncIO;
        this.chunkSize = other.chunkSize;
        this.detectSparseFiles = other.detectSparseFiles;
    }
    
    /**
     * Sets whether to use asynchronous I/O.
     *
     * @param useAsyncIO true to use async I/O, false for sync I/O
     * @return this builder for method chaining
     */
    public ChunkingOptions withUseAsyncIO(boolean useAsyncIO) {
        this.useAsyncIO = useAsyncIO;
        return this;
    }
    
    /**
     * Sets the chunk size in bytes.
     *
     * @param chunkSize chunk size in bytes, must be positive
     * @return this builder for method chaining
     * @throws IllegalArgumentException if chunkSize is not positive
     */
    public ChunkingOptions withChunkSize(int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("Chunk size must be positive");
        }
        this.chunkSize = chunkSize;
        return this;
    }
    
    /**
     * Sets whether to detect sparse files.
     *
     * @param detectSparseFiles true to detect sparse files
     * @return this builder for method chaining
     */
    public ChunkingOptions withDetectSparseFiles(boolean detectSparseFiles) {
        this.detectSparseFiles = detectSparseFiles;
        return this;
    }
    
    /**
     * Gets whether to use asynchronous I/O.
     *
     * @return true if async I/O is enabled
     */
    public boolean isUseAsyncIO() {
        return useAsyncIO;
    }
    
    /**
     * Gets the chunk size in bytes.
     *
     * @return chunk size in bytes
     */
    public int getChunkSize() {
        return chunkSize;
    }
    
    /**
     * Gets whether to detect sparse files.
     *
     * @return true if sparse file detection is enabled
     */
    public boolean isDetectSparseFiles() {
        return detectSparseFiles;
    }
    
    @Override
    public String toString() {
        return "ChunkingOptions{" +
                "useAsyncIO=" + useAsyncIO +
                ", chunkSize=" + chunkSize +
                ", detectSparseFiles=" + detectSparseFiles +
                '}';
    }
}