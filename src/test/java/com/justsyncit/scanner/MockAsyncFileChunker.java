package com.justsyncit.scanner;

import org.junit.jupiter.api.DisplayName;

import com.justsyncit.scanner.FileChunker.ChunkingResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mock implementation of AsyncFileChunker for testing.
 * Provides controllable behavior and state tracking for test scenarios.
 */
@DisplayName("Mock Async File Chunker")
public class MockAsyncFileChunker implements AsyncFileChunker {

    private final AtomicInteger chunkCount;
    private final AtomicInteger activeOperations;
    private final AtomicReference<AsyncByteBufferPool> bufferPoolRef;
    private final AtomicReference<AsyncChunkHandler> chunkHandlerRef;
    private volatile boolean closed;
    private volatile int maxConcurrentOperations;
    private final List<String> chunkHistory;

    public MockAsyncFileChunker() {
        this.chunkCount = new AtomicInteger(0);
        this.activeOperations = new AtomicInteger(0);
        this.bufferPoolRef = new AtomicReference<>();
        this.chunkHandlerRef = new AtomicReference<>();
        this.closed = false;
        this.maxConcurrentOperations = 4;
        this.chunkHistory = new ArrayList<>();
    }

    @Override
    public void chunkFileAsync(Path file, ChunkingOptions options,
            CompletionHandler<ChunkingResult, Exception> handler) {
        if (closed) {
            handler.failed(new IllegalStateException("Chunker is closed"));
            return;
        }

        activeOperations.incrementAndGet();

        // Simulate async chunking with configurable delay
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(10); // Simulate work
                ChunkingResult result = createMockChunkingResult(file);
                chunkCount.incrementAndGet();
                handler.completed(result);
            } catch (Exception e) {
                handler.failed(e);
            } finally {
                activeOperations.decrementAndGet();
            }
        }).exceptionally(ex -> {
            handler.failed(new RuntimeException("Chunking failed", ex));
            activeOperations.decrementAndGet();
            return null;
        });
    }

    @Override
    public CompletableFuture<ChunkingResult> chunkFileAsync(Path file, ChunkingOptions options) {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Chunker is closed"));
        }

        activeOperations.incrementAndGet();

        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(10); // Simulate work
                ChunkingResult result = createMockChunkingResult(file);
                chunkCount.incrementAndGet();
                return result;
            } catch (Exception e) {
                throw new RuntimeException("Chunking failed", e);
            } finally {
                activeOperations.decrementAndGet();
            }
        });
    }

    @Override
    public void setAsyncBufferPool(AsyncByteBufferPool asyncBufferPool) {
        bufferPoolRef.set(asyncBufferPool);
    }

    @Override
    public AsyncByteBufferPool getAsyncBufferPool() {
        return bufferPoolRef.get();
    }

    @Override
    public void setAsyncChunkHandler(AsyncChunkHandler asyncChunkHandler) {
        chunkHandlerRef.set(asyncChunkHandler);
    }

    @Override
    public AsyncChunkHandler getAsyncChunkHandler() {
        return chunkHandlerRef.get();
    }

    @Override
    public CompletableFuture<String> getStatsAsync() {
        return CompletableFuture.completedFuture(String.format(
                "MockAsyncFileChunker{chunkCount=%d, activeOperations=%d, closed=%b, maxConcurrent=%d}",
                chunkCount.get(), activeOperations.get(), closed, maxConcurrentOperations));
    }

    @Override
    public boolean supportsOverlappingIO() {
        return true;
    }

    @Override
    public boolean supportsBackpressure() {
        return true;
    }

    @Override
    public CompletableFuture<Void> closeAsync() {
        closed = true;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public int getActiveOperations() {
        return activeOperations.get();
    }

    @Override
    public int getMaxConcurrentOperations() {
        return maxConcurrentOperations;
    }

    @Override
    public void setMaxConcurrentOperations(int maxConcurrentOperations) {
        this.maxConcurrentOperations = maxConcurrentOperations;
    }

    // Legacy interface methods
    @Override
    public void setBufferPool(BufferPool bufferPool) {
        // Mock implementation - convert to async pool if needed
    }

    @Override
    public void setChunkSize(int chunkSize) {
        // Mock implementation
    }

    @Override
    public int getChunkSize() {
        return 65536; // Default mock size
    }

    @Override
    public String storeChunk(byte[] data) {
        String chunkId = "mock-chunk-" + data.hashCode();
        chunkHistory.add(chunkId);
        return chunkId;
    }

    @Override
    public byte[] retrieveChunk(String hash) {
        return new byte[0]; // Mock empty data
    }

    @Override
    public boolean existsChunk(String hash) {
        return chunkHistory.contains(hash);
    }

    @Override
    public CompletableFuture<ChunkingResult> chunkFile(Path file, ChunkingOptions options) {
        return chunkFileAsync(file, options);
    }

    // Test control methods
    public void reset() {
        chunkCount.set(0);
        activeOperations.set(0);
        chunkHistory.clear();
    }

    public int getChunkCount() {
        return chunkCount.get();
    }

    public List<String> getChunkHistory() {
        return new ArrayList<>(chunkHistory);
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    private ChunkingResult createMockChunkingResult(Path file) {
        List<String> chunkIds = List.of("mock-chunk-1", "mock-chunk-2");
        return new ChunkingResult(file, chunkIds.size(), 1024, 0, "mock-hash", chunkIds);
    }
}