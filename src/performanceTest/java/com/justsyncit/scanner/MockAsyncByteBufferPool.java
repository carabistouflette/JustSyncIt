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

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

/**
 * Mock implementation of AsyncByteBufferPool for testing.
 * Provides simple in-memory buffer operations for performance testing.
 */
public class MockAsyncByteBufferPool implements AsyncByteBufferPool, BufferPool {

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