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
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stream hashing implementation using BLAKE3 algorithm.
 * Follows Single Responsibility Principle by focusing only on stream operations.
 *
 * This class is thread-safe. Multiple threads can safely use this instance to hash
 * different streams concurrently, as each hashing operation uses its own buffer
 * and hasher instance.
 */
public class Blake3StreamHasher implements StreamHasher {

    /** Logger for the stream hasher. */
    private static final Logger logger = LoggerFactory.getLogger(Blake3StreamHasher.class);
    
    /** Default buffer size for streaming operations (32KB). */
    private static final int DEFAULT_BUFFER_SIZE = 32768; // 32KB buffer for optimal performance
    
    /** Maximum allowed stream size to prevent resource exhaustion (10GB). */
    private static final long MAX_STREAM_SIZE = 10L * 1024 * 1024 * 1024; // 10GB
    
    /** Incremental hasher factory. */
    private final IncrementalHasherFactory incrementalHasherFactory;
    
    /** Buffer size for streaming operations. */
    private final int bufferSize;
    
    /** Maximum allowed stream size. */
    private final long maxStreamSize;
    
    /** Counter for tracking active hashing operations. */
    private final AtomicLong activeOperations = new AtomicLong(0);

    /**
     * Creates a new Blake3StreamHasher with the provided factory using default buffer size.
     *
     * @param incrementalHasherFactory the factory for creating incremental hashers
     * @throws IllegalArgumentException if incrementalHasherFactory is null
     */
    public Blake3StreamHasher(IncrementalHasherFactory incrementalHasherFactory) {
        this(incrementalHasherFactory, DEFAULT_BUFFER_SIZE, MAX_STREAM_SIZE);
    }

    /**
     * Creates a new Blake3StreamHasher with the provided factory and custom buffer size.
     *
     * @param incrementalHasherFactory the factory for creating incremental hashers
     * @param bufferSize the buffer size to use for streaming operations (must be > 0)
     * @param maxStreamSize the maximum allowed stream size in bytes (must be > 0)
     * @throws IllegalArgumentException if any parameter is null or invalid
     */
    public Blake3StreamHasher(IncrementalHasherFactory incrementalHasherFactory, int bufferSize, long maxStreamSize) {
        this.incrementalHasherFactory = Objects.requireNonNull(incrementalHasherFactory,
                "Incremental hasher factory cannot be null");
        
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("Buffer size must be positive");
        }
        this.bufferSize = bufferSize;
        
        if (maxStreamSize <= 0) {
            throw new IllegalArgumentException("Maximum stream size must be positive");
        }
        this.maxStreamSize = maxStreamSize;
        
