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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Batch-aware wrapper for ThreadPoolManager that integrates with the batch
 * processing system.
 * Provides optimized thread pool management for batch processing scenarios.
 * Enhances performance through batch coordination and resource optimization.
 */
public class BatchAwareThreadPoolManager {

    private static final Logger logger = LoggerFactory.getLogger(BatchAwareThreadPoolManager.class);

    private final ThreadPoolManager delegate;
    private final AsyncBatchProcessor batchProcessor;
    private final BatchConfiguration batchConfig;
    private final AtomicInteger activeBatchOperations;
    private final AtomicLong totalTasksSubmitted;
    private final AtomicLong totalTasksCompleted;

    /**
     * Creates a new BatchAwareThreadPoolManager.
     *
     * @param delegate       the underlying thread pool manager
     * @param batchProcessor the batch processor for coordination
     * @param batchConfig    the batch configuration
     * @throws IllegalArgumentException if any parameter is null
     */
    public BatchAwareThreadPoolManager(ThreadPoolManager delegate,
            AsyncBatchProcessor batchProcessor,
            BatchConfiguration batchConfig) {
        if (delegate == null) {
            throw new IllegalArgumentException("Delegate thread pool manager cannot be null");
        }
        if (batchProcessor == null) {
            throw new IllegalArgumentException("Batch processor cannot be null");
        }
        if (batchConfig == null) {
            throw new IllegalArgumentException("Batch configuration cannot be null");
        }

        this.delegate = delegate;
        this.batchProcessor = batchProcessor;
        this.batchConfig = batchConfig;
        this.activeBatchOperations = new AtomicInteger(0);
        this.totalTasksSubmitted = new AtomicLong(0);
        this.totalTasksCompleted = new AtomicLong(0);
    }

    // Delegate methods

    /**
     * Gets the current configuration.
     */
    public ThreadPoolConfiguration getConfiguration() {
        return delegate.getConfiguration();
    }

    /**
     * Gets the CPU thread pool.
     */
    public ExecutorService getCpuThreadPool() {
        return delegate.getCpuThreadPool();
    }

    /**
     * Gets the I/O thread pool.
     */
    public ExecutorService getIoThreadPool() {
        return delegate.getIoThreadPool();
    }

    /**
     * Gets the management thread pool.
     */
    public ExecutorService getManagementThreadPool() {
        return delegate.getManagementThreadPool();
    }

    /**
     * Gets the WatchService thread pool.
     */
    public ExecutorService getWatchServiceThreadPool() {
        return delegate.getWatchServiceThreadPool();
    }

    /**
     * Gets the CompletionHandler thread pool.
     */
    public ExecutorService getCompletionHandlerThreadPool() {
        return delegate.getCompletionHandlerThreadPool();
    }

    /**
     * Gets the batch processing thread pool.
     */
    public ExecutorService getBatchProcessingThreadPool() {
        return delegate.getBatchProcessingThreadPool();
    }

    /**
     * Gets thread pool for specified type.
     */
    public ExecutorService getThreadPool(ThreadPoolManager.PoolType type) {
        return delegate.getThreadPool(type);
    }

    // Enhanced batch-aware task submission methods

    /**
     * Submits a CPU task with batch processing integration.
     */
    public <T> CompletableFuture<T> submitCpuTask(Callable<T> task) {
        return submitCpuTask(task, null);
    }

