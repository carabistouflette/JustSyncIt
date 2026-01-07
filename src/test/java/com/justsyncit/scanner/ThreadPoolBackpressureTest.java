package com.justsyncit.scanner;

import org.junit.jupiter.api.Test;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for reproducing the Thread Pool Implosion bug in ManagedThreadPool.
 */
public class ThreadPoolBackpressureTest {

    // Concrete implementation for testing
    private static class TestManagedThreadPool extends ManagedThreadPool {
        TestManagedThreadPool(ThreadPoolConfiguration.PoolConfig config) {
            super(config,
                    null, // SystemResourceInfo
                    null, // ThreadPoolMonitor
                    ThreadPoolManager.PoolType.IO,
                    new LinkedBlockingQueue<>(),
                    Executors.defaultThreadFactory(),
                    new ThreadPoolExecutor.AbortPolicy());
        }

        public ThreadPoolExecutor getThreadPoolExecutor() {
            return this.executor;
        }
    }

    @Test
    public void testBackpressureIdeallyShouldNotCompound() {
        int coreSize = 10;
        int maxSize = 100;

        ThreadPoolConfiguration.PoolConfig config = new ThreadPoolConfiguration.PoolConfig.Builder()
                .corePoolSize(coreSize)
                .maximumPoolSize(maxSize)
                .keepAliveTimeMs(1000)
                .build();

        TestManagedThreadPool pool = new TestManagedThreadPool(config);

        // Verify initial state
        assertEquals(maxSize, pool.getThreadPoolExecutor().getMaximumPoolSize(),
                "Initial max size should match config");

        // Apply backpressure once (100% pressure)
        // Logic: 1.0 pressure -> 0.3 factor -> reduce by 30% -> target 70
        // Current impl: currentMax * (1.0 - 0.3) = 100 * 0.7 = 70
        pool.applyBackpressure(1.0);
        int sizeAfterFirstCall = pool.getThreadPoolExecutor().getMaximumPoolSize();

        // This assertion might pass on both valid and invalid implementations if they
        // do the same first step
        assertTrue(sizeAfterFirstCall < maxSize, "Pool size should be reduced");
        assertEquals(70, sizeAfterFirstCall, "Pool size should be approx 70% of max");

        // Apply backpressure AGAIN (still 100% pressure)
        // Correct behavior: Should stay at 70 (calculated from CONFIG max)
        // Buggy behavior: Calculates from CURRENT max (70) -> 70 * 0.7 = 49
        pool.applyBackpressure(1.0);
        int sizeAfterSecondCall = pool.getThreadPoolExecutor().getMaximumPoolSize();

        // If bug exists, this will fail or be 49
        // We expect the fix to make this 70
        assertEquals(70, sizeAfterSecondCall,
                "Backpressure calculation should be idempotent based on config, but it compounded!");

        // Apply backpressure multiple times to demonstrate the "implosion"
        for (int i = 0; i < 5; i++) {
            pool.applyBackpressure(1.0);
        }

        int finalSize = pool.getThreadPoolExecutor().getMaximumPoolSize();
        assertEquals(70, finalSize, "Pool size imploded after repeated backpressure calls!");

        pool.shutdown();
    }
}
