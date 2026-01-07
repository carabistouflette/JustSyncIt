package com.justsyncit.scanner;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

public class ThroughputAbsurdityTest {

    private static class TestManagedThreadPool extends ManagedThreadPool {
        public TestManagedThreadPool(ThreadPoolConfiguration.PoolConfig config) {
            super(config,
                    new SystemResourceInfo(),
                    null, // monitor
                    ThreadPoolManager.PoolType.IO,
                    new LinkedBlockingQueue<>(),
                    Executors.defaultThreadFactory(),
                    new ThreadPoolExecutor.AbortPolicy());
        }
    }

    @Test
    public void testThroughputIsNotAbsurd() throws InterruptedException {
        ThreadPoolConfiguration.PoolConfig config = new ThreadPoolConfiguration.PoolConfig.Builder()
                .corePoolSize(2)
                .maximumPoolSize(4)
                .keepAliveTimeMs(1000)
                .build();

        TestManagedThreadPool pool = new TestManagedThreadPool(config);

        int taskCount = 50;
        CountDownLatch latch = new CountDownLatch(taskCount);

        for (int i = 0; i < taskCount; i++) {
            pool.submit(() -> {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Tasks should complete");

        ThreadPoolStats.PoolSpecificStats stats = pool.getPoolStats();
        double throughput = stats.getThroughput();

        System.out.println("Calculated Throughput: " + throughput);

        // Throughput should be roughly 50 tasks / 0.5 sec = 100 tasks/sec.
        // Even if slow, it should be > 1.0.
        // The bug makes it ~ 50 / 1.7e9 ~= 0.000000029

        assertTrue(throughput > 1.0, "Throughput " + throughput + " is absurdly low (likely dividing by epoch time)");
    }
}
