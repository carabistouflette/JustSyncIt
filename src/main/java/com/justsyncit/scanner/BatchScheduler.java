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

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Intelligent batch scheduler for optimizing file processing operations.
 * Provides priority-based scheduling, resource-aware coordination, and
 * adaptive batch sizing for optimal performance.
 */

/**
 * Intelligent batch scheduler for optimizing file processing operations.
 * Provides priority-based scheduling, resource-aware coordination, and
 * adaptive batch sizing for optimal performance.
 */

public class BatchScheduler {

    private static final Logger logger = LoggerFactory.getLogger(BatchScheduler.class);

    /** Default maximum concurrent batches. */
    private static final int DEFAULT_MAX_CONCURRENT_BATCHES = 5;
    /** Default scheduling interval in milliseconds. */
    private static final long DEFAULT_SCHEDULING_INTERVAL_MS = 100;
    /** Default maximum queue size. */
    private static final int DEFAULT_MAX_QUEUE_SIZE = 1000;

    /** Thread pool manager for resource coordination. */
    private final ThreadPoolManager threadPoolManager;

    /** Async batch processor for executing batches. */
    private final AsyncBatchProcessor asyncBatchProcessor;

    /** Configuration for batch scheduling. */
    private final BatchConfiguration configuration;

    /** Priority queue for batch operations. */
    private final PriorityBlockingQueue<ScheduledBatchOperation> batchQueue;

    /** Map of active batch operations. */
    private final Map<String, ScheduledBatchOperation> activeOperations;

    /** Semaphore for controlling concurrent batches. */
    private final Semaphore concurrentBatchSemaphore;

    /** Scheduled executor for batch scheduling. */
    private final ScheduledExecutorService schedulingExecutor;

    /** Whether the scheduler is running. */
    private final AtomicBoolean running;

    /** Current scheduling statistics. */
    private final SchedulingStatistics schedulingStats;

    /** Resource monitor for adaptive scheduling. */
    private final ResourceMonitor resourceMonitor;

    /** Lock for waiting on operations to complete. */
    private final java.util.concurrent.locks.ReentrantLock shutdownLock = new java.util.concurrent.locks.ReentrantLock();

    /** Condition for waiting on operations to complete. */
    private final java.util.concurrent.locks.Condition allOperationsCompleted = shutdownLock.newCondition();

    /**
     * Creates a new BatchScheduler with default configuration.
     *
     * @param threadPoolManager   thread pool manager for resource coordination
     * @param asyncBatchProcessor async batch processor for executing batches
     * @throws IllegalArgumentException if any parameter is null
     */
    public static BatchScheduler create(ThreadPoolManager threadPoolManager,
            AsyncBatchProcessor asyncBatchProcessor) {
        if (threadPoolManager == null) {
            throw new IllegalArgumentException("Thread pool manager cannot be null");
        }
        if (asyncBatchProcessor == null) {
            throw new IllegalArgumentException("Async batch processor cannot be null");
        }

        return new BatchScheduler(threadPoolManager, asyncBatchProcessor, new BatchConfiguration());
    }

    /**
     * Creates a new BatchScheduler with custom configuration.
     *
     * @param threadPoolManager   thread pool manager for resource coordination
     * @param asyncBatchProcessor async batch processor for executing batches
     * @param configuration       batch scheduling configuration
     * @throws IllegalArgumentException if any parameter is null
     */
    public static BatchScheduler create(ThreadPoolManager threadPoolManager,
            AsyncBatchProcessor asyncBatchProcessor,
            BatchConfiguration configuration) {
        if (threadPoolManager == null) {
            throw new IllegalArgumentException("Thread pool manager cannot be null");
        }
        if (asyncBatchProcessor == null) {
            throw new IllegalArgumentException("Async batch processor cannot be null");
        }
        if (configuration == null) {
            throw new IllegalArgumentException("Configuration cannot be null");
        }

        return new BatchScheduler(threadPoolManager, asyncBatchProcessor, configuration);
    }

