package com.justsyncit.integrity.service;

import com.justsyncit.network.protocol.ProtocolConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pools reusable byte arrays for Reed-Solomon operations to reduce GC pressure.
 * Stores 1MB buffers (ProtocolConstants.MAX_CHUNK_SIZE).
 */
public class ShardBufferPool {
    private static final Logger logger = LoggerFactory.getLogger(ShardBufferPool.class);
    private static final int BUFFER_SIZE = ProtocolConstants.MAX_CHUNK_SIZE;
    private static final int MAX_POOL_SIZE = 128; // 128MB max memory

    private static final ShardBufferPool INSTANCE = new ShardBufferPool();

    private final Queue<byte[]> pool = new ConcurrentLinkedQueue<>();
    private final AtomicInteger createdCount = new AtomicInteger(0);
    private final AtomicInteger poolSize = new AtomicInteger(0);

    public static ShardBufferPool getInstance() {
        return INSTANCE;
    }

    /**
     * Acquires a buffer. If pool is empty, allocates a new one.
     * Returned buffer size is always equal to ProtocolConstants.MAX_CHUNK_SIZE.
     */
    public byte[] acquire() {
        byte[] buffer = pool.poll();
        if (buffer == null) {
            createdCount.incrementAndGet();
            if (logger.isTraceEnabled()) {
                logger.trace("Allocated new shard buffer. Total created: {}", createdCount.get());
            }
            return new byte[BUFFER_SIZE];
        }
        poolSize.decrementAndGet();
        return buffer;
    }

    /**
     * Releases a buffer back to the pool.
     * Only accepts buffers of size ProtocolConstants.MAX_CHUNK_SIZE.
     */
    public void release(byte[] buffer) {
        if (buffer == null || buffer.length != BUFFER_SIZE) {
            return;
        }

        // Simple bounding to prevent infinite growth
        if (poolSize.get() < MAX_POOL_SIZE) {
            pool.offer(buffer);
            poolSize.incrementAndGet();
        } else {
            if (logger.isTraceEnabled()) {
                logger.trace("Pool full, discarding buffer. Pool size: {}", poolSize.get());
            }
        }
    }
}
