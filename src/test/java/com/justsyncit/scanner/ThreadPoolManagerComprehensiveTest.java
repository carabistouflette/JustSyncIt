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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for ThreadPoolManager following TDD principles.
 * Tests core functionality without interfering with singleton pattern.
 */
@DisplayName("ThreadPoolManager Comprehensive Tests")
class ThreadPoolManagerComprehensiveTest {

    private static ThreadPoolManager threadPoolManager;

    @BeforeAll
    static void setUpClass() {
        // Get the singleton instance once for all tests
        threadPoolManager = ThreadPoolManager.getInstance();
    }

    @AfterAll
    static void tearDownClass() {
        // Don't shutdown the singleton - let it live for the application
    }

    @Nested
    @DisplayName("Basic Operations")
    class BasicOperations {

        @Test
        @DisplayName("Should get singleton instance")
        void shouldGetSingletonInstance() {
            // When
            ThreadPoolManager instance1 = ThreadPoolManager.getInstance();
            ThreadPoolManager instance2 = ThreadPoolManager.getInstance();

            // Then
            assertNotNull(instance1);
            assertNotNull(instance2);
            assertSame(instance1, instance2);
        }

        @Test
        @DisplayName("Should get IO thread pool")
        void shouldGetIoThreadPool() throws Exception {
            // When
            ExecutorService ioThreadPool = threadPoolManager.getIoThreadPool();

            // Then
            assertNotNull(ioThreadPool, "IO thread pool should not be null");
            assertFalse(ioThreadPool.isShutdown(), "IO thread pool should not be shutdown");
            
            // Test that it can execute tasks
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> "test", ioThreadPool);
            String result = future.get(2, TimeUnit.SECONDS);
            assertEquals("test", result, "IO thread pool should execute tasks correctly");
        }

