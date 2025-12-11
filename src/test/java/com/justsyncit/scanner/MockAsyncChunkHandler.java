package com.justsyncit.scanner;

import org.junit.jupiter.api.DisplayName;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mock implementation of AsyncChunkHandler for testing.
 * Provides controllable behavior and state tracking for test scenarios.
 */
@DisplayName("Mock Async Chunk Handler")
public class MockAsyncChunkHandler implements AsyncChunkHandler {

    private final AtomicInteger chunksHandled;
    private final AtomicInteger activeOperations;
    private final AtomicLong totalBytesProcessed;
    private final AtomicLong totalProcessingTime;
    private final List<ChunkInfo> chunkHistory;
    private volatile boolean closed;
    private volatile int maxConcurrentChunks;
    private volatile boolean simulateFailure;
    private volatile long simulatedDelayMs;

    public MockAsyncChunkHandler() {
        this.chunksHandled = new AtomicInteger(0);
        this.activeOperations = new AtomicInteger(0);
        this.totalBytesProcessed = new AtomicLong(0);
        this.totalProcessingTime = new AtomicLong(0);
        this.chunkHistory = new ArrayList<>();
        this.closed = false;
        this.maxConcurrentChunks = 10;
        this.simulateFailure = false;
        this.simulatedDelayMs = 0;
    }

    @Override
    public CompletableFuture<String> processChunkAsync(ByteBuffer chunkData, int chunkIndex, int totalChunks, Path file) {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Handler is closed"));
        }

        if (simulateFailure) {
            return CompletableFuture.failedFuture(new RuntimeException("Simulated chunk processing failure"));
        }

        activeOperations.incrementAndGet();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (simulatedDelayMs > 0) {
                    Thread.sleep(simulatedDelayMs);
                }
                
                long startTime = System.nanoTime();
                
                // Simulate chunk processing
                int chunkSize = chunkData.remaining();
                totalBytesProcessed.addAndGet(chunkSize);
                
                // Generate mock hash
                String hash = "mock-hash-" + chunkIndex + "-" + System.currentTimeMillis();
                
                long processingTime = System.nanoTime() - startTime;
                totalProcessingTime.addAndGet(processingTime);
                
                // Record chunk info
                ChunkInfo chunkInfo = new ChunkInfo(
                    file,
                    chunkIndex,
                    totalChunks,
                    chunkSize,
                    hash,
                    System.currentTimeMillis()
                );
                chunkHistory.add(chunkInfo);
                
