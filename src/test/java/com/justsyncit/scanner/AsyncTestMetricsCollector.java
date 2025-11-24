package com.justsyncit.scanner;

import org.junit.jupiter.api.DisplayName;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Metrics collector for async testing scenarios.
 * Provides comprehensive collection and analysis of test metrics including performance,
 * resource usage, and operation statistics.
 */
@DisplayName("Async Test Metrics Collector")
public class AsyncTestMetricsCollector {

    private final Map<String, ComponentMetrics> componentMetrics;
    private final Map<String, List<OperationTimer>> operationTimers;
    private final Map<String, PerformanceMetrics> performanceMetrics;
    private final AtomicReference<Instant> testStartTime;
    private final AtomicReference<Instant> testEndTime;
    private final AtomicLong totalOperations;
    private final AtomicLong successfulOperations;
    private final AtomicLong failedOperations;
    private final AtomicLong totalExecutionTimeMs;
    private final AtomicInteger activeOperations;

    public AsyncTestMetricsCollector() {
        this.componentMetrics = new ConcurrentHashMap<>();
        this.operationTimers = new ConcurrentHashMap<>();
        this.performanceMetrics = new ConcurrentHashMap<>();
        this.testStartTime = new AtomicReference<>();
        this.testEndTime = new AtomicReference<>();
        this.totalOperations = new AtomicLong(0);
        this.successfulOperations = new AtomicLong(0);
        this.failedOperations = new AtomicLong(0);
        this.totalExecutionTimeMs = new AtomicLong(0);
        this.activeOperations = new AtomicInteger(0);
    }

    /**
     * Starts collecting metrics for a test session.
     */
    public void startTestSession() {
        testStartTime.set(Instant.now());
        totalOperations.set(0);
        successfulOperations.set(0);
        failedOperations.set(0);
        totalExecutionTimeMs.set(0);
        activeOperations.set(0);
        componentMetrics.clear();
        operationTimers.clear();
        performanceMetrics.clear();
    }

    /**
     * Ends the current test session and finalizes metrics.
     */
    public void endTestSession() {
        testEndTime.set(Instant.now());
    }

    /**
     * Creates a new operation timer for measuring operation duration.
     *
     * @param operationName the name of the operation
     * @param componentName the name of the component being tested
     * @return a new OperationTimer instance
     */
    public OperationTimer startOperation(String operationName, String componentName) {
        totalOperations.incrementAndGet();
        activeOperations.incrementAndGet();
        
        OperationTimer timer = new OperationTimer(operationName, componentName, this);
        operationTimers.computeIfAbsent(componentName, k -> new ArrayList<>()).add(timer);
        
        return timer;
    }

    /**
     * Records the completion of an operation.
     *
     * @param timer the operation timer
     * @param bytesProcessed the number of bytes processed (optional)
     */
    void recordOperationCompletion(OperationTimer timer, Long bytesProcessed) {
        activeOperations.decrementAndGet();
        
        Duration duration = timer.getDuration();
        totalExecutionTimeMs.addAndGet(duration.toMillis());
        
        if (timer.isSuccess()) {
            successfulOperations.incrementAndGet();
        } else {
            failedOperations.incrementAndGet();
        }
        
        // Update component metrics
        ComponentMetrics metrics = componentMetrics.computeIfAbsent(timer.getComponentName(), 
            k -> new ComponentMetrics(timer.getComponentName()));
        
        metrics.recordOperation(timer, bytesProcessed);
    }

    /**
     * Records performance metrics for a component.
     *
     * @param componentName the name of the component
     * @param metrics the performance metrics
     */
    public void recordPerformanceMetrics(String componentName, PerformanceMetrics metrics) {
        performanceMetrics.put(componentName, metrics);
    }

    /**
     * Gets metrics for a specific component.
     *
     * @param componentName the name of the component
     * @return the component metrics, or null if not found
     */
    public ComponentMetrics getComponentMetrics(String componentName) {
        return componentMetrics.get(componentName);
    }

    /**
     * Gets performance metrics for a specific component.
     *
     * @param componentName the name of the component
     * @return the performance metrics, or null if not found
     */
    public PerformanceMetrics getPerformanceMetrics(String componentName) {
        return performanceMetrics.get(componentName);
    }

    /**
     * Gets operation metrics for a specific operation.
     *
     * @param operationName name of operation
     * @return operation metrics, or null if not found
     */
    public ComponentMetrics getOperationMetrics(String operationName) {
        return componentMetrics.get(operationName);
    }

    /**
     * Gets resource metrics for a specific test.
     *
     * @param testName name of test
     * @return resource metrics, or null if not found
     */
    public PerformanceMetrics getResourceMetrics(String testName) {
        return performanceMetrics.get(testName);
    }

