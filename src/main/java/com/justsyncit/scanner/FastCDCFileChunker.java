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

import com.justsyncit.hash.Blake3Service;
import com.justsyncit.scanner.fastcdc.FastCDC;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Implementation of FileChunker using FastCDC (Content-Defined Chunking).
 * Uses a sliding window approach with Gear hashing to determine chunk
 * boundaries.
 * Optimized with asynchronous I/O and buffered reading.
 */
public class FastCDCFileChunker implements FileChunker {

    private static final Logger logger = LoggerFactory.getLogger(FastCDCFileChunker.class);

    // Read buffer size (1MB - large enough to hold several max-size chunks)
    private static final int READ_BUFFER_SIZE = 1024 * 1024;

    private final Blake3Service blake3Service;
    private final ExecutorService executorService;
    private BufferPool bufferPool;
    private ChunkingOptions defaultOptions;

    private volatile boolean closed;

    public static FastCDCFileChunker create(Blake3Service blake3Service) {
        return new FastCDCFileChunker(blake3Service);
    }

    private FastCDCFileChunker(Blake3Service blake3Service) {
        this.blake3Service = blake3Service;
        this.executorService = Executors.newFixedThreadPool(4); // Worker pool
        this.bufferPool = ByteBufferPool.create(); // Default pool
        this.defaultOptions = new ChunkingOptions();
        this.closed = false;
    }

    @Override
    public CompletableFuture<ChunkingResult> chunkFile(Path file, ChunkingOptions options) {
        if (file == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("File cannot be null"));
        }
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Chunker has been closed"));
        }

        ChunkingOptions effectiveOptions = options != null ? options : defaultOptions;

        // Ensure algorithm matches
        if (effectiveOptions.getAlgorithm() == ChunkingOptions.ChunkingAlgorithm.FIXED) {
            logger.warn("FastCDCFileChunker called with FIXED algorithm option. Ignoring and using CDC.");
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                return doChunkFile(file, effectiveOptions);
            } catch (Exception e) {
                logger.error("Error during CDC chunking of {}", file, e);
                return ChunkingResult.createFailed(file, e);
            }
        }, executorService);
    }

    private ChunkingResult doChunkFile(Path file, ChunkingOptions options) throws IOException {
        long fileSize = Files.size(file);

        // Setup FastCDC
        int minSize = options.getMinChunkSize();
        int avgSize = options.getChunkSize(); // In ChunkingOptions, 'chunkSize' is avg for CDC
        int maxSize = options.getMaxChunkSize();

        FastCDC fastCDC = new FastCDC(minSize, avgSize, maxSize);

        List<String> chunkHashes = new ArrayList<>();
        String fileHash = null; // We can compute this if we track full file digest

        // Full file hasher
        com.justsyncit.hash.Blake3Service.Blake3IncrementalHasher fileHasher = blake3Service.createIncrementalHasher();

        try (AsynchronousFileChannel channel = AsynchronousFileChannel.open(file, StandardOpenOption.READ)) {

            ByteBuffer buffer = bufferPool.acquire(READ_BUFFER_SIZE);
            long filePosition = 0;
            int chunksCount = 0;

            try {
                // Initial read
                buffer.clear();
                long readPos = 0;

                while (readPos < fileSize) {
                    // Read into buffer (compacted state assumed)
                    // If buffer is empty (position=0), read full.
                    // If buffer has residue, position is at end of residue.

                    int bytesToRead = buffer.remaining();
                    if (bytesToRead > 0) {
                        try {
                            Integer read = channel.read(buffer, readPos).get();
                            if (read == -1) {
                                // EOF reached unexpectedly? Should be covered by readPos check
                                break;
                            }
                            readPos += read;
                        } catch (Exception e) {
                            throw new IOException("Failed to read file", e);
                        }
                    }

                    buffer.flip(); // Prepare for reading

                    // Process chunks in buffer
                    while (buffer.hasRemaining()) {
                        int offset = buffer.position();
                        int available = buffer.remaining();

                        // Check if we have enough data to determine a chunk
                        // We need at least maxSize to define a chunk definitely,
                        // OR if we are at EOF, we take whatever is left.
                        boolean atEOF = (readPos == fileSize);

                        int chunkLen = fastCDC.nextChunk(buffer, offset, available);

                        boolean isFullChunk = false;
                        if (chunkLen == available) {
                            // Consumed all available data
                            if (chunkLen < maxSize && !atEOF) {
                                // Needed more data but hit buffer end.
                                // This is a partial chunk (residue).
                                isFullChunk = false;
                            } else {
                                // Valid chunk (either forced by maxSize, or EOF)
                                isFullChunk = true;
                            }
                        } else {
                            // Found a cut point before end of buffer
                            isFullChunk = true;
                        }

                        if (isFullChunk) {
                            // Process valid chunk
                            byte[] chunkData = new byte[chunkLen];
                            buffer.get(chunkData); // Advances position

                            // Hash chunk
                            String chunkHash = blake3Service.hashBuffer(chunkData);
                            chunkHashes.add(chunkHash);
                            chunksCount++;

                            // Update file hasher
                            fileHasher.update(chunkData);

                            // Callbacks
                            if (options.getProgressCallback() != null) {
                                options.getProgressCallback().onProgress(chunkLen);
                            }
                        } else {
                            // Not a full chunk, compact and break to read more
                            // 'buffer' position is still at start of residue
                            break;
                        }
                    }

                    // Compact buffer for next read
                    // Moves remaining bytes to start, sets position to end of data, limit to
                    // capacity
                    buffer.compact();
                }

                // Finalize file hash
                fileHash = fileHasher.digest();

            } finally {
                bufferPool.release(buffer);
            }

            // Compute MinHash signature from chunk hashes
            // We use the chunk hashes as features
            com.justsyncit.dedup.similarity.MinHash minHash = new com.justsyncit.dedup.similarity.MinHash();
            java.util.Set<Integer> usageFeatures = new java.util.HashSet<>();
            for (String h : chunkHashes) {
                usageFeatures.add(com.justsyncit.dedup.similarity.MinHash.hashString(h));
            }
            long[] signature = minHash.computeSignature(usageFeatures);

            // Record metrics
            com.justsyncit.dedup.dashboard.DedupMetricsService.getInstance()
                    .recordChunkProcessing(fileSize, false); // Marking as unique/unknown for now for the file

            return new ChunkingResult(file, chunksCount, fileSize, 0, fileHash, chunkHashes, signature);
        }
    }

    @Override
    public void setBufferPool(BufferPool bufferPool) {
        this.bufferPool = bufferPool;
    }

    @Override
    public void setChunkSize(int chunkSize) {
        defaultOptions.withChunkSize(chunkSize);
    }

    @Override
    public int getChunkSize() {
        return defaultOptions.getChunkSize();
    }

    // Unimplemented methods from interface
    @Override
    public String storeChunk(byte[] data) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte[] retrieveChunk(String hash) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean existsChunk(String hash) throws IOException {
        throw new UnsupportedOperationException();
    }

    public void close() {
        this.closed = true;
        this.executorService.shutdown();
    }
}
