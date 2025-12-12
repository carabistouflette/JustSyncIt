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

import com.justsyncit.simd.SimdDetectionService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

/**
 * Refactored BLAKE3 service that follows SOLID principles.
 * This class acts as a facade that composes smaller, focused services.
 */
public class Blake3ServiceImpl implements Blake3Service {

    /** File hashing service. */
    private final FileHasher fileHasher;
    /** Buffer hashing service. */
    private final BufferHasher bufferHasher;
    /** Stream hashing service. */
    private final StreamHasher streamHasher;
    /** Incremental hasher factory. */
    private final IncrementalHasherFactory incrementalHasherFactory;
    /** BLAKE3 information provider. */
    private final Blake3Info blake3Info;
    /** Executor for parallel operations. */
    private final Executor executor;

    /**
     * Creates a new Blake3ServiceRefactored with all required dependencies.
     *
     * @param fileHasher the file hashing service
     * @param bufferHasher the buffer hashing service
     * @param streamHasher the stream hashing service
     * @param incrementalHasherFactory the incremental hasher factory
     * @param simdDetectionService the SIMD detection service
     */
    public Blake3ServiceImpl(
            FileHasher fileHasher,
            BufferHasher bufferHasher,
            StreamHasher streamHasher,
            IncrementalHasherFactory incrementalHasherFactory,
            SimdDetectionService simdDetectionService) {
        this(fileHasher, bufferHasher, streamHasher, incrementalHasherFactory,
             simdDetectionService, ForkJoinPool.commonPool());
    }

    /**
     * Creates a new Blake3ServiceImpl with all required dependencies and a custom executor.
     *
     * @param fileHasher the file hashing service
     * @param bufferHasher the buffer hashing service
     * @param streamHasher the stream hashing service
     * @param incrementalHasherFactory the incremental hasher factory
     * @param simdDetectionService the SIMD detection service
     * @param executor the executor for parallel operations
     */
    public Blake3ServiceImpl(
            FileHasher fileHasher,
            BufferHasher bufferHasher,
            StreamHasher streamHasher,
            IncrementalHasherFactory incrementalHasherFactory,
            SimdDetectionService simdDetectionService,
            Executor executor) {
        this.fileHasher = Objects.requireNonNull(fileHasher, "FileHasher cannot be null");
        this.bufferHasher = Objects.requireNonNull(bufferHasher, "BufferHasher cannot be null");
        this.streamHasher = Objects.requireNonNull(streamHasher, "StreamHasher cannot be null");
        this.incrementalHasherFactory = Objects.requireNonNull(incrementalHasherFactory,
                                                               "IncrementalHasherFactory cannot be null");
        this.blake3Info = new Blake3InfoImpl(Objects.requireNonNull(simdDetectionService,
                                                                    "SimdDetectionService cannot be null"));
        this.executor = Objects.requireNonNull(executor, "Executor cannot be null");
    }

    @Override
    public String hashFile(Path filePath) throws IOException, HashingException {
        if (filePath == null) {
            throw new IllegalArgumentException("File path cannot be null");
        }
        return fileHasher.hashFile(filePath);
    }

    @Override
    public String hashBuffer(byte[] data) throws HashingException {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        return bufferHasher.hashBuffer(data);
    }

