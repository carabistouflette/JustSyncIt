package com.justsyncit.scanner;

import org.junit.jupiter.api.DisplayName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Provider for test configurations used in async testing.
 * Provides predefined and configurable test scenarios for various async components.
 */
@DisplayName("Async Test Configuration Provider")
public class AsyncTestConfigurationProvider {

    /**
     * Default configuration for buffer pool tests.
     */
    public static class BufferPoolTestConfig {
        private final int poolSize;
        private final int bufferSize;
        private final int maxConcurrentOperations;
        private final Duration operationTimeout;
        private final boolean enableMetrics;
        private final boolean enableDetailedLogging;

        public BufferPoolTestConfig(int poolSize, int bufferSize, int maxConcurrentOperations, 
                                   Duration operationTimeout, boolean enableMetrics, boolean enableDetailedLogging) {
            this.poolSize = poolSize;
            this.bufferSize = bufferSize;
            this.maxConcurrentOperations = maxConcurrentOperations;
            this.operationTimeout = operationTimeout;
            this.enableMetrics = enableMetrics;
            this.enableDetailedLogging = enableDetailedLogging;
        }

        public int getPoolSize() { return poolSize; }
        public int getBufferSize() { return bufferSize; }
        public int getMaxConcurrentOperations() { return maxConcurrentOperations; }
        public Duration getOperationTimeout() { return operationTimeout; }
        public boolean isEnableMetrics() { return enableMetrics; }
        public boolean isEnableDetailedLogging() { return enableDetailedLogging; }

        @Override
        public String toString() {
            return String.format("BufferPoolTestConfig{poolSize=%d, bufferSize=%d, maxConcurrent=%d, timeout=%s}",
                    poolSize, bufferSize, maxConcurrentOperations, operationTimeout);
        }
    }

    /**
     * Configuration for file chunking tests.
     */
    public static class FileChunkingTestConfig {
        private final long fileSize;
        private final int chunkSize;
        private final int expectedChunks;
        private final Duration operationTimeout;
        private final boolean enableAsyncIO;
        private final boolean enableMetrics;
        private final String contentPattern;

        public FileChunkingTestConfig(long fileSize, int chunkSize, int expectedChunks, 
                                    Duration operationTimeout, boolean enableAsyncIO, boolean enableMetrics, String contentPattern) {
            this.fileSize = fileSize;
            this.chunkSize = chunkSize;
            this.expectedChunks = expectedChunks;
            this.operationTimeout = operationTimeout;
            this.enableAsyncIO = enableAsyncIO;
            this.enableMetrics = enableMetrics;
            this.contentPattern = contentPattern;
        }

        public long getFileSize() { return fileSize; }
        public int getChunkSize() { return chunkSize; }
        public int getExpectedChunks() { return expectedChunks; }
        public Duration getOperationTimeout() { return operationTimeout; }
        public boolean isEnableAsyncIO() { return enableAsyncIO; }
        public boolean isEnableMetrics() { return enableMetrics; }
        public String getContentPattern() { return contentPattern; }

        @Override
        public String toString() {
            return String.format("FileChunkingTestConfig{fileSize=%d, chunkSize=%d, expectedChunks=%d, asyncIO=%s}",
                    fileSize, chunkSize, expectedChunks, enableAsyncIO);
        }
    }

    /**
     * Configuration for thread pool tests.
     */
    public static class ThreadPoolTestConfig {
        private final ThreadPoolManager.PoolType poolType;
        private final int taskCount;
        private final long taskDurationMs;
        private final Duration operationTimeout;
        private final boolean enableMetrics;
        private final Map<String, Object> poolProperties;

        public ThreadPoolTestConfig(ThreadPoolManager.PoolType poolType, int taskCount, long taskDurationMs,
                                   Duration operationTimeout, boolean enableMetrics, Map<String, Object> poolProperties) {
            this.poolType = poolType;
            this.taskCount = taskCount;
            this.taskDurationMs = taskDurationMs;
            this.operationTimeout = operationTimeout;
            this.enableMetrics = enableMetrics;
            this.poolProperties = new HashMap<>(poolProperties);
        }

        public ThreadPoolManager.PoolType getPoolType() { return poolType; }
        public int getTaskCount() { return taskCount; }
        public long getTaskDurationMs() { return taskDurationMs; }
        public Duration getOperationTimeout() { return operationTimeout; }
        public boolean isEnableMetrics() { return enableMetrics; }
        public Map<String, Object> getPoolProperties() { return new HashMap<>(poolProperties); }