    /**
     * Gets the total test session duration.
     *
     * @return the total test duration
     */
    public Duration getTotalTestDuration() {
        Instant start = testStartTime.get();
        Instant end = testEndTime.get();
        
        if (start == null) {
            return Duration.ZERO;
        }
        
        if (end == null) {
            return Duration.between(start, Instant.now());
        }
        
        return Duration.between(start, end);
    }

    /**
     * Gets the total number of operations executed.
     *
     * @return total operations count
     */
    public long getTotalOperations() {
        return totalOperations.get();
    }

    /**
     * Gets the number of successful operations.
     *
     * @return successful operations count
     */
    public long getSuccessfulOperations() {
        return successfulOperations.get();
    }

    /**
     * Gets the number of failed operations.
     *
     * @return failed operations count
     */
    public long getFailedOperations() {
        return failedOperations.get();
    }

    /**
     * Gets the current number of active operations.
     *
     * @return active operations count
     */
    public int getActiveOperations() {
        return activeOperations.get();
    }

    /**
     * Gets the success rate as a percentage.
     *
     * @return success rate (0.0 to 100.0)
     */
    public double getSuccessRate() {
        long total = totalOperations.get();
        if (total == 0) {
            return 0.0;
        }
        return (successfulOperations.get() * 100.0) / total;
    }