    /**
     * Submits a CPU task with batch processing integration and completion handler.
     */
    public <T> CompletableFuture<T> submitCpuTask(Callable<T> task, CompletionHandler<T, Exception> handler) {
        if (task == null) {
            CompletableFuture<T> failed = CompletableFuture.failedFuture(
                    new IllegalArgumentException("Task cannot be null"));
            if (handler != null) {
                failed.whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        handler.failed(throwable instanceof Exception ? (Exception) throwable
                                : new RuntimeException(throwable));
                    }
                });
            }
            return failed;
        }

        totalTasksSubmitted.incrementAndGet();

        // For small tasks, use direct delegation to avoid batch overhead
        if (isSmallTask(task)) {
            return delegate.submitTask(ThreadPoolManager.PoolType.CPU, task)
                    .whenComplete((result, throwable) -> {
                        if (throwable == null) {
                            totalTasksCompleted.incrementAndGet();
                        }
                        if (handler != null) {
                            if (throwable != null) {
                                handler.failed(throwable instanceof Exception ? (Exception) throwable
                                        : new RuntimeException(throwable));
                            } else {
                                handler.completed(result);
                            }
                        }
                    });
        }

        // Create batch operation for CPU task
        BatchOperation operation = createCpuTaskOperation(task);

        // Submit to batch processor
        CompletableFuture<BatchOperationResult> batchFuture = batchProcessor.processOperation(operation,
                new BatchOptions());

        // Handle batch result
        CompletableFuture<T> result = batchFuture.thenCompose(batchResult -> {
            if (!batchResult.isSuccess()) {
                Exception error = batchResult.getError();
                return CompletableFuture.failedFuture(error != null ? error : new RuntimeException("CPU task failed"));
            }

            // Execute the actual task
            return delegate.submitTask(ThreadPoolManager.PoolType.CPU, task);
        });

        // Track completion
        result.whenComplete((taskResult, throwable) -> {
            if (throwable == null) {
                totalTasksCompleted.incrementAndGet();
            }
            if (handler != null) {
                if (throwable != null) {
                    handler.failed(
                            throwable instanceof Exception ? (Exception) throwable : new RuntimeException(throwable));
                } else {
                    handler.completed(taskResult);
                }
            }
        });

        return result;
    }

    /**
     * Submits an I/O task with batch processing integration.
     */
    public <T> CompletableFuture<T> submitIoTask(Callable<T> task) {
        return submitIoTask(task, null);
    }

    /**
     * Submits an I/O task with batch processing integration and completion handler.
     */
    public <T> CompletableFuture<T> submitIoTask(Callable<T> task, CompletionHandler<T, Exception> handler) {
        if (task == null) {
            CompletableFuture<T> failed = CompletableFuture.failedFuture(
                    new IllegalArgumentException("Task cannot be null"));
            if (handler != null) {
                failed.whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        handler.failed(throwable instanceof Exception ? (Exception) throwable
                                : new RuntimeException(throwable));
                    }
                });
            }
            return failed;
        }

        totalTasksSubmitted.incrementAndGet();

        // For small tasks, use direct delegation to avoid batch overhead
        if (isSmallTask(task)) {
            return delegate.submitTask(ThreadPoolManager.PoolType.IO, task)
                    .whenComplete((result, throwable) -> {
                        if (throwable == null) {
                            totalTasksCompleted.incrementAndGet();
                        }
                        if (handler != null) {
                            if (throwable != null) {
                                handler.failed(throwable instanceof Exception ? (Exception) throwable
                                        : new RuntimeException(throwable));
                            } else {
                                handler.completed(result);
                            }
                        }
                    });
        }

        // Create batch operation for I/O task
        BatchOperation operation = createIoTaskOperation(task);

        // Submit to batch processor
        CompletableFuture<BatchOperationResult> batchFuture = batchProcessor.processOperation(operation,
                new BatchOptions());

        // Handle batch result
        CompletableFuture<T> result = batchFuture.thenCompose(batchResult -> {
            if (!batchResult.isSuccess()) {
                Exception error = batchResult.getError();
                return CompletableFuture.failedFuture(error != null ? error : new RuntimeException("I/O task failed"));
            }

            // Execute the actual task
            return delegate.submitTask(ThreadPoolManager.PoolType.IO, task);
        });

        // Track completion
        result.whenComplete((taskResult, throwable) -> {
            if (throwable == null) {
                totalTasksCompleted.incrementAndGet();
            }
            if (handler != null) {
                if (throwable != null) {
                    handler.failed(
                            throwable instanceof Exception ? (Exception) throwable : new RuntimeException(throwable));
                } else {
                    handler.completed(taskResult);
                }
            }
        });

        return result;
    }

    /**
     * Submits a management task with batch processing integration.
     */
    public <T> CompletableFuture<T> submitManagementTask(Callable<T> task) {
        return submitManagementTask(task, null);
    }

    /**
     * Submits a management task with batch processing integration and completion
     * handler.
     */
    public <T> CompletableFuture<T> submitManagementTask(Callable<T> task, CompletionHandler<T, Exception> handler) {
        totalTasksSubmitted.incrementAndGet();
        return delegate.submitTask(ThreadPoolManager.PoolType.MANAGEMENT, task)
                .whenComplete((result, throwable) -> {
                    if (throwable == null) {
                        totalTasksCompleted.incrementAndGet();
                    }
                    if (handler != null) {
                        if (throwable != null) {
                            handler.failed(throwable instanceof Exception ? (Exception) throwable
                                    : new RuntimeException(throwable));
                        } else {
                            handler.completed(result);
                        }
                    }
                });
    }

    /**
     * Submits a batch processing task with batch processing integration.
     */
    public <T> CompletableFuture<T> submitBatchProcessingTask(Callable<T> task) {
        return submitBatchProcessingTask(task, null);
    }

    /**
     * Submits a batch processing task with batch processing integration and
     * completion handler.
     */
    public <T> CompletableFuture<T> submitBatchProcessingTask(Callable<T> task,
            CompletionHandler<T, Exception> handler) {
        if (task == null) {
            CompletableFuture<T> failed = CompletableFuture.failedFuture(
                    new IllegalArgumentException("Task cannot be null"));
            if (handler != null) {
                failed.whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        handler.failed(throwable instanceof Exception ? (Exception) throwable
                                : new RuntimeException(throwable));
                    }
                });
            }
            return failed;
        }

        totalTasksSubmitted.incrementAndGet();
        activeBatchOperations.incrementAndGet();

        // Create batch operation for batch processing task
        BatchOperation operation = createBatchProcessingTaskOperation(task);

        // Submit to batch processor
        CompletableFuture<BatchOperationResult> batchFuture = batchProcessor.processOperation(operation,
                new BatchOptions());

        // Handle batch result
        CompletableFuture<T> result = batchFuture.thenCompose(batchResult -> {
            if (!batchResult.isSuccess()) {
                Exception error = batchResult.getError();
                return CompletableFuture
                        .failedFuture(error != null ? error : new RuntimeException("Batch processing task failed"));
            }

            // Execute the actual task
            return delegate.submitTask(ThreadPoolManager.PoolType.BATCH_PROCESSING, task);
        });

        // Track completion
        result.whenComplete((taskResult, throwable) -> {
            activeBatchOperations.decrementAndGet();
            if (throwable == null) {
                totalTasksCompleted.incrementAndGet();
            }
            if (handler != null) {
                if (throwable != null) {
                    handler.failed(
                            throwable instanceof Exception ? (Exception) throwable : new RuntimeException(throwable));
                } else {
                    handler.completed(taskResult);
                }
            }
        });

        return result;
    }

    /**
     * Gets comprehensive statistics.
     */
    public ThreadPoolStats getStats() {
        return delegate.getStats();
    }

    /**
     * Gets comprehensive statistics asynchronously.
     */
    public CompletableFuture<String> getStatsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            ThreadPoolStats delegateStats = delegate.getStats();
            long submitted = totalTasksSubmitted.get();
            long completed = totalTasksCompleted.get();
            int active = activeBatchOperations.get();

            return String.format(
                    "BatchAwareThreadPoolManager Stats%n"
                            + "Delegate: %s%n"
                            + "Total Tasks Submitted: %d%n"
                            + "Total Tasks Completed: %d%n"
                            + "Active Batch Operations: %d%n"
                            + "Success Rate: %.2f%%",
                    delegateStats.toString(), submitted, completed, active,
                    submitted > 0 ? (completed * 100.0 / submitted) : 0.0);
        });
    }

    /**
     * Triggers adaptive resizing.
     */
    public void triggerAdaptiveResizing() {
        delegate.triggerAdaptiveResizing();
    }

    /**
     * Applies backpressure.
     */
    public void applyBackpressure(double pressureLevel) {
        delegate.applyBackpressure(pressureLevel);
    }

    /**
     * Releases backpressure.
     */
    public void releaseBackpressure() {
        delegate.releaseBackpressure();
    }

    /**
     * Updates configuration.
     */
    public void updateConfiguration(ThreadPoolConfiguration newConfig) {
        delegate.updateConfiguration(newConfig);
    }

    /**
     * Shuts down the thread pool manager.
     */
    public void shutdown() {
        activeBatchOperations.set(0);
        delegate.shutdown();
    }

    /**
     * Shuts down the thread pool manager immediately.
     */
    public void shutdownNow() {
        activeBatchOperations.set(0);
        delegate.shutdown();
    }

    /**
     * Checks if shutdown has been initiated.
     * Note: ThreadPoolManager doesn't have isShutdown() method, so we use a
     * workaround.
     */
    public boolean isShutdown() {
        // ThreadPoolManager doesn't expose shutdown state directly
        // This is a simplified implementation
        return false;
    }

    /**
     * Checks if all threads have terminated.
     * Note: ThreadPoolManager doesn't have isTerminated() method, so we use a
     * workaround.
     */
    public boolean isTerminated() {
        // ThreadPoolManager doesn't expose termination state directly
        // This is a simplified implementation
        return false;
    }

    /**
     * Waits for termination.
     * Note: ThreadPoolManager doesn't have awaitTermination() method, so we use a
     * workaround.
     */
    public boolean awaitTermination(long timeout) throws InterruptedException {
        // ThreadPoolManager doesn't expose awaitTermination directly
        // This is a simplified implementation that just waits
        Thread.sleep(timeout);
        return true;
    }

    // Batch processing methods

    /**
     * Submits multiple tasks as a batch operation.
     *
     * @param tasks list of tasks to submit
     * @param <T>   task result type
     * @return a CompletableFuture that completes with list of results
     */
    public <T> CompletableFuture<List<T>> submitBatchTasks(List<Callable<T>> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        // Create batch operation for multiple tasks
        BatchOperation operation = createBatchTasksOperation(tasks);

        // Submit to batch processor
        return batchProcessor.processOperation(operation, new BatchOptions())
                .thenCompose(batchResult -> {
                    if (!batchResult.isSuccess()) {
                        Exception error = batchResult.getError();
                        return CompletableFuture
                                .failedFuture(error != null ? error : new RuntimeException("Batch tasks failed"));
                    }

                    // Execute all tasks
                    return executeBatchTasks(tasks);
                });
    }

    // Helper methods

    /**
     * Determines if a task is small enough to bypass batch processing.
     */
    private boolean isSmallTask(Callable<?> task) {
        // Simple heuristic based on task class name
        String className = task.getClass().getSimpleName();
        return className.contains("Small") || className.contains("Quick") || className.contains("Fast");
    }

    /**
     * Creates a batch operation for CPU task.
     */
    private <T> BatchOperation createCpuTaskOperation(Callable<T> task) {
        BatchOperation.ResourceRequirements requirements = new BatchOperation.ResourceRequirements(
                1024 * 1024, // 1MB memory for CPU task
                1, // 1 CPU core
                5, // 5MB/s I/O bandwidth
                (long) batchConfig.getBatchTimeoutSeconds() * 1000 // timeout in ms
        );

        return new BatchOperation(
                "cpu-task-" + System.currentTimeMillis() + "-" + task.hashCode(),
                BatchOperationType.CHUNKING, // Use CHUNKING as generic CPU operation
                List.of(), // No files for task operations
                BatchPriority.NORMAL,
                requirements);
    }

    /**
     * Creates a batch operation for I/O task.
     */
    private <T> BatchOperation createIoTaskOperation(Callable<T> task) {
        BatchOperation.ResourceRequirements requirements = new BatchOperation.ResourceRequirements(
                2 * 1024 * 1024, // 2MB memory for I/O task
                1, // 1 CPU core
                50, // 50MB/s I/O bandwidth
                (long) batchConfig.getBatchTimeoutSeconds() * 1500 // 1.5x timeout for I/O
        );

        return new BatchOperation(
                "io-task-" + System.currentTimeMillis() + "-" + task.hashCode(),
                BatchOperationType.STORAGE, // Use STORAGE as generic I/O operation
                List.of(), // No files for task operations
                BatchPriority.HIGH, // I/O tasks are high priority
                requirements);
    }

    /**
     * Creates a batch operation for batch processing task.
     */
    private <T> BatchOperation createBatchProcessingTaskOperation(Callable<T> task) {
        BatchOperation.ResourceRequirements requirements = new BatchOperation.ResourceRequirements(
                4 * 1024 * 1024, // 4MB memory for batch processing task
                2, // 2 CPU cores for batch processing
                20, // 20MB/s I/O bandwidth
                (long) batchConfig.getBatchTimeoutSeconds() * 2000 // 2x timeout for batch processing
        );

        return new BatchOperation(
                "batch-task-" + System.currentTimeMillis() + "-" + task.hashCode(),
                BatchOperationType.HASHING, // Use HASHING as generic batch operation
                List.of(), // No files for task operations
                BatchPriority.HIGH, // Batch processing tasks are high priority
                requirements);
    }

    /**
     * Creates a batch operation for multiple tasks.
     */
    private <T> BatchOperation createBatchTasksOperation(List<Callable<T>> tasks) {
        BatchOperation.ResourceRequirements requirements = new BatchOperation.ResourceRequirements(
                (long) tasks.size() * 1024 * 1024, // 1MB per task
                Math.max(2, tasks.size() / 4), // Scale CPU cores with task count
                Math.max(10, tasks.size() * 5), // Scale I/O with task count
                (long) batchConfig.getBatchTimeoutSeconds() * 3000 // 3x timeout for multiple tasks
        );

        return new BatchOperation(
                "batch-tasks-" + System.currentTimeMillis() + "-" + tasks.size(),
                BatchOperationType.TRANSFER, // Use TRANSFER for multi-task operations
                List.of(), // No files for task operations
                BatchPriority.CRITICAL, // Multi-task operations are critical
                requirements);
    }

    /**
     * Executes multiple tasks and collects results.
     */
    private <T> CompletableFuture<List<T>> executeBatchTasks(List<Callable<T>> tasks) {
        List<CompletableFuture<T>> futures = tasks.stream()
                .map(task -> delegate.submitTask(ThreadPoolManager.PoolType.BATCH_PROCESSING, task))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    // Getters

    /**
     * Gets the delegate thread pool manager.
     */
    public ThreadPoolManager getDelegate() {
        return delegate;
    }

    /**
     * Gets the batch processor.
     */
    public AsyncBatchProcessor getBatchProcessor() {
        return batchProcessor;
    }

    /**
     * Gets the total number of tasks submitted.
     */
    public long getTotalTasksSubmitted() {
        return totalTasksSubmitted.get();
    }

    /**
     * Gets the total number of tasks completed.
     */
    public long getTotalTasksCompleted() {
        return totalTasksCompleted.get();
    }

    /**
     * Gets the number of active batch operations.
     */
    public int getActiveBatchOperations() {
        return activeBatchOperations.get();
    }
}