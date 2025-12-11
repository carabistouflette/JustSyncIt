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

package com.justsyncit.hash;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * Thread-safe file hashing implementation using BLAKE3 service.
 * Follows Single Responsibility Principle by focusing only on file operations.
 *
 * This implementation is thread-safe and can be safely used by multiple threads concurrently.
 */
public class Blake3FileHasher implements FileHasher {

    /** Logger for the file hasher. */
    private static final Logger logger = LoggerFactory.getLogger(Blake3FileHasher.class);

    /** Default buffer size for streaming operations (64KB). */
    private static final int DEFAULT_BUFFER_SIZE = 65536; // 64KB buffer for streaming
    
    /** Threshold for small files that can be read entirely into memory. */
    private static final long SMALL_FILE_THRESHOLD = 1024 * 1024; // 1MB
    
    /** Maximum file size to prevent resource exhaustion (100MB). */
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB

    /** Stream hasher instance. */
    private final StreamHasher streamHasher;

    /** Buffer hasher instance. */
    private final BufferHasher bufferHasher;

    /** Buffer size for streaming operations. */
    private final int bufferSize;
    
    /** Maximum allowed file size. */
    private final long maxFileSize;
    
    /** Thread safety lock for concurrent operations. */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Creates a new Blake3FileHasher with the provided dependencies and default settings.
     *
     * @param streamHasher the stream hashing service, must not be null
     * @param bufferHasher the buffer hashing service, must not be null
     * @throws IllegalArgumentException if any parameter is null
     */
    public Blake3FileHasher(StreamHasher streamHasher, BufferHasher bufferHasher) {
        this(streamHasher, bufferHasher, DEFAULT_BUFFER_SIZE, MAX_FILE_SIZE);
    }

    /**
     * Creates a new Blake3FileHasher with the provided dependencies and custom settings.
     *
     * @param streamHasher the stream hashing service, must not be null
     * @param bufferHasher the buffer hashing service, must not be null
     * @param bufferSize the buffer size for streaming operations, must be positive
     * @param maxFileSize the maximum allowed file size in bytes, must be positive
     * @throws IllegalArgumentException if any parameter is null or invalid
     */
    public Blake3FileHasher(StreamHasher streamHasher, BufferHasher bufferHasher,
                           int bufferSize, long maxFileSize) {
        this.streamHasher = Objects.requireNonNull(streamHasher, "Stream hasher cannot be null");
        this.bufferHasher = Objects.requireNonNull(bufferHasher, "Buffer hasher cannot be null");
        
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("Buffer size must be positive");
        }
        this.bufferSize = bufferSize;
        
        if (maxFileSize <= 0) {
            throw new IllegalArgumentException("Max file size must be positive");
        }
        this.maxFileSize = maxFileSize;
        
