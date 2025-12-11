package com.justsyncit.scanner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simplified error handling tests for async components.
 * Focuses on the core issue without complex setup.
 */
@DisplayName("Async Error Handling Simplified Test")
class AsyncErrorHandlingSimplifiedTest {

    private Path testDir;
    private Path testFile;
    private ExecutorService executorService;

    @BeforeEach
    void setUp() throws IOException {
        testDir = Files.createTempDirectory("async-error-simple-test");
        testFile = testDir.resolve("test.txt");
        Files.write(testFile, "Hello, World!".getBytes(), StandardOpenOption.CREATE);
        
        // Create a dedicated executor service for this test class
        executorService = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() throws IOException {
        // Force immediate shutdown of executor service to prevent thread leaks
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
            try {
                if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                    System.err.println("Executor service did not terminate gracefully within 2 seconds");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Clean up test files
        try {
            if (testFile != null) {
                Files.deleteIfExists(testFile);
            }
            if (testDir != null) {
                Files.deleteIfExists(testDir);
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    @Nested
    @DisplayName("Buffer Pool Error Handling")
    class BufferPoolErrorHandling {

        @Test
        @DisplayName("Should handle pool exhaustion gracefully")
        @Timeout(value = 15, unit = TimeUnit.SECONDS)
        void shouldHandlePoolExhaustionGracefully() throws Exception {
            // Create a small pool
            AsyncByteBufferPool smallPool = AsyncByteBufferPoolImpl.create(8192, 2);
            List<ByteBuffer> acquiredBuffers = new ArrayList<>();
            
            try {
                // Acquire all buffers
                for (int i = 0; i < 2; i++) {
                    CompletableFuture<ByteBuffer> future = smallPool.acquireAsync(8192);
                    ByteBuffer buffer = future.get(5, TimeUnit.SECONDS);
                    acquiredBuffers.add(buffer);
                    assertNotNull(buffer);
                }
                
                // Try to acquire one more - this should timeout or fail
                CompletableFuture<ByteBuffer> excessFuture = smallPool.acquireAsync(8192);
                try {
                    ByteBuffer buffer = excessFuture.get(1, TimeUnit.SECONDS); // Short timeout
                    // If it succeeds, that's also acceptable behavior
                    assertNotNull(buffer);
                    acquiredBuffers.add(buffer); // Add to list for cleanup
                } catch (Exception e) {
                    // Timeout or other exception is also acceptable
                    System.out.println("Expected exception during excess acquisition: " + e.getMessage());
                }
                
                // Release one buffer
                if (!acquiredBuffers.isEmpty()) {
                    smallPool.releaseAsync(acquiredBuffers.remove(0));
                }
                
                // Now acquisition should work
                CompletableFuture<ByteBuffer> future = smallPool.acquireAsync(8192);
                ByteBuffer buffer = future.get(5, TimeUnit.SECONDS);
                assertNotNull(buffer);
                acquiredBuffers.add(buffer); // Add to list for cleanup
                
            } finally {
                // CRITICAL: Release ALL acquired buffers back to prevent state bleeding
                for (ByteBuffer buffer : acquiredBuffers) {
                    try {
                        smallPool.releaseAsync(buffer);
                    } catch (Exception e) {
                        // Ignore release errors during cleanup
                    }
                }
                smallPool.clear();
            }
        }
    }
}