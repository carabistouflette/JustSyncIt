package com.justsyncit.scanner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Debug test to isolate the hanging issue in AsyncErrorHandlingTest.
 */
@DisplayName("Async Error Handling Debug Test")
class AsyncErrorHandlingDebugTest {

    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        executorService = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
            try {
                if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                    System.err.println("Executor did not terminate within 2 seconds");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    @DisplayName("Simple buffer pool test")
    void simpleBufferPoolTest() throws Exception {
        AsyncByteBufferPool pool = AsyncByteBufferPoolImpl.create(8192, 2);
        
        try {
            // Acquire all buffers
            ByteBuffer buffer1 = pool.acquireAsync(8192).get(5, TimeUnit.SECONDS);
            ByteBuffer buffer2 = pool.acquireAsync(8192).get(5, TimeUnit.SECONDS);
            
            assertNotNull(buffer1);
            assertNotNull(buffer2);
            
            // Try to acquire one more - should fail or timeout
            CompletableFuture<ByteBuffer> excessFuture = pool.acquireAsync(8192);
            try {
                ByteBuffer buffer3 = excessFuture.get(1, TimeUnit.SECONDS);
                // If it succeeds, release it
                pool.releaseAsync(buffer3);
            } catch (Exception e) {
                // Expected - pool is exhausted
                System.out.println("Expected exception: " + e.getMessage());
            }
            
            // Release one buffer
            pool.releaseAsync(buffer1);
            
            // Now acquisition should work
            ByteBuffer buffer4 = pool.acquireAsync(8192).get(5, TimeUnit.SECONDS);
            assertNotNull(buffer4);
            pool.releaseAsync(buffer4);
            
            // Release the second buffer
            pool.releaseAsync(buffer2);
            
        } finally {
            pool.clear();
        }
    }
}