    /**
     * Private constructor.
     */
    private BatchScheduler(ThreadPoolManager threadPoolManager,
            AsyncBatchProcessor asyncBatchProcessor,
            BatchConfiguration configuration) {
        this.threadPoolManager = threadPoolManager;
        this.asyncBatchProcessor = asyncBatchProcessor;
        this.configuration = new BatchConfiguration(configuration);
        this.batchQueue = new PriorityBlockingQueue<>(DEFAULT_MAX_QUEUE_SIZE,
                Comparator.comparing(ScheduledBatchOperation::getPriority).reversed()
                        .thenComparing(ScheduledBatchOperation::getSubmissionTime));
        this.activeOperations = new ConcurrentHashMap<>();
        this.concurrentBatchSemaphore = new Semaphore(configuration.getMaxConcurrentBatches());
        this.schedulingExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        this.running = new AtomicBoolean(false);
        this.schedulingStats = new SchedulingStatistics();
        this.resourceMonitor = new ResourceMonitor();

        logger.info("BatchScheduler initialized with config: {}", configuration);
    }

    /**
     * Starts the batch scheduler.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            logger.info("Starting batch scheduler...");

            // Start the scheduling loop
            schedulingExecutor.scheduleAtFixedRate(this::scheduleNextBatch,
                    0, DEFAULT_SCHEDULING_INTERVAL_MS, TimeUnit.MILLISECONDS);

            // Start resource monitoring
            resourceMonitor.start(schedulingExecutor);

            logger.info("Batch scheduler started successfully");
        }
    }

    /**
     * Stops the batch scheduler.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            logger.info("Stopping batch scheduler...");

            // Stop resource monitoring
            resourceMonitor.stop();

            // Wait for active operations to complete
            // Wait for active operations to complete
            shutdownLock.lock();
            try {
                while (!activeOperations.isEmpty()) {
                    try {
                        allOperationsCompleted.await(100, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } finally {
                shutdownLock.unlock();
            }

            logger.info("Batch scheduler stopped successfully");
        }
    }

    /**
     * Schedules a batch operation for processing.
     *
     * @param files   list of files to process
     * @param options batch processing options
     * @return operation ID for tracking
     */
    public String scheduleBatch(List<Path> files, BatchOptions options) {
        return scheduleBatch(files, options, BatchPriority.NORMAL);
    }

