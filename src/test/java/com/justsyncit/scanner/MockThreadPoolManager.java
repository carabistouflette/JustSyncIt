package com.justsyncit.scanner;

import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mock implementation for testing ThreadPoolManager functionality.
 * Provides controllable behavior and state tracking for test scenarios.
 */
@DisplayName("Mock Thread Pool Manager")
public class MockThreadPoolManager {

    private final AtomicInteger activeOperations;
    private final AtomicLong totalTasksSubmitted;
    private final AtomicLong totalTasksCompleted;
    private final AtomicLong totalTasksFailed;
    private volatile boolean shutdown;
    private volatile int maxConcurrentOperations;
    private final List<MockManagedThreadPool> managedPools;
    private final ExecutorService ioThreadPool;
    private final ExecutorService cpuThreadPool;
    private final ExecutorService completionHandlerThreadPool;
    private final ExecutorService batchProcessingThreadPool;
    private final ExecutorService watchServiceThreadPool;
    private final ExecutorService managementThreadPool;

    public MockThreadPoolManager() {
        this.activeOperations = new AtomicInteger(0);
        this.totalTasksSubmitted = new AtomicLong(0);
        this.totalTasksCompleted = new AtomicLong(0);
        this.totalTasksFailed = new AtomicLong(0);
        this.shutdown = false;
        this.maxConcurrentOperations = 10;
        this.managedPools = new ArrayList<>();

        // Create mock thread pools
        this.ioThreadPool = Executors.newFixedThreadPool(4);
        this.cpuThreadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        this.completionHandlerThreadPool = Executors.newFixedThreadPool(4);
        this.batchProcessingThreadPool = Executors.newFixedThreadPool(2);
        this.watchServiceThreadPool = Executors.newFixedThreadPool(2);
        this.managementThreadPool = Executors.newFixedThreadPool(1);
    }

    /**
     * Gets I/O thread pool optimized for AsynchronousFileChannel operations.
     */
    public ExecutorService getIoThreadPool() {
        if (shutdown) {
            throw new IllegalStateException("Manager is shutdown");
        }
        return ioThreadPool;
    }

    /**
     * Gets CPU thread pool for hashing and computation-intensive operations.
     */
    public ExecutorService getCpuThreadPool() {
        if (shutdown) {
            throw new IllegalStateException("Manager is shutdown");
        }
        return cpuThreadPool;
    }

    /**
     * Gets CompletionHandler thread pool for callback processing.
     */
    public ExecutorService getCompletionHandlerThreadPool() {
        if (shutdown) {
            throw new IllegalStateException("Manager is shutdown");
        }
        return completionHandlerThreadPool;
    }

    /**
     * Gets batch processing thread pool for coordinated batch operations.
     */
    public ExecutorService getBatchProcessingThreadPool() {
        if (shutdown) {
            throw new IllegalStateException("Manager is shutdown");
        }
        return batchProcessingThreadPool;
    }

    /**
     * Gets WatchService thread pool for directory monitoring operations.
     */
    public ExecutorService getWatchServiceThreadPool() {
        if (shutdown) {
            throw new IllegalStateException("Manager is shutdown");
        }
        return watchServiceThreadPool;
    }

    /**
     * Gets management thread pool for internal operations.
     */
    public ExecutorService getManagementThreadPool() {
        if (shutdown) {
            throw new IllegalStateException("Manager is shutdown");
        }
        return managementThreadPool;
    }

    /**
     * Submits a task to appropriate thread pool.
     */
    public <T> CompletableFuture<T> submitTask(ThreadPoolManager.PoolType type, java.util.concurrent.Callable<T> task) {
        if (shutdown) {
            return CompletableFuture.failedFuture(new IllegalStateException("Manager is shutdown"));
        }

        totalTasksSubmitted.incrementAndGet();
        activeOperations.incrementAndGet();

        ExecutorService executor = getThreadPoolForType(type);
        return CompletableFuture.supplyAsync(() -> {
            try {
                T result = task.call();
                totalTasksCompleted.incrementAndGet();
                return result;
            } catch (Exception e) {
                totalTasksFailed.incrementAndGet();
                throw new RuntimeException("Task execution failed", e);
            } finally {
                activeOperations.decrementAndGet();
            }
        }, executor);
    }

    /**
     * Gets comprehensive statistics for all thread pools.
     */
    public ThreadPoolStats getStats() {
        if (shutdown) {
            throw new IllegalStateException("Manager is shutdown");
        }

        // Create mock stats
        return new ThreadPoolStats.Builder()
                .setPoolName("MockThreadPoolManager")
                .setActiveThreads(activeOperations.get())
                .setTotalTasks((int) totalTasksSubmitted.get())
                .setCompletedTasks((int) totalTasksCompleted.get())
                .setSubmittedTasks(totalTasksSubmitted.get())
                .setCompletedSubmittedTasks(totalTasksCompleted.get())
                .setAverageExecutionTime(100.0)
                .setThroughput(10.0)
                .setUtilizationRate(0.5)
                .setEfficiency(0.8)
                .build();
    }

    /**
     * Triggers adaptive resizing of all thread pools.
     */
    public void triggerAdaptiveResizing() {
        if (shutdown) {
            throw new IllegalStateException("Manager is shutdown");
        }
        // Mock adaptive resizing - no actual implementation needed for testing
    }

