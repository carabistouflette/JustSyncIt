package com.justsyncit.scanner;

import com.justsyncit.hash.Blake3Service;
import com.justsyncit.storage.ContentStore;
import com.justsyncit.storage.StorageIntegrityException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementation of FileChunker with fixed-size chunking and async I/O.
 * Uses AsynchronousFileChannel for optimal SSD/HDD performance.
 * Follows Single Responsibility Principle by focusing only on chunking
 * operations.
 */

public class FixedSizeFileChunker implements FileChunker {

    /** Logger for the chunker. */
    private static final Logger logger = LoggerFactory.getLogger(FixedSizeFileChunker.class);

    /** Default chunk size (64KB). */
    private static final int DEFAULT_CHUNK_SIZE = 64 * 1024;
    /** Default buffer count. */
    private static final int DEFAULT_BUFFER_COUNT = 4;

    /** BLAKE3 service for hash calculation. */
    private final Blake3Service blake3Service;
    /** Buffer pool for memory management. */
    private BufferPool bufferPool;
    /** Async buffer pool for memory management. */
    private AsyncByteBufferPool asyncBufferPool;
    /** Current chunk size. */
    private volatile int chunkSize;
    /** Executor service for async operations. */
    private final ExecutorService executorService;
    /** Whether the chunker has been closed. */
    private volatile boolean closed;
    /** Content store for storing chunks. */
    private ContentStore contentStore;
    /** Semaphore for controlling concurrent operations. */
    private final Semaphore operationSemaphore;
    /** Number of currently active operations. */
    private final AtomicInteger activeOperations;
    /** Maximum number of concurrent operations. */
    private volatile int maxConcurrentOperations;

    /**
     * Creates a new FixedSizeFileChunker with default settings.
     *
     * @param blake3Service BLAKE3 service for hash calculation
     * @return a new FixedSizeFileChunker with default settings
     * @throws IllegalArgumentException if blake3Service is null
     */
    public static FixedSizeFileChunker create(Blake3Service blake3Service) {
        return create(blake3Service, ByteBufferPool.create(), AsyncByteBufferPoolImpl.create(), DEFAULT_CHUNK_SIZE,
                null);
    }

    /**
     * Creates a new FixedSizeFileChunker with custom settings.
     *
     * @param blake3Service BLAKE3 service for hash calculation
     * @param bufferPool    buffer pool for memory management
     * @param chunkSize     chunk size in bytes
     * @return a new FixedSizeFileChunker with custom settings
     * @throws IllegalArgumentException if parameters are invalid
     */
    public static FixedSizeFileChunker create(Blake3Service blake3Service, BufferPool bufferPool, int chunkSize) {
        return create(blake3Service, bufferPool, AsyncByteBufferPoolImpl.create(), chunkSize, null);
    }

    /**
     * Creates a new FixedSizeFileChunker with custom settings and content store.
     *
     * @param blake3Service BLAKE3 service for hash calculation
     * @param bufferPool    buffer pool for memory management
     * @param chunkSize     chunk size in bytes
     * @param contentStore  content store for storing chunks
     * @return a new FixedSizeFileChunker with custom settings and content store
     * @throws IllegalArgumentException if parameters are invalid
     */
    public static FixedSizeFileChunker create(Blake3Service blake3Service, BufferPool bufferPool, int chunkSize,
            ContentStore contentStore) {
        return create(blake3Service, bufferPool, AsyncByteBufferPoolImpl.create(), chunkSize, contentStore);
    }

    /**
     * Creates a new FixedSizeFileChunker with custom settings and async buffer
     * pool.
     *
     * @param blake3Service   BLAKE3 service for hash calculation
     * @param bufferPool      buffer pool for memory management
     * @param asyncBufferPool async buffer pool for memory management
     * @param chunkSize       chunk size in bytes
     * @param contentStore    content store for storing chunks
     * @return a new FixedSizeFileChunker with custom settings and content store
     * @throws IllegalArgumentException if parameters are invalid
     */
    public static FixedSizeFileChunker create(Blake3Service blake3Service, BufferPool bufferPool,
            AsyncByteBufferPool asyncBufferPool, int chunkSize,
            ContentStore contentStore) {
        if (blake3Service == null) {
            throw new IllegalArgumentException("BLAKE3 service cannot be null");
        }
        if (bufferPool == null) {
            throw new IllegalArgumentException("Buffer pool cannot be null");
        }
        if (asyncBufferPool == null) {
            throw new IllegalArgumentException("Async buffer pool cannot be null");
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("Chunk size must be positive");
        }

        return new FixedSizeFileChunker(blake3Service, bufferPool, asyncBufferPool, chunkSize, contentStore);
    }