        @Override
        public String toString() {
            return String.format("ThreadPoolTestConfig{type=%s, tasks=%d, duration=%dms}",
                    poolType.getName(), taskCount, taskDurationMs);
        }
    }

    /**
     * Configuration for performance tests.
     */
    public static class PerformanceTestConfig {
        private final String testName;
        private final int iterations;
        private final int concurrency;
        private final Duration warmupTime;
        private final Duration measurementTime;
        private final Map<String, Double> performanceThresholds;
        private final boolean enableProfiling;

        public PerformanceTestConfig(String testName, int iterations, int concurrency,
                                    Duration warmupTime, Duration measurementTime,
                                    Map<String, Double> performanceThresholds, boolean enableProfiling) {
            this.testName = testName;
            this.iterations = iterations;
            this.concurrency = concurrency;
            this.warmupTime = warmupTime;
            this.measurementTime = measurementTime;
            this.performanceThresholds = new HashMap<>(performanceThresholds);
            this.enableProfiling = enableProfiling;
        }

        public String getTestName() { return testName; }
        public int getIterations() { return iterations; }
        public int getConcurrency() { return concurrency; }
        public Duration getWarmupTime() { return warmupTime; }
        public Duration getMeasurementTime() { return measurementTime; }
        public Map<String, Double> getPerformanceThresholds() { return new HashMap<>(performanceThresholds); }
        public boolean isEnableProfiling() { return enableProfiling; }

        @Override
        public String toString() {
            return String.format("PerformanceTestConfig{name='%s', iterations=%d, concurrency=%d}",
                    testName, iterations, concurrency);
        }
    }

    // Predefined configurations

    /**
     * Gets default buffer pool test configurations.
     */
    public static List<BufferPoolTestConfig> getDefaultBufferPoolConfigs() {
        return Arrays.asList(
            new BufferPoolTestConfig(10, 8192, 5, Duration.ofSeconds(5), true, false),
            new BufferPoolTestConfig(50, 4096, 25, Duration.ofSeconds(10), true, false),
            new BufferPoolTestConfig(100, 16384, 50, Duration.ofSeconds(15), true, true),
            new BufferPoolTestConfig(5, 1024, 10, Duration.ofSeconds(3), false, false),
            new BufferPoolTestConfig(200, 32768, 100, Duration.ofSeconds(30), true, true)
        );
    }

    /**
     * Gets default file chunking test configurations.
     */
    public static List<FileChunkingTestConfig> getDefaultFileChunkingConfigs() {
        return Arrays.asList(
            new FileChunkingTestConfig(1024, 256, 4, Duration.ofSeconds(5), true, true, "RANDOM"),
            new FileChunkingTestConfig(4096, 1024, 4, Duration.ofSeconds(10), true, true, "SEQUENTIAL"),
            new FileChunkingTestConfig(65536, 8192, 8, Duration.ofSeconds(15), true, true, "MIXED"),
            new FileChunkingTestConfig(1048576, 16384, 64, Duration.ofSeconds(30), true, true, "RANDOM"),
            new FileChunkingTestConfig(0, 1024, 0, Duration.ofSeconds(1), true, true, "EMPTY"),
            new FileChunkingTestConfig(1, 1024, 1, Duration.ofSeconds(2), true, true, "SEQUENTIAL"),
            new FileChunkingTestConfig(10485760, 65536, 160, Duration.ofSeconds(60), true, true, "SPARSE")
        );
    }

    /**
     * Gets default thread pool test configurations.
     */
    public static List<ThreadPoolTestConfig> getDefaultThreadPoolConfigs() {
        return Arrays.asList(
            new ThreadPoolTestConfig(ThreadPoolManager.PoolType.IO, 10, 100, Duration.ofSeconds(10), true, Collections.emptyMap()),
            new ThreadPoolTestConfig(ThreadPoolManager.PoolType.CPU, 20, 50, Duration.ofSeconds(15), true, Collections.emptyMap()),
            new ThreadPoolTestConfig(ThreadPoolManager.PoolType.COMPLETION_HANDLER, 30, 25, Duration.ofSeconds(10), true, Collections.emptyMap()),
            new ThreadPoolTestConfig(ThreadPoolManager.PoolType.BATCH_PROCESSING, 15, 75, Duration.ofSeconds(20), true, Collections.emptyMap()),
            new ThreadPoolTestConfig(ThreadPoolManager.PoolType.WATCH_SERVICE, 5, 200, Duration.ofSeconds(25), true, Collections.emptyMap())
        );
    }