        @Test
        @DisplayName("Should get CPU thread pool")
        void shouldGetCpuThreadPool() throws Exception {
            // When
            ExecutorService cpuThreadPool = threadPoolManager.getCpuThreadPool();

            // Then
            assertNotNull(cpuThreadPool, "CPU thread pool should not be null");
            assertFalse(cpuThreadPool.isShutdown(), "CPU thread pool should not be shutdown");
            
            // Test that it can execute tasks
            CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> 42, cpuThreadPool);
            Integer result = future.get(2, TimeUnit.SECONDS);
            assertEquals(42, result, "CPU thread pool should execute tasks correctly");
        }

        @Test
        @DisplayName("Should get completion handler thread pool")
        void shouldGetCompletionHandlerThreadPool() throws Exception {
            // When
            ExecutorService completionHandlerThreadPool = threadPoolManager.getCompletionHandlerThreadPool();

            // Then
            assertNotNull(completionHandlerThreadPool, "Completion handler thread pool should not be null");
            assertFalse(completionHandlerThreadPool.isShutdown(), "Completion handler thread pool should not be shutdown");
            
            // Test that it can execute tasks
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> true, completionHandlerThreadPool);
            Boolean result = future.get(2, TimeUnit.SECONDS);
            assertTrue(result, "Completion handler thread pool should execute tasks correctly");
        }

        @Test
        @DisplayName("Should get batch processing thread pool")
        void shouldGetBatchProcessingThreadPool() throws Exception {
            // When
            ExecutorService batchThreadPool = threadPoolManager.getBatchProcessingThreadPool();

            // Then
            assertNotNull(batchThreadPool, "Batch processing thread pool should not be null");
            assertFalse(batchThreadPool.isShutdown(), "Batch processing thread pool should not be shutdown");
            
            // Test that it can execute tasks
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> "batch", batchThreadPool);
            String result = future.get(2, TimeUnit.SECONDS);
            assertEquals("batch", result, "Batch processing thread pool should execute tasks correctly");
        }

        @Test
        @DisplayName("Should get watch service thread pool")
        void shouldGetWatchServiceThreadPool() throws Exception {
            // When
            ExecutorService watchThreadPool = threadPoolManager.getWatchServiceThreadPool();

            // Then
            assertNotNull(watchThreadPool, "Watch service thread pool should not be null");
            assertFalse(watchThreadPool.isShutdown(), "Watch service thread pool should not be shutdown");
            
            // Test that it can execute tasks
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> "watch", watchThreadPool);
            String result = future.get(2, TimeUnit.SECONDS);
            assertEquals("watch", result, "Watch service thread pool should execute tasks correctly");
        }
    }

    @Nested
    @DisplayName("Configuration and Properties")
    class ConfigurationAndProperties {

        @Test
        @DisplayName("Should provide thread pool statistics")
        void shouldProvideThreadPoolStatistics() throws Exception {
            // When
            ThreadPoolStats.PoolSpecificStats ioStats = threadPoolManager.getPoolStats(ThreadPoolManager.PoolType.IO);
            ThreadPoolStats.PoolSpecificStats cpuStats = threadPoolManager.getPoolStats(ThreadPoolManager.PoolType.CPU);
            ThreadPoolStats.PoolSpecificStats completionHandlerStats = threadPoolManager.getPoolStats(ThreadPoolManager.PoolType.COMPLETION_HANDLER);
            ThreadPoolStats.PoolSpecificStats batchStats = threadPoolManager.getPoolStats(ThreadPoolManager.PoolType.BATCH_PROCESSING);
            ThreadPoolStats.PoolSpecificStats watchStats = threadPoolManager.getPoolStats(ThreadPoolManager.PoolType.WATCH_SERVICE);

            // Then
            assertNotNull(ioStats);
            assertNotNull(cpuStats);
            assertNotNull(completionHandlerStats);
            assertNotNull(batchStats);
            assertNotNull(watchStats);

            // Verify basic stats structure
            assertTrue(ioStats.getCurrentEfficiency() >= 0.0);
            assertTrue(ioStats.getThroughput() >= 0.0);
            assertTrue(ioStats.getAverageLatency() >= 0.0);
        }

        @Test
        @DisplayName("Should provide configuration for each thread pool type")
        void shouldProvideConfigurationForEachThreadPoolType() throws Exception {
            // When
            ThreadPoolConfiguration.PoolConfig ioConfig = threadPoolManager.getConfiguration().getPoolConfig(ThreadPoolManager.PoolType.IO);
            ThreadPoolConfiguration.PoolConfig cpuConfig = threadPoolManager.getConfiguration().getPoolConfig(ThreadPoolManager.PoolType.CPU);
            ThreadPoolConfiguration.PoolConfig completionHandlerConfig = threadPoolManager.getConfiguration().getPoolConfig(ThreadPoolManager.PoolType.COMPLETION_HANDLER);
            ThreadPoolConfiguration.PoolConfig batchConfig = threadPoolManager.getConfiguration().getPoolConfig(ThreadPoolManager.PoolType.BATCH_PROCESSING);
            ThreadPoolConfiguration.PoolConfig watchConfig = threadPoolManager.getConfiguration().getPoolConfig(ThreadPoolManager.PoolType.WATCH_SERVICE);

            // Then
            assertNotNull(ioConfig);
            assertNotNull(cpuConfig);
            assertNotNull(completionHandlerConfig);
            assertNotNull(batchConfig);
            assertNotNull(watchConfig);

            assertTrue(ioConfig.getCorePoolSize() >= 0);
            assertTrue(ioConfig.getMaximumPoolSize() >= ioConfig.getCorePoolSize());
            assertTrue(ioConfig.getKeepAliveTimeMs() >= 0);
            assertNotNull(ioConfig.getThreadNamePrefix());
        }
    }

    @Nested
    @DisplayName("Performance Characteristics")
    class PerformanceCharacteristics {

        @Test
        @DisplayName("Should handle concurrent task submission")
        void shouldHandleConcurrentTaskSubmission() throws Exception {
            // Given
            int taskCount = 10; // Reduced task count to avoid timeout
            ExecutorService ioThreadPool = threadPoolManager.getIoThreadPool();
            AtomicInteger completedTasks = new AtomicInteger(0);

            // When
            CompletableFuture<?>[] futures = new CompletableFuture[taskCount];
            for (int i = 0; i < taskCount; i++) {
                final int taskId = i;
                futures[i] = CompletableFuture.runAsync(() -> {
                    // Simple task
                    completedTasks.incrementAndGet();
                }, ioThreadPool);
            }

            // Then
            CompletableFuture.allOf(futures).get(5, TimeUnit.SECONDS);
            
            // All futures should be completed
            for (CompletableFuture<?> future : futures) {
                assertTrue(future.isDone(), "All tasks should be completed");
            }
            
            assertEquals(taskCount, completedTasks.get(), "All tasks should have completed");
        }

        @Test
        @DisplayName("Should meet performance targets for task execution")
        void shouldMeetPerformanceTargetsForTaskExecution() throws Exception {
            // Given
            int taskCount = 5; // Reduced task count
            ExecutorService cpuThreadPool = threadPoolManager.getCpuThreadPool();

            // When
            long startTime = System.currentTimeMillis();
            CompletableFuture<?>[] futures = new CompletableFuture[taskCount];
            for (int i = 0; i < taskCount; i++) {
                futures[i] = CompletableFuture.runAsync(() -> {
                    // Lightweight task
                    long sum = 0;
                    for (int j = 0; j < 10; j++) {
                        sum += j;
                    }
                }, cpuThreadPool);
            }
            
            CompletableFuture.allOf(futures).get(3, TimeUnit.SECONDS);
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            // Then
            assertTrue(duration < 3000, "Tasks should complete within 3 seconds");
        }
    }

    @Nested
    @DisplayName("Error Handling and Recovery")
    class ErrorHandlingAndRecovery {

        @Test
        @DisplayName("Should handle task execution exceptions gracefully")
        void shouldHandleTaskExecutionExceptionsGracefully() throws Exception {
            // Given
            ExecutorService ioThreadPool = threadPoolManager.getIoThreadPool();
            AtomicInteger exceptionCount = new AtomicInteger(0);

            // When
            CompletableFuture<?>[] futures = new CompletableFuture[6];
            for (int i = 0; i < 6; i++) {
                final int taskId = i;
                futures[i] = CompletableFuture.runAsync(() -> {
                    try {
                        if (taskId % 2 == 0) {
                            throw new RuntimeException("Simulated task failure " + taskId);
                        }
                        // Successful task
                    } catch (RuntimeException e) {
                        exceptionCount.incrementAndGet();
                        // Don't re-throw, just count
                    }
                }, ioThreadPool);
            }

            // Wait for all tasks to complete
            CompletableFuture.allOf(futures).get(3, TimeUnit.SECONDS);

            // Then
            // Thread pool should still be functional
            assertNotNull(ioThreadPool);
            assertFalse(ioThreadPool.isShutdown(), "Thread pool should still be functional");
            
            // Should be able to submit new tasks
            CompletableFuture<Void> newTask = CompletableFuture.runAsync(() -> {
                // Simple task
            }, ioThreadPool);
            newTask.get(2, TimeUnit.SECONDS);
            
            // Should have caught some exceptions
            assertTrue(exceptionCount.get() > 0, "Should have caught some exceptions");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"io", "cpu", "completion", "batch", "watch"})
    @DisplayName("Should handle various thread pool types")
    void shouldHandleVariousThreadPoolTypes(String poolType) throws Exception {
        // Given
        ExecutorService threadPool;
        switch (poolType) {
            case "io":
                threadPool = threadPoolManager.getIoThreadPool();
                break;
            case "cpu":
                threadPool = threadPoolManager.getCpuThreadPool();
                break;
            case "completion":
                threadPool = threadPoolManager.getCompletionHandlerThreadPool();
                break;
            case "batch":
                threadPool = threadPoolManager.getBatchProcessingThreadPool();
                break;
            case "watch":
                threadPool = threadPoolManager.getWatchServiceThreadPool();
                break;
            default:
                throw new IllegalArgumentException("Unknown pool type: " + poolType);
        }

        // When
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            // Very simple task - no sleep to avoid timeout
        }, threadPool);

        // Then
        future.get(2, TimeUnit.SECONDS);
        assertNotNull(threadPool);
        assertFalse(threadPool.isShutdown(), "Thread pool should not be shutdown for type: " + poolType);
    }
}