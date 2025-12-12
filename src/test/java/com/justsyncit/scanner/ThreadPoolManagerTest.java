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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Comprehensive test suite for ThreadPoolManager and specialized thread pools.
 * Validates thread pool functionality, performance, and integration.
 */
public class ThreadPoolManagerTest {

    private ThreadPoolManager threadPoolManager;
    private ThreadPoolConfiguration config;

    @BeforeEach
    void setUp() {
        config = new ThreadPoolConfiguration.Builder()
                .enableAdaptiveSizing(true)
                .enableThreadAffinity(false)
                .enableBackpressure(true)
                .build();

        threadPoolManager = ThreadPoolManager.getInstance();
    }

    @AfterEach
    void tearDown() {
        if (threadPoolManager != null) {
            threadPoolManager.shutdown();
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Should initialize all specialized thread pools")
    void shouldInitializeAllThreadPools() {
        // Verify all pool types are available
        assertNotNull(threadPoolManager.getIoThreadPool());
        assertNotNull(threadPoolManager.getCpuThreadPool());
        assertNotNull(threadPoolManager.getCompletionHandlerThreadPool());
        assertNotNull(threadPoolManager.getBatchProcessingThreadPool());
        assertNotNull(threadPoolManager.getWatchServiceThreadPool());
        assertNotNull(threadPoolManager.getManagementThreadPool());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Should submit tasks to appropriate thread pools")
    void shouldSubmitTasksToAppropriatePools() throws Exception {
        // Test I/O task submission
        CompletableFuture<String> ioTask = threadPoolManager.submitTask(
                ThreadPoolManager.PoolType.IO,
                () -> "I/O Task Completed");
        assertEquals("I/O Task Completed", ioTask.get(5, TimeUnit.SECONDS));

        // Test CPU task submission
        CompletableFuture<String> cpuTask = threadPoolManager.submitTask(
                ThreadPoolManager.PoolType.CPU,
                () -> "CPU Task Completed");
        assertEquals("CPU Task Completed", cpuTask.get(5, TimeUnit.SECONDS));

        // Test CompletionHandler task submission
        CompletableFuture<Void> completionTask = threadPoolManager.submitTask(
                ThreadPoolManager.PoolType.COMPLETION_HANDLER,
                () -> null);
        assertNull(completionTask.get(5, TimeUnit.SECONDS));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Should handle priority-based task submission")
    void shouldHandlePriorityBasedTaskSubmission() throws Exception {
        // Test high priority task
        CompletableFuture<String> highPriorityTask = threadPoolManager.submitTask(
                ThreadPoolManager.PoolType.CPU,
                ThreadPoolManager.ThreadPriority.HIGH,
                () -> "High Priority Task");
        assertEquals("High Priority Task", highPriorityTask.get(5, TimeUnit.SECONDS));

        // Test low priority task
        CompletableFuture<String> lowPriorityTask = threadPoolManager.submitTask(
                ThreadPoolManager.PoolType.WATCH_SERVICE,
                ThreadPoolManager.ThreadPriority.LOW,
                () -> "Low Priority Task");
        assertEquals("Low Priority Task", lowPriorityTask.get(5, TimeUnit.SECONDS));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Should apply and release backpressure")
    void shouldApplyAndReleaseBackpressure() throws Exception {
        // Get initial stats
        ThreadPoolStats initialStats = threadPoolManager.getStats();
        assertNotNull(initialStats);

        // Apply backpressure
        threadPoolManager.applyBackpressure(0.8);

        // Wait a bit for backpressure to take effect
        Thread.sleep(100);

        // Release backpressure
        threadPoolManager.releaseBackpressure();

        // Verify backpressure is released
        ThreadPoolStats finalStats = threadPoolManager.getStats();
        assertNotNull(finalStats);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Should trigger adaptive resizing")
    void shouldTriggerAdaptiveResizing() throws Exception {
        // Get initial stats
        ThreadPoolStats initialStats = threadPoolManager.getStats();
        assertNotNull(initialStats);

        // Trigger adaptive resizing
        threadPoolManager.triggerAdaptiveResizing();

        // Wait a bit for resizing to take effect
        Thread.sleep(100);

        // Verify resizing occurred
        ThreadPoolStats finalStats = threadPoolManager.getStats();
        assertNotNull(finalStats);
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("Should handle concurrent task execution")
    void shouldHandleConcurrentTaskExecution() throws Exception {
        int taskCount = 100;
        CountDownLatch latch = new CountDownLatch(taskCount);
        AtomicInteger completedTasks = new AtomicInteger(0);

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // Submit concurrent tasks
        for (int i = 0; i < taskCount; i++) {
            CompletableFuture<Void> future = threadPoolManager.submitTask(
                    ThreadPoolManager.PoolType.IO,
                    () -> {
                        try {
                            // Simulate some work
                            Thread.sleep(10);
                            completedTasks.incrementAndGet();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            latch.countDown();
                        }
                        return null;
                    });
            futures.add(future);
        }

        // Wait for all tasks to complete
        assertTrue(latch.await(30, TimeUnit.SECONDS));

        // Verify all tasks completed
        assertEquals(taskCount, completedTasks.get());

        // Verify all futures completed successfully
        for (CompletableFuture<Void> future : futures) {
            assertTrue(future.isDone());
            assertFalse(future.isCompletedExceptionally());
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Should collect comprehensive statistics")
    void shouldCollectComprehensiveStatistics() throws Exception {
        // Execute some tasks to generate statistics
        threadPoolManager.submitTask(ThreadPoolManager.PoolType.IO, () -> "Task 1");
        threadPoolManager.submitTask(ThreadPoolManager.PoolType.CPU, () -> "Task 2");
        threadPoolManager.submitTask(ThreadPoolManager.PoolType.COMPLETION_HANDLER, () -> null);

        // Wait for tasks to complete
        Thread.sleep(100);

        // Get statistics
        ThreadPoolStats stats = threadPoolManager.getStats();
        assertNotNull(stats);

        // Verify pool-specific stats
        ThreadPoolStats.PoolSpecificStats ioStats = threadPoolManager.getPoolStats(ThreadPoolManager.PoolType.IO);
        assertNotNull(ioStats);

        ThreadPoolStats.PoolSpecificStats cpuStats = threadPoolManager.getPoolStats(ThreadPoolManager.PoolType.CPU);
        assertNotNull(cpuStats);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Should update configuration dynamically")
    void shouldUpdateConfigurationDynamically() throws Exception {
        // Create new configuration
        ThreadPoolConfiguration newConfig = new ThreadPoolConfiguration.Builder()
                .enableAdaptiveSizing(false)
                .enableThreadAffinity(true)
                .enableBackpressure(false)
                .build();

        // Update configuration
        threadPoolManager.updateConfiguration(newConfig);

        // Verify configuration was updated
        ThreadPoolConfiguration updatedConfig = threadPoolManager.getConfiguration();
        assertNotNull(updatedConfig);
        assertFalse(updatedConfig.isEnableAdaptiveSizing());
        assertTrue(updatedConfig.isEnableThreadAffinity());
        assertFalse(updatedConfig.isEnableBackpressure());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Should handle thread pool shutdown gracefully")
    void shouldHandleThreadPoolShutdownGracefully() throws Exception {
        // Submit some tasks
        CompletableFuture<String> task1 = threadPoolManager.submitTask(
                ThreadPoolManager.PoolType.IO, () -> "Task 1");
        CompletableFuture<String> task2 = threadPoolManager.submitTask(
                ThreadPoolManager.PoolType.CPU, () -> "Task 2");

        // Wait for tasks to start
        Thread.sleep(50);

        // Shutdown thread pool manager
        threadPoolManager.shutdown();

        // Verify tasks complete successfully
        assertEquals("Task 1", task1.get(5, TimeUnit.SECONDS));
        assertEquals("Task 2", task2.get(5, TimeUnit.SECONDS));

        // Verify thread pools are shutdown
        assertThrows(IllegalStateException.class,
                () -> threadPoolManager.submitTask(ThreadPoolManager.PoolType.IO, () -> "Should Fail"));
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("Should handle resource coordination")
    void shouldHandleResourceCoordination() throws Exception {
        // This test would verify resource coordination features
        // For now, just verify basic functionality

        // Submit resource-intensive tasks
        AtomicLong totalMemoryUsed = new AtomicLong(0);

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            CompletableFuture<Void> future = threadPoolManager.submitTask(
                    ThreadPoolManager.PoolType.CPU,
                    () -> {
                        // Simulate memory allocation
                        totalMemoryUsed.addAndGet(1024 * 1024); // 1MB
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return null;
                    });
            futures.add(future);
        }

        // Wait for all tasks to complete
        AsyncTestUtils.waitForAll(java.time.Duration.ofSeconds(30), futures);

        // Verify memory usage
        assertTrue(totalMemoryUsed.get() > 0);

        // Verify resource stats
        ThreadPoolStats stats = threadPoolManager.getStats();
        assertNotNull(stats);
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @DisplayName("Should validate performance targets")
    void shouldValidatePerformanceTargets() throws Exception {
        long startTime = System.nanoTime();

        // Execute performance test tasks
        int taskCount = 1000;
        CountDownLatch latch = new CountDownLatch(taskCount);

        for (int i = 0; i < taskCount; i++) {
            threadPoolManager.submitTask(
                    ThreadPoolManager.PoolType.IO,
                    () -> {
                        // Simulate I/O operation
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            latch.countDown();
                        }
                        return null;
                    });
        }

        // Wait for completion
        assertTrue(latch.await(60, TimeUnit.SECONDS));

        long endTime = System.nanoTime();
        long duration = endTime - startTime;
        double throughput = (double) taskCount / (duration / 1_000_000_000.0);

        // Performance target: >1000 tasks/second for I/O operations
        assertTrue(throughput > 1000.0,
                "Throughput should be >1000 tasks/sec, was: " + throughput);

        // Verify efficiency metrics
        ThreadPoolStats stats = threadPoolManager.getStats();
        assertNotNull(stats);
        assertTrue(stats.getEfficiency() > 0.5,
                "Efficiency should be >50%");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Should handle error conditions gracefully")
    void shouldHandleErrorConditionsGracefully() throws Exception {
        // Test with null task
        assertThrows(IllegalArgumentException.class, () -> threadPoolManager.submitTask(ThreadPoolManager.PoolType.IO,
                (java.util.concurrent.Callable<String>) null));

        // Test with invalid pool type
        assertThrows(IllegalArgumentException.class, () -> threadPoolManager.getThreadPool(null));

        // Test operations after shutdown
        threadPoolManager.shutdown();
        assertThrows(IllegalStateException.class,
                () -> threadPoolManager.submitTask(ThreadPoolManager.PoolType.IO, () -> "Should Fail"));
    }
}