    /**
     * Gets default performance test configurations.
     */
    public static List<PerformanceTestConfig> getDefaultPerformanceConfigs() {
        Map<String, Double> throughputThreshold = new HashMap<>();
        throughputThreshold.put("minThroughputOpsPerSecond", 1000.0);
        throughputThreshold.put("maxLatencyMs", 10.0);
        throughputThreshold.put("maxErrorRate", 0.01);

        Map<String, Double> latencyThreshold = new HashMap<>();
        latencyThreshold.put("minThroughputOpsPerSecond", 500.0);
        latencyThreshold.put("maxLatencyMs", 5.0);
        latencyThreshold.put("maxErrorRate", 0.005);

        Map<String, Double> scalabilityThreshold = new HashMap<>();
        scalabilityThreshold.put("minThroughputOpsPerSecond", 2000.0);
        scalabilityThreshold.put("maxLatencyMs", 50.0);
        scalabilityThreshold.put("maxErrorRate", 0.02);

        return Arrays.asList(
            new PerformanceTestConfig("ThroughputTest", 1000, 10, Duration.ofSeconds(5), Duration.ofSeconds(30), throughputThreshold, false),
            new PerformanceTestConfig("LatencyTest", 500, 5, Duration.ofSeconds(3), Duration.ofSeconds(20), latencyThreshold, true),
            new PerformanceTestConfig("ScalabilityTest", 2000, 50, Duration.ofSeconds(10), Duration.ofSeconds(60), scalabilityThreshold, false),
            new PerformanceTestConfig("EnduranceTest", 10000, 20, Duration.ofSeconds(30), Duration.ofSeconds(300), throughputThreshold, false)
        );
    }

    /**
     * Gets configuration for stress testing.
     */
    public static class StressTestConfig {
        private final int maxConcurrentOperations;
        private final Duration testDuration;
        private final double resourceUtilizationTarget;
        private final boolean enableFailureInjection;
        private final double failureRate;
        private final Map<String, Object> stressParameters;

        public StressTestConfig(int maxConcurrentOperations, Duration testDuration, double resourceUtilizationTarget,
                               boolean enableFailureInjection, double failureRate, Map<String, Object> stressParameters) {
            this.maxConcurrentOperations = maxConcurrentOperations;
            this.testDuration = testDuration;
            this.resourceUtilizationTarget = resourceUtilizationTarget;
            this.enableFailureInjection = enableFailureInjection;
            this.failureRate = failureRate;
            this.stressParameters = new HashMap<>(stressParameters);
        }

        public int getMaxConcurrentOperations() { return maxConcurrentOperations; }
        public Duration getTestDuration() { return testDuration; }
        public double getResourceUtilizationTarget() { return resourceUtilizationTarget; }
        public boolean isEnableFailureInjection() { return enableFailureInjection; }
        public double getFailureRate() { return failureRate; }
        public Map<String, Object> getStressParameters() { return new HashMap<>(stressParameters); }

        @Override
        public String toString() {
            return String.format("StressTestConfig{maxConcurrent=%d, duration=%s, targetUtilization=%.2f}",
                    maxConcurrentOperations, testDuration, resourceUtilizationTarget);
        }
    }

    /**
     * Gets default stress test configurations.
     */
    public static List<StressTestConfig> getDefaultStressTestConfigs() {
        Map<String, Object> lightStressParams = new HashMap<>();
        lightStressParams.put("memoryPressure", "low");
        lightStressParams.put("cpuPressure", "medium");

        Map<String, Object> mediumStressParams = new HashMap<>();
        mediumStressParams.put("memoryPressure", "medium");
        mediumStressParams.put("cpuPressure", "high");

        Map<String, Object> heavyStressParams = new HashMap<>();
        heavyStressParams.put("memoryPressure", "high");
        heavyStressParams.put("cpuPressure", "maximum");

        return Arrays.asList(
            new StressTestConfig(50, Duration.ofMinutes(2), 0.7, false, 0.0, lightStressParams),
            new StressTestConfig(100, Duration.ofMinutes(5), 0.8, true, 0.05, mediumStressParams),
            new StressTestConfig(200, Duration.ofMinutes(10), 0.9, true, 0.1, heavyStressParams)
        );
    }