    /**
     * Applies backpressure to all thread pools.
     */
    public void applyBackpressure(double pressureLevel) {
        if (shutdown) {
            throw new IllegalStateException("Manager is shutdown");
        }
        // Mock backpressure application
    }

    /**
     * Releases backpressure from all thread pools.
     */
    public void releaseBackpressure() {
        if (shutdown) {
            throw new IllegalStateException("Manager is shutdown");
        }
        // Mock backpressure release
    }

    /**
     * Shuts down all thread pools and management services.
     */
    public void shutdown() {
        shutdown = true;

        try {
            ioThreadPool.shutdown();
            cpuThreadPool.shutdown();
            completionHandlerThreadPool.shutdown();
            batchProcessingThreadPool.shutdown();
            watchServiceThreadPool.shutdown();
            managementThreadPool.shutdown();

            // Wait for termination
            if (!ioThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                ioThreadPool.shutdownNow();
            }
            if (!cpuThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                cpuThreadPool.shutdownNow();
            }
            if (!completionHandlerThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                completionHandlerThreadPool.shutdownNow();
            }
            if (!batchProcessingThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                batchProcessingThreadPool.shutdownNow();
            }
            if (!watchServiceThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                watchServiceThreadPool.shutdownNow();
            }
            if (!managementThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                managementThreadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // Force shutdown
            ioThreadPool.shutdownNow();
            cpuThreadPool.shutdownNow();
            completionHandlerThreadPool.shutdownNow();
            batchProcessingThreadPool.shutdownNow();
            watchServiceThreadPool.shutdownNow();
            managementThreadPool.shutdownNow();
        }
    }

    /**
     * Checks if manager is shutdown.
     */
    public boolean isShutdown() {
        return shutdown;
    }

    /**
     * Gets current number of active operations.
     */
    public int getActiveOperations() {
        return activeOperations.get();
    }

    /**
     * Gets maximum concurrent operations.
     */
    public int getMaxConcurrentOperations() {
        return maxConcurrentOperations;
    }

    /**
     * Sets maximum concurrent operations.
     */
    public void setMaxConcurrentOperations(int maxConcurrentOperations) {
        this.maxConcurrentOperations = maxConcurrentOperations;
    }

    // Test control methods
    public void reset() {
        activeOperations.set(0);
        totalTasksSubmitted.set(0);
        totalTasksCompleted.set(0);
        totalTasksFailed.set(0);
        shutdown = false;
        managedPools.clear();
    }

    public long getTotalTasksSubmitted() {
        return totalTasksSubmitted.get();
    }

    public long getTotalTasksCompleted() {
        return totalTasksCompleted.get();
    }

    public long getTotalTasksFailed() {
        return totalTasksFailed.get();
    }

    public List<MockManagedThreadPool> getManagedPools() {
        return new ArrayList<>(managedPools);
    }

    public void setShutdown(boolean shutdown) {
        this.shutdown = shutdown;
    }

    /**
     * Simulate task failure
     */
    public void simulateTaskFailure() {
        totalTasksFailed.incrementAndGet();
    }

    /**
     * Simulate task completion
     */
    public void simulateTaskCompletion() {
        totalTasksCompleted.incrementAndGet();
    }

    private ExecutorService getThreadPoolForType(ThreadPoolManager.PoolType type) {
        switch (type) {
            case IO:
                return ioThreadPool;
            case CPU:
                return cpuThreadPool;
            case COMPLETION_HANDLER:
                return completionHandlerThreadPool;
            case BATCH_PROCESSING:
                return batchProcessingThreadPool;
            case WATCH_SERVICE:
                return watchServiceThreadPool;
            case MANAGEMENT:
                return managementThreadPool;
            default:
                throw new IllegalArgumentException("Unknown pool type: " + type);
        }
    }

    /**
     * Mock implementation of ManagedThreadPool for testing
     */
    public static class MockManagedThreadPool {
        private final String poolName;
        private final int corePoolSize;
        private final int maximumPoolSize;
        private volatile boolean shutdown;
        private final AtomicInteger activeThreads;
        private final AtomicInteger queueSize;
        private final AtomicLong completedTasks;

        public MockManagedThreadPool(String poolName, int corePoolSize, int maximumPoolSize) {
            this.poolName = poolName;
            this.corePoolSize = corePoolSize;
            this.maximumPoolSize = maximumPoolSize;
            this.shutdown = false;
            this.activeThreads = new AtomicInteger(0);
            this.queueSize = new AtomicInteger(0);
            this.completedTasks = new AtomicLong(0);
        }

        public String getPoolName() {
            return poolName;
        }

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public int getMaximumPoolSize() {
            return maximumPoolSize;
        }

        public boolean isShutdown() {
            return shutdown;
        }

        public int getActiveThreads() {
            return activeThreads.get();
        }

        public int getQueueSize() {
            return queueSize.get();
        }

        public long getCompletedTasks() {
            return completedTasks.get();
        }

        public void shutdown() {
            shutdown = true;
        }

        public ThreadPoolStats.PoolSpecificStats getPoolStats() {
            return new ThreadPoolStats.PoolSpecificStats(
                    0, // resizeCount
                    System.currentTimeMillis(), // lastResizeTime
                    0, // consecutiveOptimizations
                    0.7, // currentEfficiency
                    0.8, // targetEfficiency
                    50.0, // averageLatency
                    100.0 // throughput
            );
        }
    }
}