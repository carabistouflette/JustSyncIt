package com.justsyncit.scanner;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for handling asynchronous chunk processing operations.
 * Follows Single Responsibility Principle by focusing only on chunk handling.
 * Provides callback-based and CompletableFuture-based APIs for flexibility.
 */
public interface AsyncChunkHandler {

    /**
     * Asynchronously processes a single chunk of data.
     *
     * @param chunkData   the chunk data to process
     * @param chunkIndex  the index of this chunk within the file
     * @param totalChunks the total number of chunks in the file
     * @param file        the source file (for context)
     * @return a CompletableFuture that completes with the hash of the processed
     *         chunk
     * @throws IllegalArgumentException if chunkData is null or invalid
     */
    CompletableFuture<String> processChunkAsync(ByteBuffer chunkData, int chunkIndex, int totalChunks, Path file);

    /**
     * Asynchronously processes a single chunk of data with CompletionHandler
     * pattern.
     *
     * @param chunkData   the chunk data to process
     * @param chunkIndex  the index of this chunk within the file
     * @param totalChunks the total number of chunks in the file
     * @param file        the source file (for context)
     * @param handler     the completion handler to notify when processing is done
     * @throws IllegalArgumentException if chunkData is null or invalid or handler
     *                                  is null
     */
    void processChunkAsync(ByteBuffer chunkData, int chunkIndex, int totalChunks, Path file,
            CompletionHandler<String, Exception> handler);

    /**
     * Asynchronously processes multiple chunks concurrently.
     *
     * @param chunks array of chunk data to process
     * @param file   the source file (for context)
     * @return a CompletableFuture that completes with an array of chunk hashes in
     *         the same order
     * @throws IllegalArgumentException if chunks is null or contains null elements
     */
    CompletableFuture<String[]> processChunksAsync(ByteBuffer[] chunks, Path file);

    /**
     * Asynchronously processes multiple chunks concurrently with CompletionHandler
     * pattern.
     *
     * @param chunks  array of chunk data to process
     * @param file    the source file (for context)
     * @param handler the completion handler to notify when processing is done
     * @throws IllegalArgumentException if chunks is null or contains null elements
     *                                  or handler is null
     */
    void processChunksAsync(ByteBuffer[] chunks, Path file,
            CompletionHandler<String[], Exception> handler);

    /**
     * Gets the maximum number of concurrent chunks this handler can process.
     *
     * @return the maximum number of concurrent chunks
     */
    int getMaxConcurrentChunks();

    /**
     * Sets the maximum number of concurrent chunks this handler can process.
     *
     * @param maxConcurrentChunks the maximum number of concurrent chunks
     * @throws IllegalArgumentException if maxConcurrentChunks is not positive
     */
    void setMaxConcurrentChunks(int maxConcurrentChunks);

    /**
     * Checks if this handler supports backpressure control.
     *
     * @return true if backpressure control is supported, false otherwise
     */
    default boolean supportsBackpressure() {
        return false;
    }

    /**
     * Applies backpressure if supported. This method should be called before
     * submitting
     * new chunks for processing to prevent overwhelming the system.
     *
     * @return a CompletableFuture that completes when backpressure control allows
     *         processing
     * @throws UnsupportedOperationException if backpressure is not supported
     */
    default CompletableFuture<Void> applyBackpressure() {
        if (supportsBackpressure()) {
            return CompletableFuture.completedFuture(null);
        }
        throw new UnsupportedOperationException("Backpressure control is not supported");
    }

    /**
     * Releases backpressure after processing is complete. This method should be
     * called
     * after chunk processing completes to allow more processing.
     *
     * @return a CompletableFuture that completes when backpressure is released
     * @throws UnsupportedOperationException if backpressure is not supported
     */
    default CompletableFuture<Void> releaseBackpressure() {
        if (supportsBackpressure()) {
            return CompletableFuture.completedFuture(null);
        }
        throw new UnsupportedOperationException("Backpressure control is not supported");
    }
}