    /**
     * Gets the average operation execution time in milliseconds.
     *
     * @return average execution time
     */
    public double getAverageExecutionTimeMs() {
        long total = totalOperations.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) totalExecutionTimeMs.get() / total;
    }

    /**
     * Gets the throughput in operations per second.
     *
     * @return throughput (operations/second)
     */
    public double getThroughputPerSecond() {
        Duration duration = getTotalTestDuration();
        if (duration.isZero() || duration.isNegative()) {
            return 0.0;
        }
        
        long total = totalOperations.get();
        double durationSeconds = duration.toMillis() / 1000.0;
        return total / durationSeconds;
    }

    /**
     * Generates a comprehensive metrics report.
     *
     * @return a formatted metrics report
     */
    public String generateReport() {
        StringBuilder report = new StringBuilder();
        
        report.append("=== Async Test Metrics Report ===\n");
        report.append(String.format("Test Duration: %s\n", getTotalTestDuration()));
        report.append(String.format("Total Operations: %d\n", getTotalOperations()));
        report.append(String.format("Successful Operations: %d\n", getSuccessfulOperations()));
        report.append(String.format("Failed Operations: %d\n", getFailedOperations()));
        report.append(String.format("Success Rate: %.2f%%\n", getSuccessRate()));
        report.append(String.format("Average Execution Time: %.2f ms\n", getAverageExecutionTimeMs()));
        report.append(String.format("Throughput: %.2f ops/sec\n", getThroughputPerSecond()));
        report.append(String.format("Active Operations: %d\n", getActiveOperations()));
        
        report.append("\n=== Component Metrics ===\n");
        for (Map.Entry<String, ComponentMetrics> entry : componentMetrics.entrySet()) {
            report.append(entry.getValue().toString()).append("\n");
        }
        
        report.append("\n=== Performance Metrics ===\n");
        for (Map.Entry<String, PerformanceMetrics> entry : performanceMetrics.entrySet()) {
            report.append(entry.getValue().toString()).append("\n");
        }
        
        return report.toString();
    }

    /**
     * Resets all collected metrics.
     */
    public void reset() {
        componentMetrics.clear();
        operationTimers.clear();
        performanceMetrics.clear();
        testStartTime.set(null);
        testEndTime.set(null);
        totalOperations.set(0);
        successfulOperations.set(0);
        failedOperations.set(0);
        totalExecutionTimeMs.set(0);
        activeOperations.set(0);
    }

    /**
     * Timer for measuring individual operation duration and success.
     */
    public static class OperationTimer {
        private final String operationName;
        private final String componentName;
        private final AsyncTestMetricsCollector collector;
        private final Instant startTime;
        private final AtomicReference<Instant> endTime;
        private final AtomicReference<Exception> exception;
        private volatile boolean completed;

        OperationTimer(String operationName, String componentName, AsyncTestMetricsCollector collector) {
            this.operationName = operationName;
            this.componentName = componentName;
            this.collector = collector;
            this.startTime = Instant.now();
            this.endTime = new AtomicReference<>();
            this.exception = new AtomicReference<>();
            this.completed = false;
        }

        /**
         * Completes the operation timer successfully.
         *
         * @param bytesProcessed the number of bytes processed (optional)
         */
        public void complete(Long bytesProcessed) {
            synchronized (this) {
                if (!completed) {
                    completed = true;
                endTime.set(Instant.now());
                collector.recordOperationCompletion(this, bytesProcessed);
                }
            }
        }

        /**
         * Completes the operation timer with a failure.
         *
         * @param exception the exception that caused the failure
         */
        public void completeWithFailure(Exception exception) {
            synchronized (this) {
                if (!completed) {
                    completed = true;
                this.exception.set(exception);
                endTime.set(Instant.now());
                collector.recordOperationCompletion(this, null);
                }
            }
        }

        /**
         * Gets the operation duration.
         *
         * @return the duration of the operation
         */
        public Duration getDuration() {
            Instant end = endTime.get();
            if (end == null) {
                return Duration.between(startTime, Instant.now());
            }
            return Duration.between(startTime, end);
        }

        /**
         * Checks if the operation completed successfully.
         *
         * @return true if successful, false otherwise
         */
        public boolean isSuccess() {
            return exception.get() == null;
        }

        /**
         * Gets the exception that caused the operation to fail.
         *
         * @return the exception, or null if successful
         */
        public Exception getException() {
            return exception.get();
        }

        /**
         * Gets the operation name.
         *
         * @return the operation name
         */
        public String getOperationName() {
            return operationName;
        }

        /**
         * Gets the component name.
         *
         * @return the component name
         */
        public String getComponentName() {
            return componentName;
        }

        @Override
        public String toString() {
            return String.format("OperationTimer{operation='%s', component='%s', duration=%s, success=%s}",
                    operationName, componentName, getDuration(), isSuccess());
        }
    }

    /**
     * Metrics for a specific component.
     */
    public static class ComponentMetrics {
        private final String componentName;
        private final AtomicLong totalOperations;
        private final AtomicLong successfulOperations;
        private final AtomicLong failedOperations;
        private final AtomicLong totalBytesProcessed;
        private final AtomicLong totalExecutionTimeMs;
        private final AtomicLong minExecutionTimeMs;
        private final AtomicLong maxExecutionTimeMs;

        ComponentMetrics(String componentName) {
            this.componentName = componentName;
            this.totalOperations = new AtomicLong(0);
            this.successfulOperations = new AtomicLong(0);
            this.failedOperations = new AtomicLong(0);
            this.totalBytesProcessed = new AtomicLong(0);
            this.totalExecutionTimeMs = new AtomicLong(0);
            this.minExecutionTimeMs = new AtomicLong(Long.MAX_VALUE);
            this.maxExecutionTimeMs = new AtomicLong(0);
        }

        void recordOperation(OperationTimer timer, Long bytesProcessed) {
            totalOperations.incrementAndGet();
            
            if (timer.isSuccess()) {
                successfulOperations.incrementAndGet();
            } else {
                failedOperations.incrementAndGet();
            }
            
            if (bytesProcessed != null) {
                totalBytesProcessed.addAndGet(bytesProcessed);
            }
            
            long executionTimeMs = timer.getDuration().toMillis();
            totalExecutionTimeMs.addAndGet(executionTimeMs);
            
            // Update min/max execution times
            long currentMin = minExecutionTimeMs.get();
            while (executionTimeMs < currentMin && !minExecutionTimeMs.compareAndSet(currentMin, executionTimeMs)) {
                currentMin = minExecutionTimeMs.get();
            }
            
            long currentMax = maxExecutionTimeMs.get();
            while (executionTimeMs > currentMax && !maxExecutionTimeMs.compareAndSet(currentMax, executionTimeMs)) {
                currentMax = maxExecutionTimeMs.get();
            }
        }

        public String getComponentName() { return componentName; }
        public long getTotalOperations() { return totalOperations.get(); }
        public long getSuccessfulOperations() { return successfulOperations.get(); }
        public long getFailedOperations() { return failedOperations.get(); }
        public long getTotalBytesProcessed() { return totalBytesProcessed.get(); }
        public double getAverageExecutionTimeMs() {
            long total = totalOperations.get();
            return total == 0 ? 0.0 : (double) totalExecutionTimeMs.get() / total;
        }
        public long getMinExecutionTimeMs() { 
            long min = minExecutionTimeMs.get();
            return min == Long.MAX_VALUE ? 0 : min;
        }
        public long getMaxExecutionTimeMs() { return maxExecutionTimeMs.get(); }

        @Override
        public String toString() {
            return String.format("ComponentMetrics{name='%s', operations=%d, success=%d, failed=%d, bytes=%d, avgTime=%.2fms, minTime=%dms, maxTime=%dms}",
                    componentName, getTotalOperations(), getSuccessfulOperations(), getFailedOperations(),
                    getTotalBytesProcessed(), getAverageExecutionTimeMs(), getMinExecutionTimeMs(), getMaxExecutionTimeMs());
        }
    }

    /**
     * Performance metrics for a component.
     */
    public static class PerformanceMetrics {
        private final double throughputOpsPerSecond;
        private final double averageLatencyMs;
        private final double p95LatencyMs;
        private final double p99LatencyMs;
        private final double errorRate;
        private final double cpuUtilizationPercent;
        private final double memoryUtilizationPercent;

        public PerformanceMetrics(double throughputOpsPerSecond, double averageLatencyMs, 
                                double p95LatencyMs, double p99LatencyMs, double errorRate,
                                double cpuUtilizationPercent, double memoryUtilizationPercent) {
            this.throughputOpsPerSecond = throughputOpsPerSecond;
            this.averageLatencyMs = averageLatencyMs;
            this.p95LatencyMs = p95LatencyMs;
            this.p99LatencyMs = p99LatencyMs;
            this.errorRate = errorRate;
            this.cpuUtilizationPercent = cpuUtilizationPercent;
            this.memoryUtilizationPercent = memoryUtilizationPercent;
        }

        public double getThroughputOpsPerSecond() { return throughputOpsPerSecond; }
        public double getAverageLatencyMs() { return averageLatencyMs; }
        public double getP95LatencyMs() { return p95LatencyMs; }
        public double getP99LatencyMs() { return p99LatencyMs; }
        public double getErrorRate() { return errorRate; }
        public double getCpuUtilizationPercent() { return cpuUtilizationPercent; }
        public double getMemoryUtilizationPercent() { return memoryUtilizationPercent; }

        @Override
        public String toString() {
            return String.format("PerformanceMetrics{throughput=%.2f ops/s, avgLatency=%.2fms, p95Latency=%.2fms, p99Latency=%.2fms, errorRate=%.3f, cpu=%.1f%%, memory=%.1f%%}",
                    throughputOpsPerSecond, averageLatencyMs, p95LatencyMs, p99LatencyMs, errorRate, cpuUtilizationPercent, memoryUtilizationPercent);
        }
    }

    /**
     * Operation metrics for tracking specific operations.
     */
    public static class OperationMetrics {
        private final String operationName;
        private final long totalOperations;
        private final long successfulOperations;
        private final long failedOperations;
        private final double averageExecutionTimeMs;
        private final double throughputPerSecond;

        public OperationMetrics(String operationName, long totalOperations, long successfulOperations,
                           long failedOperations, double averageExecutionTimeMs, double throughputPerSecond) {
            this.operationName = operationName;
            this.totalOperations = totalOperations;
            this.successfulOperations = successfulOperations;
            this.failedOperations = failedOperations;
            this.averageExecutionTimeMs = averageExecutionTimeMs;
            this.throughputPerSecond = throughputPerSecond;
        }

        public String getOperationName() { return operationName; }
        public long getTotalOperations() { return totalOperations; }
        public long getSuccessfulOperations() { return successfulOperations; }
        public long getFailedOperations() { return failedOperations; }
        public double getAverageExecutionTimeMs() { return averageExecutionTimeMs; }
        public double getThroughputPerSecond() { return throughputPerSecond; }

        @Override
        public String toString() {
            return String.format("OperationMetrics{name='%s', total=%d, success=%d, failed=%d, avgTime=%.2fms, throughput=%.2f ops/s}",
                    operationName, totalOperations, successfulOperations, failedOperations, averageExecutionTimeMs, throughputPerSecond);
        }
    }

    /**
     * Resource metrics for tracking resource usage.
     */
    public static class ResourceMetrics {
        private final String resourceName;
        private final double cpuUtilizationPercent;
        private final double memoryUtilizationPercent;
        private final long totalBytesAllocated;
        private final long peakMemoryUsage;

        public ResourceMetrics(String resourceName, double cpuUtilizationPercent, double memoryUtilizationPercent,
                           long totalBytesAllocated, long peakMemoryUsage) {
            this.resourceName = resourceName;
            this.cpuUtilizationPercent = cpuUtilizationPercent;
            this.memoryUtilizationPercent = memoryUtilizationPercent;
            this.totalBytesAllocated = totalBytesAllocated;
            this.peakMemoryUsage = peakMemoryUsage;
        }

        public String getResourceName() { return resourceName; }
        public double getCpuUtilizationPercent() { return cpuUtilizationPercent; }
        public double getMemoryUtilizationPercent() { return memoryUtilizationPercent; }
        public long getTotalBytesAllocated() { return totalBytesAllocated; }
        public long getPeakMemoryUsage() { return peakMemoryUsage; }

        @Override
        public String toString() {
            return String.format("ResourceMetrics{name='%s', cpu=%.1f%%, memory=%.1f%%, bytes=%d, peakMemory=%d}",
                    resourceName, cpuUtilizationPercent, memoryUtilizationPercent, totalBytesAllocated, peakMemoryUsage);
        }
    }
}