    /**
     * Gets configuration for integration tests.
     */
    public static class IntegrationTestConfig {
        private final String testName;
        private final List<String> componentsInvolved;
        private final Duration testTimeout;
        private final boolean enableCrossComponentMetrics;
        private final Map<String, Object> integrationParameters;

        public IntegrationTestConfig(String testName, List<String> componentsInvolved, Duration testTimeout,
                                    boolean enableCrossComponentMetrics, Map<String, Object> integrationParameters) {
            this.testName = testName;
            this.componentsInvolved = new ArrayList<>(componentsInvolved);
            this.testTimeout = testTimeout;
            this.enableCrossComponentMetrics = enableCrossComponentMetrics;
            this.integrationParameters = new HashMap<>(integrationParameters);
        }

        public String getTestName() { return testName; }
        public List<String> getComponentsInvolved() { return new ArrayList<>(componentsInvolved); }
        public Duration getTestTimeout() { return testTimeout; }
        public boolean isEnableCrossComponentMetrics() { return enableCrossComponentMetrics; }
        public Map<String, Object> getIntegrationParameters() { return new HashMap<>(integrationParameters); }

        @Override
        public String toString() {
            return String.format("IntegrationTestConfig{name='%s', components=%s}",
                    testName, componentsInvolved);
        }
    }

    /**
     * Gets default integration test configurations.
     */
    public static List<IntegrationTestConfig> getDefaultIntegrationTestConfigs() {
        Map<String, Object> chunkingScanParams = new HashMap<>();
        chunkingScanParams.put("fileCount", 100);
        chunkingScanParams.put("maxFileSize", 1048576);

        Map<String, Object> bufferPoolChunkingParams = new HashMap<>();
        bufferPoolChunkingParams.put("poolSize", 50);
        bufferPoolChunkingParams.put("concurrentOperations", 25);

        Map<String, Object> fullWorkflowParams = new HashMap<>();
        fullWorkflowParams.put("directoryDepth", 5);
        fullWorkflowParams.put("filesPerDirectory", 20);
        fullWorkflowParams.put("maxFileSize", 10485760);

        return Arrays.asList(
            new IntegrationTestConfig("ChunkingAndScanning", 
                Arrays.asList("AsyncFileChunker", "AsyncFilesystemScanner"), 
                Duration.ofMinutes(2), true, chunkingScanParams),
            new IntegrationTestConfig("BufferPoolAndChunking", 
                Arrays.asList("AsyncByteBufferPool", "AsyncFileChunker"), 
                Duration.ofMinutes(1), true, bufferPoolChunkingParams),
            new IntegrationTestConfig("FullWorkflow", 
                Arrays.asList("AsyncFilesystemScanner", "AsyncFileChunker", "AsyncByteBufferPool", "ThreadPoolManager"), 
                Duration.ofMinutes(5), true, fullWorkflowParams)
        );
    }

    /**
     * Creates a custom buffer pool configuration.
     */
    public static BufferPoolTestConfig createBufferPoolConfig(int poolSize, int bufferSize, int maxConcurrentOperations) {
        return new BufferPoolTestConfig(poolSize, bufferSize, maxConcurrentOperations, Duration.ofSeconds(10), true, false);
    }

    /**
     * Creates a custom file chunking configuration.
     */
    public static FileChunkingTestConfig createFileChunkingConfig(long fileSize, int chunkSize, boolean enableAsyncIO) {
        int expectedChunks = (int) Math.ceil((double) fileSize / chunkSize);
        return new FileChunkingTestConfig(fileSize, chunkSize, expectedChunks, Duration.ofSeconds(30), enableAsyncIO, true, "RANDOM");
    }

    /**
     * Creates a custom thread pool configuration.
     */
    public static ThreadPoolTestConfig createThreadPoolConfig(ThreadPoolManager.PoolType poolType, int taskCount, long taskDurationMs) {
        return new ThreadPoolTestConfig(poolType, taskCount, taskDurationMs, Duration.ofSeconds(30), true, Collections.emptyMap());
    }

    /**
     * Creates a custom performance configuration.
     */
    public static PerformanceTestConfig createPerformanceConfig(String testName, int iterations, int concurrency, 
                                                              Map<String, Double> thresholds) {
        return new PerformanceTestConfig(testName, iterations, concurrency, Duration.ofSeconds(5), Duration.ofSeconds(30), 
                                        thresholds, false);
    }
}