        logger.debug("Blake3StreamHasher initialized with buffer size: {} bytes, max stream size: {} bytes",
                    bufferSize, maxStreamSize);
    }

    @Override
    public String hashStream(InputStream inputStream) throws IOException, HashingException {
        return hashStream(inputStream, (StreamHasher.HashProgressListener) null);
    }
    
    @Override
    public String hashStream(InputStream inputStream, StreamHasher.HashProgressListener progressListener)
            throws IOException, HashingException {
        Objects.requireNonNull(inputStream, "Input stream cannot be null");

        logger.trace("Starting stream hashing operation");
        activeOperations.incrementAndGet();
        
        try {
            IncrementalHasherFactory.IncrementalHasher hasher = incrementalHasherFactory.createIncrementalHasher();
            byte[] buffer = new byte[bufferSize];
            long totalBytesRead = 0;
            int bytesRead;

            try {
                long startTime = System.currentTimeMillis();
                long lastProgressTime = startTime;
                final long TIMEOUT_MS = 30000; // 30 seconds timeout
                
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    // Check for timeout
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - startTime > TIMEOUT_MS) {
                        throw new HashingException("Stream hashing timeout after " + TIMEOUT_MS + "ms");
                    }
                    
                    // Check stream size limit
                    totalBytesRead += bytesRead;
                    if (totalBytesRead > maxStreamSize) {
                        throw new HashingException("Stream size exceeds maximum allowed size of " + maxStreamSize + " bytes");
                    }
                    
                    hasher.update(buffer, 0, bytesRead);
                    
                    // Report progress if listener is provided (limit to avoid excessive logging)
                    if (progressListener != null && currentTime - lastProgressTime > 1000) { // Report every second
                        progressListener.onProgress(totalBytesRead);
                        lastProgressTime = currentTime;
                    }
                }

                String hash = hasher.digest();
                logger.debug("Successfully hashed {} bytes", totalBytesRead);
                
                if (progressListener != null) {
                    progressListener.onComplete(totalBytesRead, hash);
                }
                
                return hash;
            } catch (IOException e) {
                logger.error("I/O error while reading from input stream after {} bytes", totalBytesRead, e);
                if (progressListener != null) {
                    progressListener.onError(totalBytesRead, e);
                }
                throw e;
            } catch (HashingException e) {
                logger.error("Hashing error after processing {} bytes", totalBytesRead, e);
                if (progressListener != null) {
                    progressListener.onError(totalBytesRead, e);
                }
                throw e;
            } catch (Exception e) {
                logger.error("Unexpected error while hashing stream after {} bytes", totalBytesRead, e);
                HashingException hashingException = new HashingException("Failed to hash stream", e);
                if (progressListener != null) {
                    progressListener.onError(totalBytesRead, hashingException);
                }
                throw hashingException;
            }
        } finally {
            activeOperations.decrementAndGet();
        }
    }
    
    @Override
    public String hashStreamRange(InputStream inputStream, long offset, long length)
            throws IOException, HashingException {
        return hashStreamRange(inputStream, offset, length, null);
    }

    @Override
    public String hashStreamRange(InputStream inputStream, long offset, long length,
                                StreamHasher.HashProgressListener progressListener)
            throws IOException, HashingException {
        Objects.requireNonNull(inputStream, "Input stream cannot be null");
        
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative");
        }
        if (length < 0) {
            throw new IllegalArgumentException("Length cannot be negative");
        }
        
        logger.trace("Hashing stream range: offset={}, length={}", offset, length);
        activeOperations.incrementAndGet();
        
        try {
            // Skip to the offset
            if (offset > 0) {
                long skipped = inputStream.skip(offset);
                if (skipped != offset) {
                    throw new IOException("Failed to skip to offset " + offset +
                                          ". Only skipped " + skipped + " bytes.");
                }
            }
            
            IncrementalHasherFactory.IncrementalHasher hasher = incrementalHasherFactory.createIncrementalHasher();
            byte[] buffer = new byte[bufferSize];
            long totalBytesRead = 0;
            int bytesRead;
            long remainingBytes = (length == 0) ? Long.MAX_VALUE : length;

            try {
                while (remainingBytes > 0 && (bytesRead = inputStream.read(buffer, 0,
                        (int) Math.min(buffer.length, remainingBytes))) != -1) {
                    totalBytesRead += bytesRead;
                    remainingBytes -= bytesRead;
                    
                    // Check stream size limit
                    if (totalBytesRead > maxStreamSize) {
                        throw new HashingException("Stream size exceeds maximum allowed size of " + maxStreamSize + " bytes");
                    }
                    
                    hasher.update(buffer, 0, bytesRead);
                }

                String hash = hasher.digest();
                logger.debug("Successfully hashed {} bytes from stream range", totalBytesRead);
                return hash;
            } catch (IOException e) {
                logger.error("I/O error while reading from input stream range after {} bytes", totalBytesRead, e);
                throw e;
            } catch (HashingException e) {
                logger.error("Hashing error after processing {} bytes from stream range", totalBytesRead, e);
                throw e;
            } catch (Exception e) {
                logger.error("Unexpected error while hashing stream range after {} bytes", totalBytesRead, e);
                throw new HashingException("Failed to hash stream range", e);
            }
        } finally {
            activeOperations.decrementAndGet();
        }
    }
    
    @Override
    public String getAlgorithmName() {
        return incrementalHasherFactory.getAlgorithmName();
    }
    
    @Override
    public int getHashLength() {
        return incrementalHasherFactory.getHashLength();
    }
    
    @Override
    public int getBufferSize() {
        return bufferSize;
    }
    
    @Override
    public long getMaxStreamSize() {
        return maxStreamSize;
    }
    
    @Override
    public boolean isThreadSafe() {
        return true;
    }
    
    @Override
    public long getActiveOperationsCount() {
        return activeOperations.get();
    }
}