    /**
     * Creates a new FixedSizeFileChunker with default settings.
     *
     * @param blake3Service BLAKE3 service for hash calculation
     * @throws IllegalArgumentException if blake3Service is null
     * @deprecated Use {@link #create(Blake3Service)} instead
     */
    @Deprecated
    public FixedSizeFileChunker(Blake3Service blake3Service) {
        // No validation in constructor - use static factory method instead
        this.blake3Service = blake3Service;
        this.bufferPool = ByteBufferPool.create();
        this.asyncBufferPool = AsyncByteBufferPoolImpl.create();
        this.chunkSize = DEFAULT_CHUNK_SIZE;
        this.contentStore = null;
        this.maxConcurrentOperations = DEFAULT_BUFFER_COUNT;
        this.activeOperations = new AtomicInteger(0);
        this.executorService = Executors.newFixedThreadPool(DEFAULT_BUFFER_COUNT);
        this.operationSemaphore = new Semaphore(maxConcurrentOperations);
        this.closed = false;
    }

    /**
     * Creates a new FixedSizeFileChunker with custom settings.
     *
     * @param blake3Service BLAKE3 service for hash calculation
     * @param bufferPool    buffer pool for memory management
     * @param chunkSize     chunk size in bytes
     * @throws IllegalArgumentException if parameters are invalid
     * @deprecated Use {@link #create(Blake3Service, BufferPool, int)} instead
     */
    @Deprecated
    @SuppressWarnings("EI_EXPOSE_REP2")
    public FixedSizeFileChunker(Blake3Service blake3Service, BufferPool bufferPool, int chunkSize) {
        // No validation in constructor - use static factory method instead
        this.blake3Service = blake3Service;
        this.bufferPool = bufferPool;
        this.asyncBufferPool = AsyncByteBufferPoolImpl.create();
        this.chunkSize = chunkSize;
        this.contentStore = null;
        this.maxConcurrentOperations = DEFAULT_BUFFER_COUNT;
        this.activeOperations = new AtomicInteger(0);
        this.executorService = Executors.newFixedThreadPool(DEFAULT_BUFFER_COUNT);
        this.operationSemaphore = new Semaphore(maxConcurrentOperations);
        this.closed = false;
    }

    /**
     * Creates a new FixedSizeFileChunker with custom settings and content store.
     *
     * @param blake3Service BLAKE3 service for hash calculation
     * @param bufferPool    buffer pool for memory management
     * @param chunkSize     chunk size in bytes
     * @param contentStore  content store for storing chunks
     * @throws IllegalArgumentException if parameters are invalid
     * @deprecated Use {@link #create(Blake3Service, BufferPool, int, ContentStore)}
     *             instead
     */
    @Deprecated
    @SuppressWarnings("EI_EXPOSE_REP2")
    public FixedSizeFileChunker(Blake3Service blake3Service, BufferPool bufferPool, int chunkSize,
            ContentStore contentStore) {
        // No validation in constructor - use static factory method instead
        this.blake3Service = blake3Service;
        this.bufferPool = bufferPool;
        this.asyncBufferPool = AsyncByteBufferPoolImpl.create();
        this.chunkSize = chunkSize;
        this.contentStore = contentStore;
        this.maxConcurrentOperations = DEFAULT_BUFFER_COUNT;
        this.activeOperations = new AtomicInteger(0);
        this.executorService = Executors.newFixedThreadPool(DEFAULT_BUFFER_COUNT);
        this.operationSemaphore = new Semaphore(maxConcurrentOperations);
        this.closed = false;
    }