    /**
     * Schedules a batch operation with specific priority.
     *
     * @param files    list of files to process
     * @param options  batch processing options
     * @param priority operation priority
     * @return operation ID for tracking
     */
    public String scheduleBatch(List<Path> files, BatchOptions options, BatchPriority priority) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Files list cannot be null or empty");
        }
        if (options == null) {
            throw new IllegalArgumentException("Options cannot be null");
        }
        if (priority == null) {
            throw new IllegalArgumentException("Priority cannot be null");
        }

        String operationId = UUID.randomUUID().toString();
        ScheduledBatchOperation operation = new ScheduledBatchOperation(
                operationId, files, options, priority, Instant.now());

        if (batchQueue.offer(operation)) {
            schedulingStats.recordScheduledOperation(priority);
            logger.debug("Scheduled batch operation: id={}, files={}, priority={}",
                    operationId, files.size(), priority);
        } else {
            schedulingStats.recordRejectedOperation();
            throw new IllegalStateException("Batch queue is full, cannot schedule operation");
        }

        return operationId;
    }

    /**
     * Schedules multiple batch operations.
     *
     * @param batches list of file batches
     * @param options batch processing options
     * @return list of operation IDs
     */
    public List<String> scheduleBatches(List<List<Path>> batches, BatchOptions options) {
        List<String> operationIds = new ArrayList<>();

        for (List<Path> batch : batches) {
            String operationId = scheduleBatch(batch, options);
            operationIds.add(operationId);
        }

        return operationIds;
    }

    /**
     * Gets the status of a scheduled operation.
     *
     * @param operationId operation ID
     * @return operation status, or null if not found
     */
    public ScheduledBatchOperation getOperationStatus(String operationId) {
        return activeOperations.get(operationId);
    }

    /**
     * Cancels a scheduled operation.
     *
     * @param operationId operation ID
     * @return true if operation was cancelled, false if not found or already
     *         completed
     */
    public boolean cancelOperation(String operationId) {
        ScheduledBatchOperation operation = activeOperations.get(operationId);
        if (operation != null && operation.getStatus() == ScheduledBatchOperation.Status.PENDING) {
            operation.setStatus(ScheduledBatchOperation.Status.CANCELLED);
            activeOperations.remove(operationId);
            schedulingStats.recordCancelledOperation();
            logger.info("Cancelled batch operation: {}", operationId);
            return true;
        }
        return false;
    }

    /**
     * Gets scheduling statistics.
     *
     * @return scheduling statistics
     */
    public SchedulingStatistics getSchedulingStatistics() {
        return schedulingStats;
    }

    /**
     * Gets current resource utilization.
     *
     * @return resource utilization
     */
    public ResourceUtilization getCurrentResourceUtilization() {
        return resourceMonitor.getCurrentUtilization();
    }

    /**
     * Updates scheduler configuration.
     *
     * @param configuration new configuration
     */
    public void updateConfiguration(BatchConfiguration configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException("Configuration cannot be null");
        }

        // Configuration is final, create new instance if needed
        if (this.configuration != configuration) {
            throw new UnsupportedOperationException("Configuration cannot be updated after creation");
        }

        // Update semaphore permits if needed
        int currentPermits = concurrentBatchSemaphore.availablePermits();
        int targetPermits = configuration.getMaxConcurrentBatches();

        if (targetPermits != currentPermits) {
            concurrentBatchSemaphore.drainPermits();
            concurrentBatchSemaphore.release(targetPermits);
        }

        logger.info("Updated scheduler configuration: {}", configuration);
    }

    /**
     * Main scheduling loop.
     */
    private void scheduleNextBatch() {
        if (!running.get()) {
            return;
        }

        try {
            // Check if we can schedule more batches
            if (concurrentBatchSemaphore.tryAcquire()) {
                ScheduledBatchOperation operation = batchQueue.poll();

                if (operation != null) {
                    executeBatchOperation(operation);
                } else {
                    concurrentBatchSemaphore.release();
                }
            }
        } catch (Exception e) {
            logger.error("Error in scheduling loop", e);
        }
    }

    /**
     * Executes a batch operation.
     */
    private void executeBatchOperation(ScheduledBatchOperation operation) {
        operation.setStatus(ScheduledBatchOperation.Status.RUNNING);
        operation.setStartTime(Instant.now());
        activeOperations.put(operation.getOperationId(), operation);

        logger.debug("Executing batch operation: id={}, files={}",
                operation.getOperationId(), operation.getFiles().size());

        // Apply adaptive sizing if enabled
        List<Path> adaptedFiles = applyAdaptiveSizing(operation.getFiles(), operation.getOptions());

        // Execute the batch
        try {
            CompletableFuture<BatchResult> future = asyncBatchProcessor.processBatch(
                    adaptedFiles, operation.getOptions(), operation.getPriority());

            future.whenComplete((result, throwable) -> {
                handleBatchCompletion(operation, result, throwable);
            });
        } catch (Exception e) {
            logger.error("Synchronous error submitting batch operation", e);
            handleBatchCompletion(operation, null, e);
        }
    }

    /**
     * Handles batch operation completion.
     */
    private void handleBatchCompletion(ScheduledBatchOperation operation, BatchResult result, Throwable throwable) {
        try {
            operation.setEndTime(Instant.now());

            if (throwable != null) {
                operation.setStatus(ScheduledBatchOperation.Status.FAILED);
                operation.setError(throwable);
                schedulingStats.recordFailedOperation();
            } else if (result != null && result.isSuccess()) {
                operation.setStatus(ScheduledBatchOperation.Status.COMPLETED);
                operation.setResult(result);
                schedulingStats.recordCompletedOperation(operation.getPriority());
            } else {
                operation.setStatus(ScheduledBatchOperation.Status.FAILED);
                operation.setError(new RuntimeException("Batch processing failed"));
                schedulingStats.recordFailedOperation();
            }

            // Remove from active operations
            activeOperations.remove(operation.getOperationId());

            // Signal if empty
            if (activeOperations.isEmpty()) {
                shutdownLock.lock();
                try {
                    allOperationsCompleted.signalAll();
                } finally {
                    shutdownLock.unlock();
                }
            }

            // Release semaphore permit
            concurrentBatchSemaphore.release();

            logger.debug("Completed batch operation: id={}, status={}, duration={}ms",
                    operation.getOperationId(), operation.getStatus(),
                    operation.getProcessingTimeMs());

        } catch (Exception e) {
            logger.error("Error handling batch completion", e);
        }
    }

    /**
     * Applies adaptive sizing to files based on current system conditions.
     */
    private List<Path> applyAdaptiveSizing(List<Path> files, BatchOptions options) {
        if (!options.isAdaptiveSizing()) {
            return new ArrayList<>(files);
        }

        // Get current resource utilization
        ResourceUtilization utilization = resourceMonitor.getCurrentUtilization();

        // Calculate optimal batch size based on resources
        int optimalBatchSize = calculateOptimalBatchSize(files.size(), utilization, options);

        // Split files into optimal batches
        if (files.size() <= optimalBatchSize) {
            return new ArrayList<>(files);
        }

        List<Path> adaptedFiles = new ArrayList<>();
        int endIndex = Math.min(optimalBatchSize, files.size());
        adaptedFiles.addAll(files.subList(0, endIndex));

        // Re-queue remaining files
        if (endIndex < files.size()) {
            List<Path> remainingFiles = files.subList(endIndex, files.size());
            BatchOptions remainingOptions = new BatchOptions(options)
                    .withBatchSize(optimalBatchSize);

            batchQueue.offer(new ScheduledBatchOperation(
                    UUID.randomUUID().toString(), remainingFiles, remainingOptions,
                    BatchPriority.NORMAL, Instant.now()));
        }

        logger.debug("Applied adaptive sizing: original={}, adapted={}, requeued={}",
                files.size(), adaptedFiles.size(), files.size() - adaptedFiles.size());

        return adaptedFiles;
    }

    /**
     * Calculates optimal batch size based on resource utilization.
     */
    private int calculateOptimalBatchSize(int fileCount, ResourceUtilization utilization, BatchOptions options) {
        int baseBatchSize = options.getBatchSize();

        // Adjust based on CPU utilization
        if (utilization.cpuUtilizationPercent > 80.0) {
            baseBatchSize = Math.max(baseBatchSize / 2, configuration.getMinBatchSize());
        } else if (utilization.cpuUtilizationPercent < 40.0) {
            baseBatchSize = Math.min(baseBatchSize * 2, configuration.getMaxBatchSize());
        }

        // Adjust based on memory utilization
        if (utilization.memoryUtilizationPercent > 85.0) {
            baseBatchSize = Math.max(baseBatchSize / 3, configuration.getMinBatchSize());
        }

        // Adjust based on I/O utilization
        if (utilization.ioUtilizationPercent > 90.0) {
            baseBatchSize = Math.max(baseBatchSize / 2, configuration.getMinBatchSize());
        }

        return Math.min(baseBatchSize, fileCount);
    }

    /**
     * Scheduled batch operation representation.
     */
    public static class ScheduledBatchOperation {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        /** Operation status enumeration. */
        public enum Status {
            PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
        }

        private final String operationId;
        private final List<Path> files;
        private final BatchOptions options;
        private final BatchPriority priority;
        private final Instant submissionTime;

        private volatile Status status;
        private Instant startTime;
        private Instant endTime;
        private BatchResult result;
        private Throwable error;

        public ScheduledBatchOperation(String operationId, List<Path> files, BatchOptions options,
                BatchPriority priority, Instant submissionTime) {
            this.operationId = operationId;
            this.files = new ArrayList<>(files);
            this.options = new BatchOptions(options);
            this.priority = priority;
            this.submissionTime = submissionTime;
            this.status = Status.PENDING;
        }

        public String getOperationId() {
            return operationId;
        }

        public List<Path> getFiles() {
            return new ArrayList<>(files);
        }

        public BatchOptions getOptions() {
            return options;
        }

        public BatchPriority getPriority() {
            return priority;
        }

        public Instant getSubmissionTime() {
            return submissionTime;
        }

        public Status getStatus() {
            return status;
        }

        public void setStatus(Status status) {
            this.status = status;
        }

        public Instant getStartTime() {
            return startTime;
        }

        public void setStartTime(Instant startTime) {
            this.startTime = startTime;
        }

        public Instant getEndTime() {
            return endTime;
        }

        public void setEndTime(Instant endTime) {
            this.endTime = endTime;
        }

        public BatchResult getResult() {
            return result;
        }

        public void setResult(BatchResult result) {
            this.result = result;
        }

        public Throwable getError() {
            return error;
        }

        public void setError(Throwable error) {
            this.error = error;
        }

        public long getProcessingTimeMs() {
            if (startTime != null && endTime != null) {
                return java.time.Duration.between(startTime, endTime).toMillis();
            }
            return 0L;
        }

        public long getQueueTimeMs() {
            if (startTime != null) {
                return java.time.Duration.between(submissionTime, startTime).toMillis();
            }
            return 0L;
        }
    }

    /**
     * Scheduling statistics.
     */
    public static class SchedulingStatistics {
        private final AtomicLong totalScheduled = new AtomicLong(0);
        private final AtomicLong totalCompleted = new AtomicLong(0);
        private final AtomicLong totalFailed = new AtomicLong(0);
        private final AtomicLong totalCancelled = new AtomicLong(0);
        private final AtomicLong totalRejected = new AtomicLong(0);
        private final Map<BatchPriority, AtomicInteger> priorityStats = new ConcurrentHashMap<>();

        public SchedulingStatistics() {
            for (BatchPriority priority : BatchPriority.values()) {
                priorityStats.put(priority, new AtomicInteger(0));
            }
        }

        public void recordScheduledOperation(BatchPriority priority) {
            totalScheduled.incrementAndGet();
            priorityStats.get(priority).incrementAndGet();
        }

        public void recordCompletedOperation(BatchPriority priority) {
            totalCompleted.incrementAndGet();
        }

        public void recordFailedOperation() {
            totalFailed.incrementAndGet();
        }

        public void recordCancelledOperation() {
            totalCancelled.incrementAndGet();
        }

        public void recordRejectedOperation() {
            totalRejected.incrementAndGet();
        }

        public long getTotalScheduled() {
            return totalScheduled.get();
        }

        public long getTotalCompleted() {
            return totalCompleted.get();
        }

        public long getTotalFailed() {
            return totalFailed.get();
        }

        public long getTotalCancelled() {
            return totalCancelled.get();
        }

        public long getTotalRejected() {
            return totalRejected.get();
        }

        public int getPriorityCount(BatchPriority priority) {
            return priorityStats.get(priority).get();
        }

        public double getSuccessRate() {
            long total = totalCompleted.get() + totalFailed.get();
            return total > 0 ? (double) totalCompleted.get() / total * 100.0 : 0.0;
        }

        @Override
        public String toString() {
            return String.format(
                    "SchedulingStatistics{scheduled=%d, completed=%d, failed=%d, "
                            + "cancelled=%d, rejected=%d, successRate=%.1f%%}",
                    getTotalScheduled(), getTotalCompleted(), getTotalFailed(),
                    getTotalCancelled(), getTotalRejected(), getSuccessRate());
        }
    }

    /**
     * Resource monitor for adaptive scheduling.
     */
    private static class ResourceMonitor {
        private final AtomicReference<ResourceUtilization> currentUtilization = new AtomicReference<>(
                new ResourceUtilization(0.0, 0.0, 0.0, 0, 0L, 0L, 0L));
        private volatile java.util.concurrent.ScheduledFuture<?> monitorTask;

        public void start(ScheduledExecutorService executor) {
            if (monitorTask == null || monitorTask.isCancelled()) {
                // Schedule resource monitoring
                monitorTask = executor.scheduleAtFixedRate(this::monitorResources,
                        0, 1, TimeUnit.SECONDS);
            }
        }

        public void stop() {
            if (monitorTask != null) {
                monitorTask.cancel(false);
                monitorTask = null;
            }
        }

        public ResourceUtilization getCurrentUtilization() {
            return currentUtilization.get();
        }

        private void monitorResources() {
            try {
                // Collect resource utilization metrics
                Runtime runtime = Runtime.getRuntime();
                long maxMemory = runtime.maxMemory();
                long usedMemory = runtime.totalMemory() - runtime.freeMemory();
                double memoryUtilization = (double) usedMemory / maxMemory * 100.0;

                // Estimate CPU utilization (simplified)
                // Create a simple resource utilization object for monitoring
                double cpuUtilization = Math.min(90.0, 50.0); // Simplified estimate
                double ioUtilization = Math.min(95.0, 60.0); // Simplified estimate

                ResourceUtilization utilization = new ResourceUtilization(
                        cpuUtilization, memoryUtilization, ioUtilization,
                        1, usedMemory / (1024 * 1024), 0L, 0L);

                currentUtilization.set(utilization);

            } catch (Exception e) {
                logger.error("Error monitoring resources", e);
            }
        }
    }
}