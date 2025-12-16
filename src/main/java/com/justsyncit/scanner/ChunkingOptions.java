package com.justsyncit.scanner;

/**
 * Configuration options for file chunking operations.
 * Follows Builder pattern for flexible configuration.
 */
public class ChunkingOptions {

    /** Default chunk size (64KB). */
    private static final int DEFAULT_CHUNK_SIZE = 64 * 1024;

    /** Default minimum chunk size for CDC (4KB). */
    private static final int DEFAULT_MIN_CHUNK_SIZE = 4 * 1024;

    /** Default maximum chunk size for CDC (256KB). */
    private static final int DEFAULT_MAX_CHUNK_SIZE = 256 * 1024;

    /** Algorithm to use for chunking. */
    public enum ChunkingAlgorithm {
        FIXED,
        CDC
    }

    /** Whether to use asynchronous I/O. */
    private boolean useAsyncIO = true;

    /** Chunk size in bytes (used for Fixed and as average for CDC). */
    private int chunkSize = DEFAULT_CHUNK_SIZE;

    /** Minimum chunk size in bytes (CDC only). */
    private int minChunkSize = DEFAULT_MIN_CHUNK_SIZE;

    /** Maximum chunk size in bytes (CDC only). */
    private int maxChunkSize = DEFAULT_MAX_CHUNK_SIZE;

    /** Algorithm to use. */
    private ChunkingAlgorithm algorithm = ChunkingAlgorithm.FIXED;

    /** Whether to detect sparse files. */
    private boolean detectSparseFiles = true;

    /** Maximum number of concurrent chunks. */
    private int maxConcurrentChunks = 4;

    /** Progress callback. */
    private com.justsyncit.scanner.FileChunker.ChunkProgressCallback progressCallback;

    /** Status callback. */
    private com.justsyncit.scanner.FileChunker.ChunkStatusCallback statusCallback;

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
        this.minChunkSize = other.minChunkSize;
        this.maxChunkSize = other.maxChunkSize;
        this.algorithm = other.algorithm;
        this.detectSparseFiles = other.detectSparseFiles;
        this.maxConcurrentChunks = other.maxConcurrentChunks;
        this.progressCallback = other.progressCallback;
        this.statusCallback = other.statusCallback;
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
     * For Fixed algorithm, this is the exact chunk size.
     * For CDC algorithm, this is the target average chunk size.
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
     * Sets the minimum chunk size in bytes (CDC only).
     *
     * @param minChunkSize minimum chunk size in bytes, must be positive
     * @return this builder for method chaining
     * @throws IllegalArgumentException if minChunkSize is not positive
     */
    public ChunkingOptions withMinChunkSize(int minChunkSize) {
        if (minChunkSize <= 0) {
            throw new IllegalArgumentException("Minimum chunk size must be positive");
        }
        this.minChunkSize = minChunkSize;
        return this;
    }

    /**
     * Sets the maximum chunk size in bytes (CDC only).
     *
     * @param maxChunkSize maximum chunk size in bytes, must be positive
     * @return this builder for method chaining
     * @throws IllegalArgumentException if maxChunkSize is not positive
     */
    public ChunkingOptions withMaxChunkSize(int maxChunkSize) {
        if (maxChunkSize <= 0) {
            throw new IllegalArgumentException("Maximum chunk size must be positive");
        }
        this.maxChunkSize = maxChunkSize;
        return this;
    }

    /**
     * Sets the chunking algorithm to use.
     *
     * @param algorithm the chunking algorithm
     * @return this builder for method chaining
     */
    public ChunkingOptions withAlgorithm(ChunkingAlgorithm algorithm) {
        if (algorithm == null) {
            throw new IllegalArgumentException("Algorithm cannot be null");
        }
        this.algorithm = algorithm;
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
     * Sets the maximum number of concurrent chunks.
     *
     * @param maxConcurrentChunks maximum concurrent chunks
     * @return this builder for method chaining
     */
    public ChunkingOptions withMaxConcurrentChunks(int maxConcurrentChunks) {
        if (maxConcurrentChunks <= 0) {
            throw new IllegalArgumentException("Max concurrent chunks must be positive");
        }
        this.maxConcurrentChunks = maxConcurrentChunks;
        return this;
    }

    /**
     * Sets the progress callback.
     *
     * @param progressCallback callback for progress updates
     * @return this builder for method chaining
     */
    public ChunkingOptions withProgressCallback(
            com.justsyncit.scanner.FileChunker.ChunkProgressCallback progressCallback) {
        this.progressCallback = progressCallback;
        return this;
    }

    /**
     * Sets the status callback.
     *
     * @param statusCallback callback for status updates
     * @return this builder for method chaining
     */
    public ChunkingOptions withStatusCallback(com.justsyncit.scanner.FileChunker.ChunkStatusCallback statusCallback) {
        this.statusCallback = statusCallback;
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
     * Gets the minimum chunk size in bytes.
     *
     * @return minimum chunk size in bytes
     */
    public int getMinChunkSize() {
        return minChunkSize;
    }

    /**
     * Gets the maximum chunk size in bytes.
     *
     * @return maximum chunk size in bytes
     */
    public int getMaxChunkSize() {
        return maxChunkSize;
    }

    /**
     * Gets the chunking algorithm.
     *
     * @return the chunking algorithm
     */
    public ChunkingAlgorithm getAlgorithm() {
        return algorithm;
    }

    /**
     * Gets whether to detect sparse files.
     *
     * @return true if sparse file detection is enabled
     */
    public boolean isDetectSparseFiles() {
        return detectSparseFiles;
    }

    /**
     * Gets the maximum number of concurrent chunks.
     *
     * @return maximum concurrent chunks
     */
    public int getMaxConcurrentChunks() {
        return maxConcurrentChunks;
    }

    /**
     * Gets the progress callback.
     *
     * @return the progress callback
     */
    public com.justsyncit.scanner.FileChunker.ChunkProgressCallback getProgressCallback() {
        return progressCallback;
    }

    /**
     * Gets the status callback.
     *
     * @return the status callback
     */
    public com.justsyncit.scanner.FileChunker.ChunkStatusCallback getStatusCallback() {
        return statusCallback;
    }

    @Override
    public String toString() {
        return "ChunkingOptions{"
                + "useAsyncIO=" + useAsyncIO
                + ", chunkSize=" + chunkSize
                + ", minChunkSize=" + minChunkSize
                + ", maxChunkSize=" + maxChunkSize
                + ", algorithm=" + algorithm
                + ", detectSparseFiles=" + detectSparseFiles
                + ", maxConcurrentChunks=" + maxConcurrentChunks
                + '}';
    }
}