        logger.debug("Blake3FileHasher initialized with buffer size: {} bytes, max file size: {} bytes",
                    bufferSize, maxFileSize);
    }

    @Override
    public String hashFile(Path filePath) throws IOException {
        // Validate input parameters
        validateFilePath(filePath);
        
        // Acquire read lock for thread safety (allows concurrent reads)
        lock.readLock().lock();
        try {
            return performHashing(filePath);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Validates the file path and security constraints.
     *
     * @param filePath the file path to validate
     * @throws IllegalArgumentException if the path is invalid
     * @throws SecurityException if the path violates security constraints
     */
    private void validateFilePath(Path filePath) {
        if (filePath == null) {
            throw new IllegalArgumentException("File path cannot be null");
        }
        
        // Normalize the path to prevent path traversal attacks
        Path normalizedPath = filePath.normalize();
        
        // Check if the normalized path is different (potential traversal attempt)
        if (!normalizedPath.equals(filePath)) {
            logger.warn("Path normalization detected potential traversal attempt: {} -> {}",
                       filePath, normalizedPath);
        }
        
        // Additional security check: ensure path doesn't contain ".." segments
        if (filePath.toString().contains("..")) {
            throw new SecurityException("Path traversal not allowed: " + filePath);
        }
    }

    /**
     * Performs the actual file hashing with proper error handling and resource management.
     *
     * @param filePath the path to the file to hash
     * @return the hash as a hexadecimal string
     * @throws IOException if an I/O error occurs
     * @throws SecurityException if access to the file is denied
     * @throws IllegalArgumentException if the file doesn't exist or is invalid
     */
    private String performHashing(Path filePath) throws IOException {
        try {
            // Check file existence and properties
            if (!Files.exists(filePath)) {
                throw new IllegalArgumentException("File does not exist: " + filePath);
            }

            if (!Files.isRegularFile(filePath)) {
                throw new IllegalArgumentException("Path is not a regular file: " + filePath);
            }

            // Check file size to prevent resource exhaustion
            long fileSize;
            try {
                fileSize = Files.size(filePath);
            } catch (IOException e) {
                throw new IOException("Failed to determine file size: " + filePath, e);
            }

            if (fileSize > maxFileSize) {
                throw new IllegalArgumentException("File size exceeds maximum allowed size: " +
                                              fileSize + " bytes (max: " + maxFileSize + " bytes)");
            }

            logger.debug("Hashing file: {} ({} bytes)", filePath, fileSize);

            // Choose hashing strategy based on file size
            if (fileSize <= SMALL_FILE_THRESHOLD) {
                return hashSmallFile(filePath, fileSize);
            } else {
                return hashLargeFile(filePath, fileSize);
            }
        } catch (Exception e) {
            logger.error("Failed to hash file: {}", filePath, e);
            
            // Re-throw with proper exception type
            if (e instanceof IOException) {
                throw (IOException) e;
            } else if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            } else if (e instanceof RuntimeException) {
                throw new IOException("Unexpected error while hashing file: " + filePath, e);
            } else {
                throw new IOException("Error while hashing file: " + filePath, e);
            }
        }
    }

    /**
     * Hashes a small file by reading it entirely into memory.
     *
     * @param filePath the path to the file
     * @param fileSize the size of the file in bytes
     * @return the hash as a hexadecimal string
     * @throws HashingException if hashing fails
     * @throws RuntimeException if an I/O error occurs (wrapped)
     */
    private String hashSmallFile(Path filePath, long fileSize) throws HashingException {
        logger.trace("Using small file strategy for {} bytes", fileSize);
        
        try {
            byte[] data = Files.readAllBytes(filePath);
            return bufferHasher.hashBuffer(data);
        } catch (OutOfMemoryError e) {
            // Fallback to streaming if memory is insufficient
            logger.warn("Memory insufficient for small file strategy, falling back to streaming for: {}", filePath);
            try {
                return hashLargeFile(filePath, fileSize);
            } catch (Exception fallbackException) {
                throw new HashingException("Failed to hash file with both strategies", fallbackException);
            }
        } catch (IOException e) {
            throw new HashingException("Failed to read small file: " + filePath, e);
        }
    }

    /**
     * Hashes a large file using streaming approach.
     *
     * @param filePath the path to the file
     * @param fileSize the size of the file in bytes
     * @return the hash as a hexadecimal string
     * @throws HashingException if hashing fails
     * @throws RuntimeException if an I/O error occurs (wrapped)
     */
    private String hashLargeFile(Path filePath, long fileSize) throws HashingException {
        logger.trace("Using streaming strategy for {} bytes with buffer size: {}", fileSize, bufferSize);
        
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            return streamHasher.hashStream(inputStream);
        } catch (IOException e) {
            throw new HashingException("Failed to open stream for large file: " + filePath, e);
        }
    }

    /**
     * Gets the current buffer size used for streaming operations.
     *
     * @return the buffer size in bytes
     */
    public int getBufferSize() {
        lock.readLock().lock();
        try {
            return bufferSize;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets the maximum allowed file size.
     *
     * @return the maximum file size in bytes
     */
    public long getMaxFileSizeValue() {
        lock.readLock().lock();
        try {
            return maxFileSize;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<Long> getMaxFileSize() {
        return Optional.of(getMaxFileSizeValue());
    }

    @Override
    public HashAlgorithm getHashAlgorithm() {
        // This implementation doesn't directly expose the hash algorithm
        // Return a default implementation or throw UnsupportedOperationException
        throw new UnsupportedOperationException("Hash algorithm not directly accessible in this implementation");
    }

    @Override
    public boolean supportsCancellation() {
        return false; // This implementation doesn't support cancellation
    }

    @Override
    public FileHasher.HashingContext createHashingContext() {
        throw new UnsupportedOperationException("Hashing context not supported by this implementation");
    }

    @Override
    public CompletableFuture<String> hashFileAsync(Path filePath) throws IllegalArgumentException, SecurityException {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return hashFile(filePath);
            } catch (IOException e) {
                throw new RuntimeException("Async hashing failed", e);
            }
        });
    }

    @Override
    public String hashFileRange(Path filePath, long offset, long length)
            throws IOException, IllegalArgumentException, SecurityException, HashingException {
        // For simplicity, delegate to the main hashFile method
        // A full implementation would need to implement range-based hashing
        return hashFile(filePath);
    }

    @Override
    public String hashFileWithProgress(Path filePath, Consumer<Double> progressCallback)
            throws IOException, IllegalArgumentException, SecurityException, HashingException {
        // For simplicity, delegate to the main hashFile method
        // A full implementation would need to report progress
        return hashFile(filePath);
    }

    @Override
    public boolean validateFileHash(Path filePath, String expectedHash)
            throws IOException, IllegalArgumentException, SecurityException, HashingException {
        String actualHash = hashFile(filePath);
        return actualHash.equals(expectedHash);
    }

    @Override
    public void close() throws IOException {
        // No resources to close in this implementation
        // Method is required by the FileHasher interface
    }
}