    @Override
    public String hashBuffer(byte[] data, int offset, int length) throws HashingException {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IllegalArgumentException(
                String.format("Invalid offset/length: offset=%d, length=%d, array.length=%d",
                              offset, length, data.length));
        }
        // Allow empty arrays (offset=0, length=0) even when data.length=0
        if (data.length == 0 && offset == 0 && length == 0) {
            return bufferHasher.hashBuffer(data, offset, length);
        }
        // For non-empty arrays, ensure offset is within bounds
        if (data.length > 0 && offset >= data.length) {
            throw new IllegalArgumentException(
                String.format("Invalid offset/length: offset=%d, length=%d, array.length=%d",
                              offset, length, data.length));
        }
        return bufferHasher.hashBuffer(data, offset, length);
    }

    @Override
    public String hashBuffer(ByteBuffer buffer) throws HashingException {
        if (buffer == null) {
            throw new IllegalArgumentException("Buffer cannot be null");
        }

        // Convert ByteBuffer to byte array for hashing
        byte[] data;
        int originalPosition = buffer.position();

        try {
            if (buffer.hasArray()) {
                data = buffer.array();
                int arrayOffset = buffer.arrayOffset();
                int position = buffer.position();
                int limit = buffer.limit();
                return bufferHasher.hashBuffer(data, arrayOffset + position, limit - position);
            } else {
                data = new byte[buffer.remaining()];
                buffer.get(data);
                return bufferHasher.hashBuffer(data);
            }
        } finally {
            // Restore the original buffer position
            buffer.position(originalPosition);
        }
    }

    @Override
    public String hashStream(InputStream inputStream) throws IOException, HashingException {
        if (inputStream == null) {
            throw new IllegalArgumentException("InputStream cannot be null");
        }
        return streamHasher.hashStream(inputStream);
    }

    @Override
    public Blake3IncrementalHasher createIncrementalHasher() throws HashingException {
        try {
            return new IncrementalHasherAdapter(incrementalHasherFactory.createIncrementalHasher());
        } catch (Exception e) {
            throw new HashingException("Failed to create incremental hasher", e);
        }
    }

    @Override
    public Blake3IncrementalHasher createKeyedIncrementalHasher(byte[] key) throws HashingException {
        Objects.requireNonNull(key, "Key cannot be null");
        if (key.length != 32) {
            throw new IllegalArgumentException("Key must be exactly 32 bytes, got " + key.length);
        }

        try {
            // Create a regular hasher and apply the key as the first update
            IncrementalHasherFactory.IncrementalHasher hasher = incrementalHasherFactory.createIncrementalHasher();
            hasher.update(key);
            return new IncrementalHasherAdapter(hasher);
        } catch (Exception e) {
            throw new HashingException("Failed to create keyed incremental hasher", e);
        }
    }

    @Override
    public CompletableFuture<List<String>> hashFilesParallel(List<Path> filePaths) {
        Objects.requireNonNull(filePaths, "File paths list cannot be null");
        if (filePaths.isEmpty()) {
            return CompletableFuture.completedFuture(new java.util.ArrayList<>());
        }

        // Check for null elements in the list
        for (int i = 0; i < filePaths.size(); i++) {
            if (filePaths.get(i) == null) {
                throw new IllegalArgumentException("File path at index " + i + " cannot be null");
            }
        }

        return CompletableFuture.supplyAsync(() ->
            filePaths.parallelStream()
                .map(path -> {
                    try {
                        return hashFile(path);
                    } catch (IOException | HashingException e) {
                        throw new RuntimeException("Failed to hash file: " + path, e);
                    }
                })
                .collect(Collectors.toList()),
            executor
        ).handle((result, throwable) -> {
            if (throwable != null) {
                // Unwrap the RuntimeException to get the original cause
                Throwable cause = throwable.getCause();
                if (cause instanceof RuntimeException && cause.getCause() != null) {
                    throw new RuntimeException(cause.getCause());
                }
                throw new RuntimeException(throwable);
            }
            return result;
        });
    }

    @Override
    public boolean verify(byte[] data, String expectedHash) throws HashingException {
        Objects.requireNonNull(data, "Data cannot be null");
        Objects.requireNonNull(expectedHash, "Expected hash cannot be null");

        // Validate hash format (should be 64 hex characters for BLAKE3)
        if (!expectedHash.matches("^[a-fA-F0-9]{64}$")) {
            throw new IllegalArgumentException("Invalid hash format: expected 64 hexadecimal characters");
        }

        String actualHash = hashBuffer(data);
        return actualHash.equalsIgnoreCase(expectedHash);
    }

    @Override
    public Blake3Info getInfo() {
        return blake3Info;
    }

    /**
     * Implementation of Blake3Info providing information about the BLAKE3 implementation.
     */
    private static class Blake3InfoImpl implements Blake3Info {
        /** SIMD detection service. */
        private final SimdDetectionService simdDetectionService;

        /** Creates a new Blake3InfoImpl. */
        Blake3InfoImpl(SimdDetectionService simdDetectionService) {
            this.simdDetectionService = simdDetectionService;
        }

        @Override
        public String getVersion() {
            // Pure Java implementation version
            return "1.0.0-pure-java";
        }

        @Override
        public boolean hasSimdSupport() {
            return simdDetectionService.getSimdInfo().hasSimdSupport();
        }

        @Override
        public String getSimdInstructionSet() {
            return simdDetectionService.getSimdInfo().getBestSimdInstructionSet();
        }

        @Override
        public boolean isJniImplementation() {
            return false; // We're using the pure Java implementation
        }

        @Override
        public boolean supportsConcurrentHashing() {
            return true; // Our implementation supports concurrent hashing
        }

        @Override
        public int getOptimalBufferSize() {
            return 65536; // 64KB optimal buffer size
        }

        @Override
        public int getMaxConcurrentThreads() {
            return Runtime.getRuntime().availableProcessors();
        }
    }
}