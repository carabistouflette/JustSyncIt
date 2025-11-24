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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for batch processing system.
 * Validates that all components work together correctly.
 */
@DisplayName("Batch Processing Integration Tests")
public class BatchProcessingIntegrationTest {

    private AsyncFileBatchProcessorImpl batchProcessor;
    private BatchConfiguration configuration;

    @BeforeEach
    void setUp() {
        configuration = new BatchConfiguration();
        
        // Create mock Blake3Service for testing
        Blake3Service mockBlake3Service = new MockBlake3Service();
        
        // Create real instances for integration testing
        AsyncFileChunker asyncFileChunker = AsyncFileChunkerImpl.create(mockBlake3Service);
        AsyncByteBufferPool asyncBufferPool = AsyncByteBufferPoolImpl.create();
        ThreadPoolManager threadPoolManager = ThreadPoolManager.getInstance();
        
        batchProcessor = AsyncFileBatchProcessorImpl.create(
                asyncFileChunker, asyncBufferPool, threadPoolManager, configuration);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (batchProcessor != null && !batchProcessor.isClosed()) {
            batchProcessor.closeAsync().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("Should create batch processor successfully")
    void shouldCreateBatchProcessorSuccessfully() {
        // Then
        assertNotNull(batchProcessor);
        assertFalse(batchProcessor.isClosed());
        assertEquals(10, batchProcessor.getMaxConcurrentBatchOperations());
        assertNotNull(batchProcessor.getAsyncFileChunker());
        assertNotNull(batchProcessor.getAsyncBufferPool());
        assertNotNull(batchProcessor.getThreadPoolManager());
        assertNotNull(batchProcessor.getBatchProcessingStats());
        assertTrue(batchProcessor.supportsAdaptiveSizing());
        assertTrue(batchProcessor.supportsPriorityScheduling());
        assertTrue(batchProcessor.supportsDependencies());
    }

    @Test
    @DisplayName("Should handle empty batch gracefully")
    void shouldHandleEmptyBatchGracefully() throws Exception {
        // Given
        List<Path> emptyFiles = Arrays.asList();
        BatchOptions options = new BatchOptions();

        // When
        CompletableFuture<BatchResult> future = batchProcessor.processBatch(emptyFiles, options);
        BatchResult result = future.get(10, TimeUnit.SECONDS);

        // Then
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
        assertTrue(result.getError().getMessage().contains("Files list cannot be null or empty"));
    }

    @Test
    @DisplayName("Should apply backpressure correctly")
    void shouldApplyBackpressureCorrectly() {
        // Given
        double initialBackpressure = batchProcessor.getCurrentBackpressure();

        // When
        batchProcessor.applyBackpressure(0.5);

        // Then
        assertEquals(0.5, batchProcessor.getCurrentBackpressure());
        assertNotEquals(initialBackpressure, batchProcessor.getCurrentBackpressure());

        // When
        batchProcessor.releaseBackpressure();

        // Then
        assertEquals(0.0, batchProcessor.getCurrentBackpressure());
    }

    @Test
    @DisplayName("Should update configuration correctly")
    void shouldUpdateConfigurationCorrectly() {
        // Given
        BatchConfiguration newConfig = new BatchConfiguration()
                .withMaxConcurrentBatches(20);

        // When
        batchProcessor.updateConfiguration(newConfig);

        // Then
        assertEquals(20, batchProcessor.getMaxConcurrentBatchOperations());
    }

    @Test
    @DisplayName("Should validate batch processing capabilities")
    void shouldValidateBatchProcessingCapabilities() {
        // Then
        assertTrue(batchProcessor.supportsAdaptiveSizing());
        assertTrue(batchProcessor.supportsPriorityScheduling());
        assertTrue(batchProcessor.supportsDependencies());
        
        BatchProcessingStats stats = batchProcessor.getBatchProcessingStats();
        assertNotNull(stats);
        assertEquals(0, stats.getTotalBatchesProcessed());
        assertEquals(0, stats.getTotalFilesProcessed());
}

/**
 * Simple mock implementation of Blake3Service for testing.
 */
private static class MockBlake3Service implements Blake3Service {
    
    @Override
    public String hashFile(Path filePath) throws IOException {
        return "mock_hash_" + filePath.toString().hashCode();
    }
    
    @Override
    public String hashBuffer(byte[] data) throws com.justsyncit.hash.HashingException {
        return "mock_hash_" + Arrays.hashCode(data);
    }
    
    @Override
    public String hashStream(InputStream inputStream) throws IOException, com.justsyncit.hash.HashingException {
        return "mock_stream_hash";
    }
    
    @Override
    public Blake3IncrementalHasher createIncrementalHasher() throws com.justsyncit.hash.HashingException {
        return new MockIncrementalHasher();
    }
    
    @Override
    public Blake3Info getInfo() {
        return new MockBlake3Info();
    }
    
    private static class MockIncrementalHasher implements Blake3IncrementalHasher {
        @Override
        public void update(byte[] data) {
            // Mock implementation
        }
        
        @Override
        public void update(byte[] data, int offset, int length) {
            // Mock implementation
        }
        
        @Override
        public String digest() throws com.justsyncit.hash.HashingException {
            return "mock_incremental_hash";
        }
        
        @Override
        public void reset() {
            // Mock implementation
        }
    }
    
    private static class MockBlake3Info implements Blake3Info {
        @Override
        public String getVersion() {
            return "mock-1.0.0";
        }
        
        @Override
        public boolean hasSimdSupport() {
            return true;
        }
        
        @Override
        public String getSimdInstructionSet() {
            return "MOCK-SIMD";
        }
        
        @Override
        public boolean isJniImplementation() {
            return false;
        }
    }
}
}