                chunksHandled.incrementAndGet();
                return hash;
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Chunk processing interrupted", e);
            } catch (Exception e) {
                throw new RuntimeException("Chunk processing failed", e);
            } finally {
                activeOperations.decrementAndGet();
            }
        });
    }

    @Override
    public void processChunkAsync(ByteBuffer chunkData, int chunkIndex, int totalChunks, Path file,
                              CompletionHandler<String, Exception> handler) {
        if (closed) {
            handler.failed(new IllegalStateException("Handler is closed"));
            return;
        }

        if (simulateFailure) {
            handler.failed(new RuntimeException("Simulated chunk processing failure"));
            return;
        }

        activeOperations.incrementAndGet();
        
        CompletableFuture.runAsync(() -> {
            try {
                if (simulatedDelayMs > 0) {
                    Thread.sleep(simulatedDelayMs);
                }
                
                long startTime = System.nanoTime();
                
                // Simulate chunk processing
                int chunkSize = chunkData.remaining();
                totalBytesProcessed.addAndGet(chunkSize);
                
                // Generate mock hash
                String hash = "mock-hash-" + chunkIndex + "-" + System.currentTimeMillis();
                
                long processingTime = System.nanoTime() - startTime;
                totalProcessingTime.addAndGet(processingTime);
                
                // Record chunk info
                ChunkInfo chunkInfo = new ChunkInfo(
                    file,
                    chunkIndex,
                    totalChunks,
                    chunkSize,
                    hash,
                    System.currentTimeMillis()
                );
                chunkHistory.add(chunkInfo);
                
                chunksHandled.incrementAndGet();
                handler.completed(hash);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                handler.failed(new RuntimeException("Chunk processing interrupted", e));
            } catch (Exception e) {
                handler.failed(new RuntimeException("Chunk processing failed", e));
            } finally {
                activeOperations.decrementAndGet();
            }
        });
    }

    @Override
    public CompletableFuture<String[]> processChunksAsync(ByteBuffer[] chunks, Path file) {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Handler is closed"));
        }

        if (simulateFailure) {
            return CompletableFuture.failedFuture(new RuntimeException("Simulated chunks processing failure"));
        }

        activeOperations.incrementAndGet();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (simulatedDelayMs > 0) {
                    Thread.sleep(simulatedDelayMs);
                }
                
                long startTime = System.nanoTime();
                String[] results = new String[chunks.length];
                
                for (int i = 0; i < chunks.length; i++) {
                    ByteBuffer chunk = chunks[i];
                    int chunkSize = chunk.remaining();
                    totalBytesProcessed.addAndGet(chunkSize);
                    
                    // Generate mock hash
                    String hash = "mock-hash-" + i + "-" + System.currentTimeMillis();
                    results[i] = hash;
                    
                    // Record chunk info
                    ChunkInfo chunkInfo = new ChunkInfo(
                        file,
                        i,
                        chunks.length,
                        chunkSize,
                        hash,
                        System.currentTimeMillis()
                    );
                    chunkHistory.add(chunkInfo);
                }
                
                long processingTime = System.nanoTime() - startTime;
                totalProcessingTime.addAndGet(processingTime);
                chunksHandled.addAndGet(chunks.length);
                
                return results;
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Chunks processing interrupted", e);
            } catch (Exception e) {
                throw new RuntimeException("Chunks processing failed", e);
            } finally {
                activeOperations.decrementAndGet();
            }
        });
    }

    @Override
    public void processChunksAsync(ByteBuffer[] chunks, Path file,
                              CompletionHandler<String[], Exception> handler) {
        if (closed) {
            handler.failed(new IllegalStateException("Handler is closed"));
            return;
        }

        if (simulateFailure) {
            handler.failed(new RuntimeException("Simulated chunks processing failure"));
            return;
        }

        activeOperations.incrementAndGet();
        
        CompletableFuture.runAsync(() -> {
            try {
                if (simulatedDelayMs > 0) {
                    Thread.sleep(simulatedDelayMs);
                }
                
                long startTime = System.nanoTime();
                String[] results = new String[chunks.length];
                
                for (int i = 0; i < chunks.length; i++) {
                    ByteBuffer chunk = chunks[i];
                    int chunkSize = chunk.remaining();
                    totalBytesProcessed.addAndGet(chunkSize);
                    
                    // Generate mock hash
                    String hash = "mock-hash-" + i + "-" + System.currentTimeMillis();
                    results[i] = hash;
                    
                    // Record chunk info
                    ChunkInfo chunkInfo = new ChunkInfo(
                        file,
                        i,
                        chunks.length,
                        chunkSize,
                        hash,
                        System.currentTimeMillis()
                    );
                    chunkHistory.add(chunkInfo);
                }
                
                long processingTime = System.nanoTime() - startTime;
                totalProcessingTime.addAndGet(processingTime);
                chunksHandled.addAndGet(chunks.length);
                
                handler.completed(results);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                handler.failed(new RuntimeException("Chunks processing interrupted", e));
            } catch (Exception e) {
                handler.failed(new RuntimeException("Chunks processing failed", e));
            } finally {
                activeOperations.decrementAndGet();
            }
        });
    }

    @Override
    public int getMaxConcurrentChunks() {
        return maxConcurrentChunks;
    }

    @Override
    public void setMaxConcurrentChunks(int maxConcurrentChunks) {
        if (maxConcurrentChunks <= 0) {
            throw new IllegalArgumentException("Max concurrent chunks must be positive");
        }
        this.maxConcurrentChunks = maxConcurrentChunks;
    }

    // Test control methods
    public void reset() {
        chunksHandled.set(0);
        activeOperations.set(0);
        totalBytesProcessed.set(0);
        totalProcessingTime.set(0);
        chunkHistory.clear();
        closed = false;
        simulateFailure = false;
        simulatedDelayMs = 0;
    }

    public int getChunksHandled() {
        return chunksHandled.get();
    }

    public long getTotalBytesProcessed() {
        return totalBytesProcessed.get();
    }

    public long getTotalProcessingTime() {
        return totalProcessingTime.get();
    }

    public List<ChunkInfo> getChunkHistory() {
        return new ArrayList<>(chunkHistory);
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    public void setSimulateFailure(boolean simulateFailure) {
        this.simulateFailure = simulateFailure;
    }

    public void setSimulatedDelayMs(long simulatedDelayMs) {
        this.simulatedDelayMs = simulatedDelayMs;
    }

    /**
     * Gets average processing time per chunk in nanoseconds.
     */
    public double getAverageProcessingTime() {
        int chunks = chunksHandled.get();
        return chunks > 0 ? (double) totalProcessingTime.get() / chunks : 0.0;
    }

    /**
     * Gets throughput in bytes per second.
     */
    public double getThroughput() {
        long totalTime = totalProcessingTime.get();
        return totalTime > 0 ? (double) totalBytesProcessed.get() * 1_000_000_000.0 / totalTime : 0.0;
    }

    /**
     * Information about a processed chunk.
     */
    public static class ChunkInfo {
        private final Path file;
        private final int chunkIndex;
        private final int totalChunks;
        private final int size;
        private final String hash;
        private final long timestamp;

        public ChunkInfo(Path file, int chunkIndex, int totalChunks, int size, String hash, long timestamp) {
            this.file = file;
            this.chunkIndex = chunkIndex;
            this.totalChunks = totalChunks;
            this.size = size;
            this.hash = hash;
            this.timestamp = timestamp;
        }

        public Path getFile() {
            return file;
        }

        public int getChunkIndex() {
            return chunkIndex;
        }

        public int getTotalChunks() {
            return totalChunks;
        }

        public int getSize() {
            return size;
        }

        public String getHash() {
            return hash;
        }

        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return String.format("ChunkInfo{file=%s, index=%d/%d, size=%d, hash=%s, timestamp=%d}",
                    file, chunkIndex, totalChunks, size, hash, timestamp);
        }
    }
}