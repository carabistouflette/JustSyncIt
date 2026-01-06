package com.justsyncit.scanner;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

/**
 * Mock implementation of AsyncByteBufferPool for testing.
 * Provides simple in-memory buffer operations for performance testing.
 */
public class MockAsyncByteBufferPool implements AsyncByteBufferPool {

    @Override
    public CompletableFuture<ByteBuffer> acquireAsync(int size) {
        return CompletableFuture.completedFuture(ByteBuffer.allocate(size));
    }

    @Override
    public CompletableFuture<Void> releaseAsync(ByteBuffer buffer) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> clearAsync() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Integer> getAvailableCountAsync() {
        return CompletableFuture.completedFuture(100);
    }

    @Override
    public CompletableFuture<Integer> getTotalCountAsync() {
        return CompletableFuture.completedFuture(100);
    }

    @Override
    public CompletableFuture<Integer> getBuffersInUseAsync() {
        return CompletableFuture.completedFuture(0);
    }

    @Override
    public CompletableFuture<String> getStatsAsync() {
        return CompletableFuture.completedFuture("MockBufferPool");
    }

    // BufferPool interface methods
    @Override
    public int getDefaultBufferSize() {
        return 8192; // 8KB default
    }

    @Override
    public ByteBuffer acquire(int size) {
        return ByteBuffer.allocate(size);
    }

    @Override
    public void release(ByteBuffer buffer) {
        // No-op for mock
    }

    @Override
    public void clear() {
        // No-op for mock
    }

    @Override
    public int getAvailableCount() {
        return 100;
    }

    @Override
    public int getTotalCount() {
        return 100;
    }

}