    /**
     * Creates a new FixedSizeFileChunker with specified settings.
     */
    private FixedSizeFileChunker(Blake3Service blake3Service, BufferPool bufferPool,
            AsyncByteBufferPool asyncBufferPool, int chunkSize,
            ContentStore contentStore) {
        this.blake3Service = blake3Service;
        this.bufferPool = bufferPool;
        this.asyncBufferPool = asyncBufferPool;
        this.chunkSize = chunkSize;
        this.contentStore = contentStore;
        this.maxConcurrentOperations = DEFAULT_BUFFER_COUNT;
        this.activeOperations = new AtomicInteger(0);
        this.executorService = Executors.newFixedThreadPool(DEFAULT_BUFFER_COUNT);
        this.operationSemaphore = new Semaphore(maxConcurrentOperations);
        this.closed = false;
    }

    @Override
    public CompletableFuture<ChunkingResult> chunkFile(Path file, ChunkingOptions options) {
        if (file == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("File cannot be null"));
        }
        if (closed) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Chunker has been closed"));
        }
        if (!Files.exists(file)) {
            return CompletableFuture.failedFuture(
                    new java.nio.file.NoSuchFileException(file.toString()));
        }
        if (!Files.isRegularFile(file)) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Path is not a regular file: " + file));
        }

        final ChunkingOptions finalOptions = options != null ? options : new ChunkingOptions();
        final int effectiveChunkSize = finalOptions.getChunkSize() > 0
                ? finalOptions.getChunkSize()
                : this.chunkSize;

        return performChunkingAsync(file, finalOptions, effectiveChunkSize);
    }

    @Override
    public String storeChunk(byte[] data) throws IOException {
        // This method is not implemented in FileChunker
        // Chunk storage should be handled by ContentStore
        throw new UnsupportedOperationException("storeChunk not implemented in FileChunker");
    }

    @Override
    public byte[] retrieveChunk(String hash) throws IOException, StorageIntegrityException {
        // This would integrate with ContentStore in a real implementation
        throw new UnsupportedOperationException("retrieveChunk not implemented in FileChunker");
    }

    @Override
    public boolean existsChunk(String hash) throws IOException {
        if (contentStore == null) {
            throw new UnsupportedOperationException("Content store not initialized");
        }
        return contentStore.existsChunk(hash);
    }

    @Override
    public void deleteChunk(String hash) throws IOException {
        throw new UnsupportedOperationException("Deletion not supported by chunker");
    }

    @Override
    @SuppressWarnings("EI_EXPOSE_REP2")
    public void setBufferPool(BufferPool bufferPool) {
        if (bufferPool == null) {
            throw new IllegalArgumentException("Buffer pool cannot be null");
        }
        // Note: BufferPool is an interface, we store the reference directly
        // as these are service objects that are meant to be used directly
        this.bufferPool = bufferPool;
        logger.debug("Updated buffer pool to {}", bufferPool.getClass().getSimpleName());
    }

    /**
     * Sets the async buffer pool for memory management.
     * If no pool is set, a default async pool will be used.
     *
     * @param asyncBufferPool the async buffer pool to use
     * @throws IllegalArgumentException if asyncBufferPool is null
     */
    public void setAsyncBufferPool(AsyncByteBufferPool asyncBufferPool) {
        if (asyncBufferPool == null) {
            throw new IllegalArgumentException("Async buffer pool cannot be null");
        }
        this.asyncBufferPool = asyncBufferPool;
        logger.debug("Updated async buffer pool to {}", asyncBufferPool.getClass().getSimpleName());
    }

    /**
     * Gets the current async buffer pool.
     *
     * @return the current async buffer pool
     */
    public AsyncByteBufferPool getAsyncBufferPool() {
        return asyncBufferPool;
    }

    /**
     * Gets the current number of active operations.
     *
     * @return the number of active operations
     */
    public int getActiveOperations() {
        return activeOperations.get();
    }

    /**
     * Gets the maximum number of concurrent operations allowed.
     *
     * @return the maximum number of concurrent operations
     */
    public int getMaxConcurrentOperations() {
        return maxConcurrentOperations;
    }

    /**
     * Sets the maximum number of concurrent operations allowed.
     *
     * @param maxConcurrentOperations the maximum number of concurrent operations
     * @throws IllegalArgumentException if maxConcurrentOperations is not positive
     */
    public void setMaxConcurrentOperations(int maxConcurrentOperations) {
        if (maxConcurrentOperations <= 0) {
            throw new IllegalArgumentException("Max concurrent operations must be positive");
        }
        this.maxConcurrentOperations = maxConcurrentOperations;
        // Update semaphore permits
        operationSemaphore.drainPermits();
        operationSemaphore.release(maxConcurrentOperations);
        logger.debug("Updated max concurrent operations to {}", maxConcurrentOperations);
    }

    /**
     * Checks if the chunker has been closed.
     *
     * @return true if closed, false otherwise
     */
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void setChunkSize(int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("Chunk size must be positive");
        }
        this.chunkSize = chunkSize;
        logger.debug("Updated chunk size to {}", chunkSize);
    }

    @Override
    public int getChunkSize() {
        return chunkSize;
    }

    /**
     * Performs the actual file chunking operation asynchronously.
     */
    private CompletableFuture<FileChunker.ChunkingResult> performChunkingAsync(Path file, ChunkingOptions options,
            int effectiveChunkSize) {
        CompletableFuture<FileChunker.ChunkingResult> resultFuture = new CompletableFuture<>();

        try {
            long fileSize = Files.size(file);

            // Handle empty file case
            if (fileSize == 0) {
                try {
                    String fileHash = blake3Service.hashBuffer(new byte[0]);
                    resultFuture.complete(new FileChunker.ChunkingResult(file, 0, 0, 0, fileHash, new ArrayList<>()));
                } catch (Exception e) {
                    resultFuture.complete(FileChunker.ChunkingResult.createFailed(file, e));
                }
                return resultFuture;
            }

            // Calculate number of chunks
            int chunkCount = (int) Math.ceil((double) fileSize / effectiveChunkSize);
            List<String> chunkHashes = new ArrayList<>(chunkCount);

            logger.debug("Chunking file {} ({} bytes) into {} chunks of {} bytes each",
                    file, fileSize, chunkCount, effectiveChunkSize);

            // For small files (less than 1MB), use sync I/O to avoid channel closure issues
            // Async I/O provides no benefit for small files and causes channel closure
            // problems
            boolean useAsyncIO = options.isUseAsyncIO() && fileSize >= 1024 * 1024;

            if (useAsyncIO) {
                return performAsyncChunking(file, options, effectiveChunkSize, fileSize, chunkCount, chunkHashes);
            } else {
                return performSyncChunking(file, options, effectiveChunkSize, fileSize, chunkCount, chunkHashes);
            }
        } catch (IOException e) {
            resultFuture.complete(FileChunker.ChunkingResult.createFailed(file, e));
        }

        return resultFuture;
    }

    /**
     * Performs chunking using true asynchronous I/O with CompletionHandler pattern.
     */
    private CompletableFuture<FileChunker.ChunkingResult> performAsyncChunking(Path file, ChunkingOptions options,
            int chunkSize,
            long fileSize, int chunkCount, List<String> chunkHashes) {

        // Update max concurrent operations if specified in options
        if (options.getMaxConcurrentChunks() > 0) {
            this.maxConcurrentOperations = options.getMaxConcurrentChunks();
        }

        CompletableFuture<FileChunker.ChunkingResult> resultFuture = new CompletableFuture<>();

        try {
            AsynchronousFileChannel channel = AsynchronousFileChannel.open(file, StandardOpenOption.READ);

            // Create incremental hasher for single-pass file hashing
            com.justsyncit.hash.IncrementalHasherFactory hasherFactory = new com.justsyncit.hash.Blake3IncrementalHasherFactory(
                    com.justsyncit.hash.Sha256HashAlgorithm.create());
            com.justsyncit.hash.IncrementalHasherFactory.IncrementalHasher fileHasher = hasherFactory
                    .createIncrementalHasher();

            // Process chunks and hash file in a single pass
            processAllChunksAsync(channel, file, chunkSize, fileSize, chunkCount, chunkHashes, options, fileHasher)
                    .thenApply(fileHash -> new FileChunker.ChunkingResult(file, chunkCount, fileSize, 0, fileHash,
                            chunkHashes))
                    .whenComplete((result, throwable) -> {
                        // Close resources
                        closeChannelAsync(channel);
                        fileHasher.close();

                        if (throwable != null) {
                            resultFuture.completeExceptionally(throwable);
                        } else {
                            resultFuture.complete(result);
                        }
                    });

        } catch (Exception e) {
            resultFuture.complete(FileChunker.ChunkingResult.createFailed(file, e));
        }

        return resultFuture;
    }

    /**
     * Performs chunking using synchronous I/O.
     */
    private CompletableFuture<FileChunker.ChunkingResult> performSyncChunking(Path file, ChunkingOptions options,
            int chunkSize,
            long fileSize, int chunkCount, List<String> chunkHashes) {
        return CompletableFuture.supplyAsync(() -> {
            AsynchronousFileChannel channel = null;
            com.justsyncit.hash.IncrementalHasherFactory.IncrementalHasher fileHasher = null;
            try {
                channel = AsynchronousFileChannel.open(file, StandardOpenOption.READ);

                // Initialize incremental hasher for file hash
                com.justsyncit.hash.IncrementalHasherFactory hasherFactory = new com.justsyncit.hash.Blake3IncrementalHasherFactory(
                        com.justsyncit.hash.Sha256HashAlgorithm.create());
                fileHasher = hasherFactory.createIncrementalHasher();

                // Process chunks sequentially
                for (int i = 0; i < chunkCount; i++) {
                    long offset = (long) i * chunkSize;
                    int length = (int) Math.min(chunkSize, fileSize - offset);

                    FileChunker.ChunkStatusCallback statusCallback = options.getStatusCallback();
                    if (statusCallback != null) {
                        statusCallback.onStatus("Hashing chunk " + (i + 1));
                    }

                    // Process chunk and update file hasher
                    String chunkHash = processChunkSync(channel, offset, length, fileHasher);
                    chunkHashes.add(chunkHash);

                    FileChunker.ChunkProgressCallback progressCallback = options.getProgressCallback();
                    if (progressCallback != null) {
                        progressCallback.onProgress(length);
                    }
                }

                FileChunker.ChunkStatusCallback statusCallback = options.getStatusCallback();
                if (statusCallback != null) {
                    statusCallback.onStatus("Finalizing");
                }

                String fileHash = fileHasher.digest();
                return new FileChunker.ChunkingResult(file, chunkCount, fileSize, 0, fileHash, chunkHashes);
            } catch (Exception e) {
                return FileChunker.ChunkingResult.createFailed(file, e);
            } finally {
                if (fileHasher != null) {
                    try {
                        fileHasher.close();
                    } catch (Exception e) {
                        logger.warn("Failed to close file hasher: {}", e.getMessage());
                    }
                }
                if (channel != null) {
                    try {
                        channel.close();
                    } catch (IOException e) {
                        logger.warn("Failed to close file channel: {}", e.getMessage());
                    }
                }
            }
        }, executorService);
    }

    /**
     * Processes all chunks asynchronously using true async I/O with bounded
     * submission.
     * Returns a Future that completes with the full file hash.
     */
    private CompletableFuture<String> processAllChunksAsync(AsynchronousFileChannel channel, Path file, int chunkSize,
            long fileSize, int chunkCount, List<String> chunkHashes, ChunkingOptions options,
            com.justsyncit.hash.IncrementalHasherFactory.IncrementalHasher fileHasher) {

        CompletableFuture<String> result = new CompletableFuture<>();

        // Context object to hold the current state of the hashing chain
        // Array to allow update from lambda
        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] hashingChain = (CompletableFuture<Void>[]) new CompletableFuture[1];
        hashingChain[0] = CompletableFuture.completedFuture(null);

        // Start the bounded submission loop
        submitNextChunk(0, channel, file, chunkSize, fileSize, chunkCount, chunkHashes, options, fileHasher,
                hashingChain, result);

        return result;
    }

    /**
     * Submits chunks recursively but breaks recursion when waiting for resources.
     * This ensures we only have as many in-flight futures as the buffer pool
     * allows.
     */
    private void submitNextChunk(int startIndex,
            AsynchronousFileChannel channel, Path file, int chunkSize,
            long fileSize, int chunkCount, List<String> chunkHashes, ChunkingOptions options,
            com.justsyncit.hash.IncrementalHasherFactory.IncrementalHasher fileHasher,
            CompletableFuture<Void>[] hashingChain,
            CompletableFuture<String> finalResult) {

        // Loop to process chunks as long as resources are immediately available
        int i = startIndex;
        while (i < chunkCount) {
            // Check if we should stop
            if (finalResult.isDone())
                return;

            final int chunkIndex = i;
            long offset = (long) i * chunkSize;
            int length = (int) Math.min(chunkSize, fileSize - offset);

            // Acquire buffer - this is our throttle
            CompletableFuture<ByteBuffer> bufferFuture = asyncBufferPool.acquireAsync(length);

            if (bufferFuture.isDone()) {
                // Fast path: Resource available immediately.
                // Process this chunk and continue loop without recursion
                try {
                    ByteBuffer buffer = bufferFuture.join();
                    processSingleChunk(buffer, chunkIndex, offset, length, channel, options, chunkHashes, fileHasher,
                            hashingChain, finalResult);
                    i++;
                } catch (Exception e) {
                    finalResult.completeExceptionally(e);
                    return;
                }
            } else {
                // Slow path: Resource not available.
                // Wait for it, then resume loop from next index (i+1)
                final int nextIndex = i + 1;
                bufferFuture.whenComplete((buffer, t) -> {
                    if (t != null) {
                        finalResult.completeExceptionally(t);
                    } else {
                        try {
                            processSingleChunk(buffer, chunkIndex, offset, length, channel, options, chunkHashes,
                                    fileHasher, hashingChain, finalResult);
                            // Resume loop
                            submitNextChunk(nextIndex, channel, file, chunkSize, fileSize, chunkCount, chunkHashes,
                                    options, fileHasher, hashingChain, finalResult);
                        } catch (Exception e) {
                            finalResult.completeExceptionally(e);
                        }
                    }
                });
                return; // Break current stack/loop
            }
        }

        // Loop finished (all chunks submitted)
        // Set up final completion when the last hash operation finishes
        hashingChain[0].whenComplete((v, t) -> {
            if (t != null) {
                finalResult.completeExceptionally(t);
            } else {
                try {
                    logger.debug("Completed processing {} chunks for file {}", chunkCount, file);
                    finalResult.complete(fileHasher.digest());
                } catch (Exception e) {
                    finalResult.completeExceptionally(e);
                }
            }
        });
    }

    private void processSingleChunk(ByteBuffer buffer, int chunkIndex, long offset, int length,
            AsynchronousFileChannel channel, ChunkingOptions options,
            List<String> chunkHashes,
            com.justsyncit.hash.IncrementalHasherFactory.IncrementalHasher fileHasher,
            CompletableFuture<Void>[] hashingChain,
            CompletableFuture<String> finalResult) {

        // Prepare futures for this chunk
        CompletableFuture<Void> previousHash = hashingChain[0];
        CompletableFuture<Void> currentHash = new CompletableFuture<>();
        hashingChain[0] = currentHash; // Update chain head

        // Initiate Async Read
        channel.read(buffer, offset, null, new CompletionHandler<Integer, Void>() {
            @Override
            public void completed(Integer bytesRead, Void attachment) {
                try {
                    if (bytesRead == -1) {
                        asyncBufferPool.releaseAsync(buffer);
                        currentHash.completeExceptionally(
                                new java.io.IOException("Unexpected EOF at chunk " + chunkIndex));
                        // Don't fail the whole file immediately if we can just stop?
                        // But EOF here is an error for fixed size chunking logic unless it's the last
                        // chunk,
                        // but we calculated chunk sizes based on file size.
                        finalResult.completeExceptionally(
                                new java.io.IOException("Unexpected EOF at chunk " + chunkIndex));
                        return;
                    }

                    buffer.flip();
                    byte[] chunkData = new byte[buffer.remaining()];
                    buffer.get(chunkData);

                    // Report status
                    FileChunker.ChunkStatusCallback statusCallback = options.getStatusCallback();
                    if (statusCallback != null) {
                        statusCallback.onStatus("Hashing chunk " + (chunkIndex + 1));
                    }

                    // CPU bound work: Hash Chunk
                    String hash = blake3Service.hashBuffer(chunkData);

                    // Add to list (Need sync)
                    synchronized (chunkHashes) {
                        chunkHashes.add(hash);
                    }

                    // Report progress
                    FileChunker.ChunkProgressCallback progressCallback = options.getProgressCallback();
                    if (progressCallback != null) {
                        progressCallback.onProgress(bytesRead);
                    }
                    activeOperations.incrementAndGet(); // Stats tracking if needed, though we didn't use it for
                                                        // throttling

                    // Schedule Hasher Update (Strictly Ordered)
                    previousHash.whenComplete((v, t) -> {
                        try {
                            if (t == null) {
                                fileHasher.update(chunkData);
                                currentHash.complete(null);
                            } else {
                                currentHash.completeExceptionally(t);
                            }
                        } catch (Exception e) {
                            currentHash.completeExceptionally(e);
                        } finally {
                            asyncBufferPool.releaseAsync(buffer);
                            activeOperations.decrementAndGet();
                        }
                    });

                } catch (Exception e) {
                    asyncBufferPool.releaseAsync(buffer);
                    currentHash.completeExceptionally(e);
                    finalResult.completeExceptionally(e);
                }
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                asyncBufferPool.releaseAsync(buffer);
                currentHash.completeExceptionally(exc);
                finalResult.completeExceptionally(exc);
            }
        });
    }

    /**
     * Processes a single chunk synchronously.
     */
    private String processChunkSync(AsynchronousFileChannel channel, long offset, int length,
            com.justsyncit.hash.IncrementalHasherFactory.IncrementalHasher fileHasher) {
        ByteBuffer buffer = bufferPool.acquire(length);
        logger.trace("Acquired buffer for chunk at offset {}", offset);
        try {
            // Read chunk data
            logger.trace("Reading from channel at offset {}", offset);
            channel.read(buffer, offset).get();
            logger.trace("Read complete");
            buffer.flip();

            // Calculate hash
            byte[] chunkData = new byte[buffer.remaining()];
            buffer.get(chunkData);

            logger.trace("Hashing buffer of size {}", chunkData.length);
            String hash = blake3Service.hashBuffer(chunkData);
            logger.trace("Hash complete: {}", hash);

            // Update file hasher if provided
            if (fileHasher != null) {
                fileHasher.update(chunkData);
            }

            // Store chunk if content store is available
            if (contentStore != null) {
                logger.trace("Storing chunk in content store");
                // Just try to store the chunk - content store should handle deduplication
                // This avoids the extra existsChunk check which can cause database contention
                contentStore.storeChunk(chunkData);
                logger.trace("Store complete");
                logger.debug("Stored chunk {} ({} bytes)", hash, chunkData.length);
            }

            return hash;
        } catch (java.lang.InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new java.util.concurrent.CompletionException("Interrupted while processing chunk", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new java.util.concurrent.CompletionException("Failed to read chunk", e.getCause());
        } catch (com.justsyncit.hash.HashingException e) {
            throw new java.util.concurrent.CompletionException("Failed to hash chunk", e);
        } catch (IOException e) {
            throw new java.util.concurrent.CompletionException("Failed to store chunk", e);
        } catch (RuntimeException e) {
            logger.error("Error processing chunk at offset {} length {}", offset, length, e);
            throw new java.util.concurrent.CompletionException("Failed to process chunk", e);
        } finally {
            bufferPool.release(buffer);
        }
    }

    /**
     * Closes a file channel asynchronously.
     */
    private void closeChannelAsync(AsynchronousFileChannel channel) {
        if (channel != null) {
            CompletableFuture.runAsync(() -> {
                try {
                    channel.close();
                } catch (IOException e) {
                    logger.warn("Failed to close file channel: {}", e.getMessage());
                }
            }, executorService);
        }
    }

    @SuppressWarnings("EI_EXPOSE_REP2")
    public void setContentStore(ContentStore contentStore) {
        this.contentStore = contentStore;
        logger.debug("Updated content store");
    }

    /**
     * Closes the chunker and releases resources.
     */
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        bufferPool.clear();
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(800, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
}