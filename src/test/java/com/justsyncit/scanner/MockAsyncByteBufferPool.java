package com.justsyncit.scanner;

import org.junit.jupiter.api.DisplayName;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mock implementation of AsyncByteBufferPool for testing.
 * Provides controllable behavior and state tracking for test scenarios.
 */
@DisplayName("Mock Async Buffer Pool")
public class MockAsyncByteBufferPool implements AsyncByteBufferPool {

    private final AtomicInteger acquireCount;
    private final AtomicInteger releaseCount;
    private final AtomicInteger totalBuffers;
    private final ConcurrentLinkedQueue<ByteBuffer> availableBuffers;
    private final AtomicLong totalBytesAllocated;
    private final AtomicInteger maxPoolSize;
    private volatile boolean closed;

    public MockAsyncByteBufferPool() {
        this.acquireCount = new AtomicInteger(0);
        this.releaseCount = new AtomicInteger(0);
        this.totalBuffers = new AtomicInteger(0);
        this.availableBuffers = new ConcurrentLinkedQueue<>();
        this.totalBytesAllocated = new AtomicLong(0);
        this.maxPoolSize = new AtomicInteger(Integer.MAX_VALUE);
        this.closed = false;
    }

    public MockAsyncByteBufferPool(int maxPoolSize) {
        this();
        this.maxPoolSize.set(maxPoolSize);
    }

    @Override
    public CompletableFuture<ByteBuffer> acquireAsync(int size) {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Pool is closed"));
        }

        acquireCount.incrementAndGet();
        
        return CompletableFuture.supplyAsync(() -> {
            if (totalBuffers.get() >= maxPoolSize.get()) {
                throw new RuntimeException("Pool exhausted - maximum size reached: " + maxPoolSize.get());
            }
            
            ByteBuffer buffer = ByteBuffer.allocate(Math.max(size, 1024));
            availableBuffers.offer(buffer);
            totalBuffers.incrementAndGet();
            totalBytesAllocated.addAndGet(buffer.capacity());
            return buffer;
        });
    }

    @Override
    public CompletableFuture<Void> releaseAsync(ByteBuffer buffer) {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Pool is closed"));
        }

        releaseCount.incrementAndGet();
        
        return CompletableFuture.runAsync(() -> {
            if (buffer != null) {
                availableBuffers.remove(buffer);
                totalBuffers.decrementAndGet();
            }
        });
    }

    @Override
    public CompletableFuture<Void> clearAsync() {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Pool is closed"));
        }

        return CompletableFuture.runAsync(() -> {
            availableBuffers.clear();
            totalBuffers.set(0);
        });
    }

    @Override
    public CompletableFuture<Integer> getAvailableCountAsync() {
        return CompletableFuture.completedFuture(availableBuffers.size());
    }

    @Override
    public CompletableFuture<Integer> getTotalCountAsync() {
        return CompletableFuture.completedFuture(totalBuffers.get());
    }

    @Override
    public CompletableFuture<Integer> getBuffersInUseAsync() {
        return CompletableFuture.completedFuture(totalBuffers.get() - availableBuffers.size());
    }

    @Override
    public CompletableFuture<String> getStatsAsync() {
        return CompletableFuture.completedFuture(String.format(
                "MockAsyncByteBufferPool{acquired=%d, released=%d, total=%d, available=%d, inUse=%d, totalBytes=%d, maxPoolSize=%d, closed=%b}",
                acquireCount.get(), releaseCount.get(), totalBuffers.get(), availableBuffers.size(),
                totalBuffers.get() - availableBuffers.size(), totalBytesAllocated.get(), maxPoolSize.get(), closed));
    }

    @Override
    public ByteBuffer acquire(int size) {
        if (closed) {
            throw new IllegalStateException("Pool is closed");
        }

        acquireCount.incrementAndGet();
        
        if (totalBuffers.get() >= maxPoolSize.get()) {
            throw new RuntimeException("Pool exhausted - maximum size reached: " + maxPoolSize.get());
        }
        
        ByteBuffer buffer = ByteBuffer.allocate(Math.max(size, 1024));
        availableBuffers.offer(buffer);
        totalBuffers.incrementAndGet();
        totalBytesAllocated.addAndGet(buffer.capacity());
        return buffer;
    }

    @Override
    public void release(ByteBuffer buffer) {
        if (closed) {
            throw new IllegalStateException("Pool is closed");
        }

        releaseCount.incrementAndGet();
        
        if (buffer != null) {
            availableBuffers.remove(buffer);
            totalBuffers.decrementAndGet();
        }
    }

    @Override
    public void clear() {
        if (closed) {
            throw new IllegalStateException("Pool is closed");
        }

        availableBuffers.clear();
        totalBuffers.set(0);
    }

    @Override
    public int getAvailableCount() {
        return availableBuffers.size();
    }

    @Override
    public int getTotalCount() {
        return totalBuffers.get();
    }

    @Override
    public int getDefaultBufferSize() {
        return 1024;
    }

    // Test control methods
    public void reset() {
        acquireCount.set(0);
        releaseCount.set(0);
        totalBuffers.set(0);
        availableBuffers.clear();
        totalBytesAllocated.set(0);
        closed = false;
    }

    public int getAcquireCount() {
        return acquireCount.get();
    }

    public int getReleaseCount() {
        return releaseCount.get();
    }

    public long getTotalBytesAllocated() {
        return totalBytesAllocated.get();
    }

    public int getMaxPoolSize() {
        return maxPoolSize.get();
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize.set(maxPoolSize);
    }

    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    /**
     * Simulate pool exhaustion by setting max size to current count
     */
    public void simulateExhaustion() {
        maxPoolSize.set(totalBuffers.get());
    }

    /**
     * Simulate pool recovery by increasing max size
     */
    public void simulateRecovery() {
        maxPoolSize.set(Integer.MAX_VALUE);
    }
}