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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.justsyncit.scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Comprehensive test suite for async directory scanning operations.
 * Validates performance targets, stress testing, and various scenarios.
 */
public class AsyncScannerTestSuite {

    private static final Logger logger = LoggerFactory.getLogger(AsyncScannerTestSuite.class);

    /** Test configuration. */
    private final TestConfiguration config;

    /** Test results tracking. */
    private final TestResults results;

    /**
     * Configuration for test scenarios.
     */
    public static class TestConfiguration {
        private int maxDirectories = 1000;
        private int maxFilesPerDirectory = 100;
        private int maxFileSizeBytes = 10 * 1024 * 1024; // 10MB
        private int maxDepth = 10;
        private long performanceTargetThroughputFilesPerSec = 1000;
        private long performanceTargetLatencyMs = 100;
        private double performanceTargetMemoryEfficiency = 0.8; // 80%
        private int stressTestDurationMinutes = 5;
        private int concurrentOperations = 10;
        private boolean enablePerformanceTests = true;
        private boolean enableStressTests = true;
        private boolean enableIntegrationTests = true;
        private Path testDirectory = Paths.get("test-scanner-data");

        // Builder pattern methods
        public TestConfiguration withMaxDirectories(int max) {
            this.maxDirectories = Math.max(1, max);
            return this;
        }

        public TestConfiguration withMaxFilesPerDirectory(int max) {
            this.maxFilesPerDirectory = Math.max(1, max);
            return this;
        }

        public TestConfiguration withMaxFileSizeBytes(int max) {
            this.maxFileSizeBytes = Math.max(1024, max);
            return this;
        }

        public TestConfiguration withMaxDepth(int max) {
            this.maxDepth = Math.max(1, max);
            return this;
        }

        public TestConfiguration withPerformanceTargets(long throughputMs, long latencyMs, double memoryEfficiency) {
            this.performanceTargetThroughputFilesPerSec = throughputMs;
            this.performanceTargetLatencyMs = latencyMs;
            this.performanceTargetMemoryEfficiency = memoryEfficiency;
            return this;
        }

        public TestConfiguration withStressTestDuration(int minutes) {
            this.stressTestDurationMinutes = Math.max(1, minutes);
            return this;
        }

        public TestConfiguration withConcurrentOperations(int count) {
            this.concurrentOperations = Math.max(1, count);
            return this;
        }

        public TestConfiguration withTestTypes(boolean performance, boolean stress, boolean integration) {
            this.enablePerformanceTests = performance;
            this.enableStressTests = stress;
            this.enableIntegrationTests = integration;
            return this;
        }

        public TestConfiguration withTestDirectory(Path directory) {
            this.testDirectory = directory;
            return this;
        }

        // Getters
        public int getMaxDirectories() {
            return maxDirectories;
        }

        public int getMaxFilesPerDirectory() {
            return maxFilesPerDirectory;
        }

        public int getMaxFileSizeBytes() {
            return maxFileSizeBytes;
        }

        public int getMaxDepth() {
            return maxDepth;
        }

        public long getPerformanceTargetThroughputFilesPerSec() {
            return performanceTargetThroughputFilesPerSec;
        }

        public long getPerformanceTargetLatencyMs() {
            return performanceTargetLatencyMs;
        }

        public double getPerformanceTargetMemoryEfficiency() {
            return performanceTargetMemoryEfficiency;
        }

        public int getStressTestDurationMinutes() {
            return stressTestDurationMinutes;
        }

        public int getConcurrentOperations() {
            return concurrentOperations;
        }

        public boolean isPerformanceTestsEnabled() {
            return enablePerformanceTests;
        }

        public boolean isStressTestsEnabled() {
            return enableStressTests;
        }

        public boolean isIntegrationTestsEnabled() {
            return enableIntegrationTests;
        }

        public Path getTestDirectory() {
            return testDirectory;
        }
    }

    /**
     * Test results tracking.
     */
    public static class TestResults {
        public final AtomicInteger totalTests = new AtomicInteger(0);
        public final AtomicInteger passedTests = new AtomicInteger(0);
        public final AtomicInteger failedTests = new AtomicInteger(0);
        public final Map<String, PerformanceMetric> performanceMetrics = new ConcurrentHashMap<>();
        public final List<String> errors = new ArrayList<>();
        public final AtomicLong totalTestDurationMs = new AtomicLong(0);

        public void recordTest(String testName, boolean passed, String errorMessage) {
            totalTests.incrementAndGet();
            if (passed) {
                passedTests.incrementAndGet();
            } else {
                failedTests.incrementAndGet();
                if (errorMessage != null) {
                    errors.add(testName + ": " + errorMessage);
                }
            }
        }

        public void recordPerformanceMetric(String testName, PerformanceMetric metric) {
            performanceMetrics.put(testName, metric);
        }

        public double getSuccessRate() {
            int total = totalTests.get();
            return total > 0 ? (double) passedTests.get() / total : 0.0;
        }

        public TestSummary getSummary() {
            return new TestSummary(
                    totalTests.get(),
                    passedTests.get(),
                    failedTests.get(),
                    getSuccessRate(),
                    new ArrayList<>(errors),
                    new ConcurrentHashMap<>(performanceMetrics),
                    totalTestDurationMs.get());
        }

        @Override
        public String toString() {
            return String.format(
                    "TestResults{total=%d, passed=%d, failed=%d, successRate=%.2f%%, errors=%d}",
                    totalTests.get(), passedTests.get(), failedTests.get(),
                    getSuccessRate() * 100, errors.size());
        }
    }

    /**
     * Performance metric for test validation.
     */
    public static class PerformanceMetric {
        public final long throughputFilesPerSec;
        public final long averageLatencyMs;
        public final double memoryEfficiency;
        public final long peakMemoryUsageBytes;
        public final long totalFilesProcessed;
        public final long totalDurationMs;

        public PerformanceMetric(long throughput, long latency, double memoryEfficiency,
                long peakMemory, long totalFiles, long duration) {
            this.throughputFilesPerSec = throughput;
            this.averageLatencyMs = latency;
            this.memoryEfficiency = memoryEfficiency;
            this.peakMemoryUsageBytes = peakMemory;
            this.totalFilesProcessed = totalFiles;
            this.totalDurationMs = duration;
        }

        public boolean meetsTargets(TestConfiguration config) {
            return throughputFilesPerSec >= config.getPerformanceTargetThroughputFilesPerSec() &&
                    averageLatencyMs <= config.getPerformanceTargetLatencyMs() &&
                    memoryEfficiency >= config.getPerformanceTargetMemoryEfficiency();
        }

        @Override
        public String toString() {
            return String.format(
                    "PerformanceMetric{throughput=%d files/sec, latency=%dms, memoryEff=%.2f%%, peakMemory=%dMB, totalFiles=%d, duration=%dms}",
                    throughputFilesPerSec, averageLatencyMs, memoryEfficiency * 100,
                    peakMemoryUsageBytes / (1024 * 1024), totalFilesProcessed, totalDurationMs);
        }
    }

    /**
     * Summary of test execution.
     */
    public static class TestSummary {
        public final int totalTests;
        public final int passedTests;
        public final int failedTests;
        public final double successRate;
        public final List<String> errors;
        public final Map<String, PerformanceMetric> performanceMetrics;
        public final long totalDurationMs;

        public TestSummary(int total, int passed, int failed, double successRate,
                List<String> errors, Map<String, PerformanceMetric> metrics, long duration) {
            this.totalTests = total;
            this.passedTests = passed;
            this.failedTests = failed;
            this.successRate = successRate;
            this.errors = errors;
            this.performanceMetrics = metrics;
            this.totalDurationMs = duration;
        }

        public boolean isOverallSuccess() {
            return successRate >= 0.95; // 95% success rate required
        }

        public boolean meetsPerformanceTargets() {
            return performanceMetrics.values().stream()
                    .allMatch(metric -> metric.meetsTargets(new TestConfiguration()));
        }

        @Override
        public String toString() {
            return String.format(
                    "TestSummary{total=%d, passed=%d, failed=%d, successRate=%.2f%%, errors=%d, duration=%dms}",
                    totalTests, passedTests, failedTests, successRate * 100, errors.size(), totalDurationMs);
        }
    }

    /**
     * Creates a new AsyncScannerTestSuite with default configuration.
     */
    public AsyncScannerTestSuite() {
        this(new TestConfiguration());
    }

    /**
     * Creates a new AsyncScannerTestSuite with custom configuration.
     *
     * @param config test configuration
     */
    public AsyncScannerTestSuite(TestConfiguration config) {
        this.config = config;
        this.results = new TestResults();
    }

    /**
     * Runs the complete test suite.
     *
     * @return test summary
     */
    public CompletableFuture<TestSummary> runTestSuite() {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();

            try {
                logger.info("Starting async scanner test suite");

                // Setup test environment
                setupTestEnvironment();

                // Run performance tests
                if (config.isPerformanceTestsEnabled()) {
                    runPerformanceTests();
                }

                // Run stress tests
                if (config.isStressTestsEnabled()) {
                    runStressTests();
                }

                // Run integration tests
                if (config.isIntegrationTestsEnabled()) {
                    runIntegrationTests();
                }

                long endTime = System.currentTimeMillis();
                results.totalTestDurationMs.set(endTime - startTime);

                TestSummary summary = results.getSummary();
                logger.info("Test suite completed: {}", summary);

                return summary;

            } catch (Exception e) {
                logger.error("Test suite failed", e);
                results.recordTest("TestSuite", false, e.getMessage());
                return results.getSummary();
            } finally {
                cleanupTestEnvironment();
            }
        });
    }

    /**
     * Sets up the test environment.
     */
    private void setupTestEnvironment() {
        try {
            logger.info("Setting up test environment");

            // Create test directory
            Files.createDirectories(config.getTestDirectory());

            // Generate test data
            generateTestData();

            logger.info("Test environment setup completed");

        } catch (Exception e) {
            logger.error("Failed to setup test environment", e);
            throw new RuntimeException("Test setup failed", e);
        }
    }

    /**
     * Generates test data for scanning.
     */
    private void generateTestData() {
        Random random = new Random(12345); // Fixed seed for reproducible tests

        try {
            for (int dirIndex = 0; dirIndex < config.getMaxDirectories(); dirIndex++) {
                Path dirPath = config.getTestDirectory().resolve("dir-" + dirIndex);
                Files.createDirectories(dirPath);

                int filesInDir = random.nextInt(config.getMaxFilesPerDirectory()) + 1;
                for (int fileIndex = 0; fileIndex < filesInDir; fileIndex++) {
                    Path filePath = dirPath.resolve("file-" + fileIndex + ".txt");
                    int fileSize = random.nextInt(config.getMaxFileSizeBytes()) + 1;

                    // Generate file content
                    byte[] content = generateFileContent(fileSize, random);
                    Files.write(filePath, content);
                }
            }

            logger.info("Generated test data: {} directories with varying file counts", config.getMaxDirectories());

        } catch (IOException e) {
            logger.error("Failed to generate test data", e);
            throw new RuntimeException("Test data generation failed", e);
        }
    }

    /**
     * Generates file content for testing.
     */
    private byte[] generateFileContent(int size, Random random) {
        byte[] content = new byte[size];
        random.nextBytes(content);
        return content;
    }

    /**
     * Runs performance tests.
     */
    private void runPerformanceTests() {
        logger.info("Running performance tests");

        // Test 1: Basic scanning performance
        runBasicScanningPerformanceTest();

        // Test 2: Parallel scanning performance
        runParallelScanningPerformanceTest();

        // Test 3: Large directory scanning
        runLargeDirectoryScanningTest();

        // Test 4: Memory efficiency test
        runMemoryEfficiencyTest();

        logger.info("Performance tests completed");
    }

    /**
     * Runs basic scanning performance test.
     */
    private void runBasicScanningPerformanceTest() {
        String testName = "BasicScanningPerformance";
        try {
            AsyncScannerIntegration integration = createTestScanner();
            Path testDir = config.getTestDirectory().resolve("dir-0");

            long startTime = System.nanoTime();
            AsyncScanResult result = integration.getAsyncScanner()
                    .scanDirectoryAsync(testDir, new AsyncScanOptions())
                    .get(30, TimeUnit.SECONDS);
            long endTime = System.nanoTime();

            long durationMs = (endTime - startTime) / 1_000_000;
            long throughput = result.getScannedFileCount() * 1000 / Math.max(1, durationMs);
            long avgLatency = durationMs / Math.max(1, result.getScannedFileCount());

            PerformanceMetric metric = new PerformanceMetric(
                    throughput, avgLatency, calculateMemoryEfficiency(result),
                    result.getPeakMemoryUsage(), result.getScannedFileCount(), durationMs);

            results.recordPerformanceMetric(testName, metric);
            boolean passed = metric.meetsTargets(config);
            results.recordTest(testName, passed, passed ? null : "Performance targets not met");

            integration.shutdownAsync().get(10, TimeUnit.SECONDS);

        } catch (Exception e) {
            results.recordTest(testName, false, e.getMessage());
        }
    }

    /**
     * Runs parallel scanning performance test.
     */
    private void runParallelScanningPerformanceTest() {
        String testName = "ParallelScanningPerformance";
        try {
            AsyncScannerIntegration integration = createTestScanner();
            AsyncScanOptions options = new AsyncScanOptions()
                    .withParallelism(config.getConcurrentOperations());

            long startTime = System.nanoTime();
            List<CompletableFuture<AsyncScanResult>> futures = IntStream.range(0, config.getConcurrentOperations())
                    .mapToObj(i -> {
                        Path testDir = config.getTestDirectory().resolve("dir-" + (i % config.getMaxDirectories()));
                        return integration.getAsyncScanner().scanDirectoryAsync(testDir, options);
                    })
                    .collect(Collectors.toList());

            CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
                    .get(60, TimeUnit.SECONDS);
            long endTime = System.nanoTime();

            long durationMs = (endTime - startTime) / 1_000_000;
            int totalFiles = futures.stream()
                    .mapToInt(future -> {
                        try {
                            return future.get().getScannedFileCount();
                        } catch (Exception e) {
                            return 0;
                        }
                    })
                    .sum();

            long throughput = totalFiles * 1000 / Math.max(1, durationMs);
            long avgLatency = durationMs / Math.max(1, config.getConcurrentOperations());

            PerformanceMetric metric = new PerformanceMetric(
                    throughput, avgLatency, calculateMemoryEfficiency(null),
                    0, totalFiles, durationMs);

            results.recordPerformanceMetric(testName, metric);
            boolean passed = metric.meetsTargets(config);
            results.recordTest(testName, passed, passed ? null : "Parallel performance targets not met");

            integration.shutdownAsync().get(10, TimeUnit.SECONDS);

        } catch (Exception e) {
            results.recordTest(testName, false, e.getMessage());
        }
    }

    /**
     * Runs large directory scanning test.
     */
    private void runLargeDirectoryScanningTest() {
        String testName = "LargeDirectoryScanning";
        try {
            AsyncScannerIntegration integration = createTestScanner();
            Path largeDir = config.getTestDirectory().resolve("dir-" + (config.getMaxDirectories() - 1));

            long startTime = System.nanoTime();
            AsyncScanResult result = integration.getAsyncScanner()
                    .scanDirectoryAsync(largeDir, new AsyncScanOptions().withStreamingEnabled(true))
                    .get(60, TimeUnit.SECONDS);
            long endTime = System.nanoTime();

            long durationMs = (endTime - startTime) / 1_000_000;
            long throughput = result.getScannedFileCount() * 1000 / Math.max(1, durationMs);

            PerformanceMetric metric = new PerformanceMetric(
                    throughput, durationMs, calculateMemoryEfficiency(result),
                    result.getPeakMemoryUsage(), result.getScannedFileCount(), durationMs);

            results.recordPerformanceMetric(testName, metric);
            boolean passed = metric.meetsTargets(config);
            results.recordTest(testName, passed, passed ? null : "Large directory performance targets not met");

            integration.shutdownAsync().get(10, TimeUnit.SECONDS);

        } catch (Exception e) {
            results.recordTest(testName, false, e.getMessage());
        }
    }

    /**
     * Runs memory efficiency test.
     */
    private void runMemoryEfficiencyTest() {
        String testName = "MemoryEfficiency";
        try {
            Runtime runtime = Runtime.getRuntime();
            long initialMemory = runtime.totalMemory() - runtime.freeMemory();

            AsyncScannerIntegration integration = createTestScanner();
            Path testDir = config.getTestDirectory().resolve("dir-0");

            AsyncScanResult result = integration.getAsyncScanner()
                    .scanDirectoryAsync(testDir, new AsyncScanOptions())
                    .get(30, TimeUnit.SECONDS);

            long peakMemory = result.getPeakMemoryUsage();
            long finalMemory = runtime.totalMemory() - runtime.freeMemory();
            long memoryUsed = finalMemory - initialMemory;
            double memoryEfficiency = peakMemory > 0 ? (double) memoryUsed / peakMemory : 0.0;

            PerformanceMetric metric = new PerformanceMetric(
                    result.getScannedFileCount(), 0, memoryEfficiency,
                    peakMemory, result.getScannedFileCount(), 0);

            results.recordPerformanceMetric(testName, metric);
            boolean passed = metric.meetsTargets(config);
            results.recordTest(testName, passed, passed ? null : "Memory efficiency targets not met");

            integration.shutdownAsync().get(10, TimeUnit.SECONDS);

        } catch (Exception e) {
            results.recordTest(testName, false, e.getMessage());
        }
    }

    /**
     * Runs stress tests.
     */
    private void runStressTests() {
        logger.info("Running stress tests");

        // Test 1: High concurrency stress
        runHighConcurrencyStressTest();

        // Test 2: Memory pressure stress
        runMemoryPressureStressTest();

        // Test 3: Long duration stress
        runLongDurationStressTest();

        logger.info("Stress tests completed");
    }

    /**
     * Runs high concurrency stress test.
     */
    private void runHighConcurrencyStressTest() {
        String testName = "HighConcurrencyStress";
        try {
            AsyncScannerIntegration integration = createTestScanner();
            int highConcurrency = config.getConcurrentOperations() * 4;
            AsyncScanOptions options = new AsyncScanOptions()
                    .withParallelism(highConcurrency);

            long startTime = System.currentTimeMillis();
            List<CompletableFuture<Void>> futures = IntStream.range(0, highConcurrency)
                    .mapToObj(i -> {
                        Path testDir = config.getTestDirectory().resolve("dir-" + (i % config.getMaxDirectories()));
                        return CompletableFuture.runAsync(() -> {
                            try {
                                integration.getAsyncScanner().scanDirectoryAsync(testDir, options)
                                        .get(config.getStressTestDurationMinutes(), TimeUnit.MINUTES);
                            } catch (Exception e) {
                                logger.warn("Stress test operation failed", e);
                            }
                        });
                    })
                    .collect(Collectors.toList());

            CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
                    .get(config.getStressTestDurationMinutes() + 5, TimeUnit.MINUTES);
            long endTime = System.currentTimeMillis();

            long durationMs = endTime - startTime;
            boolean passed = durationMs <= (config.getStressTestDurationMinutes() + 2) * 60 * 1000; // Allow 2 minute
                                                                                                    // buffer

            results.recordTest(testName, passed, passed ? null : "High concurrency stress test failed");

            integration.shutdownAsync().get(10, TimeUnit.SECONDS);

        } catch (Exception e) {
            results.recordTest(testName, false, e.getMessage());
        }
    }

    /**
     * Runs memory pressure stress test.
     */
    private void runMemoryPressureStressTest() {
        String testName = "MemoryPressureStress";
        try {
            AsyncScannerIntegration integration = createTestScanner();
            AsyncScanOptions options = new AsyncScanOptions()
                    .withBatchSize(1000) // Large batch size to increase memory pressure
                    .withParallelism(config.getConcurrentOperations() * 2);

            long startTime = System.currentTimeMillis();

            // Run multiple concurrent scans to create memory pressure
            List<CompletableFuture<AsyncScanResult>> futures = IntStream.range(0, config.getConcurrentOperations())
                    .mapToObj(i -> {
                        Path testDir = config.getTestDirectory().resolve("dir-" + (i % config.getMaxDirectories()));
                        return integration.getAsyncScanner().scanDirectoryAsync(testDir, options);
                    })
                    .collect(Collectors.toList());

            CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
                    .get(config.getStressTestDurationMinutes(), TimeUnit.MINUTES);
            long endTime = System.currentTimeMillis();

            long durationMs = endTime - startTime;
            boolean passed = durationMs <= (config.getStressTestDurationMinutes() + 1) * 60 * 1000;

            results.recordTest(testName, passed, passed ? null : "Memory pressure stress test failed");

            integration.shutdownAsync().get(10, TimeUnit.SECONDS);

        } catch (Exception e) {
            results.recordTest(testName, false, e.getMessage());
        }
    }

    /**
     * Runs long duration stress test.
     */
    private void runLongDurationStressTest() {
        String testName = "LongDurationStress";
        try {
            AsyncScannerIntegration integration = createTestScanner();
            Path testDir = config.getTestDirectory().resolve("dir-0");

            long startTime = System.currentTimeMillis();

            // Run continuous scanning for the specified duration
            long endTime = startTime + config.getStressTestDurationMinutes() * 60 * 1000;
            int scanCount = 0;

            while (System.currentTimeMillis() < endTime) {
                try {
                    integration.getAsyncScanner().scanDirectoryAsync(testDir, new AsyncScanOptions())
                            .get(1, TimeUnit.MINUTES);
                    scanCount++;
                } catch (Exception e) {
                    logger.warn("Long duration scan failed", e);
                }
            }

            boolean passed = scanCount >= config.getStressTestDurationMinutes();

            results.recordTest(testName, passed, passed ? null : "Long duration stress test failed");

            integration.shutdownAsync().get(10, TimeUnit.SECONDS);

        } catch (Exception e) {
            results.recordTest(testName, false, e.getMessage());
        }
    }

    /**
     * Runs integration tests.
     */
    private void runIntegrationTests() {
        logger.info("Running integration tests");

        // Test 1: WatchService integration
        runWatchServiceIntegrationTest();

        // Test 2: Event processing integration
        runEventProcessingIntegrationTest();

        // Test 3: Configuration integration
        runConfigurationIntegrationTest();

        logger.info("Integration tests completed");
    }

    /**
     * Runs WatchService integration test.
     */
    private void runWatchServiceIntegrationTest() {
        String testName = "WatchServiceIntegration";
        try {
            AsyncScannerIntegration integration = createTestScanner();
            Path testDir = config.getTestDirectory().resolve("dir-0");

            // Start monitoring
            CompletableFuture<WatchServiceRegistration> registrationFuture = integration.startMonitoringAsync(
                    testDir, new AsyncScanOptions().withWatchServiceEnabled(true));

            WatchServiceRegistration registration = registrationFuture.get(10, TimeUnit.SECONDS);

            // Modify a file to trigger events
            Path testFile = testDir.resolve("test-watch.txt");
            Files.write(testFile, "test content".getBytes());

            // Wait for events
            Thread.sleep(2000);

            // Delete the file
            Files.delete(testFile);

            // Wait for deletion events
            Thread.sleep(2000);

            // Stop monitoring
            registration.stopAsync().get(10, TimeUnit.SECONDS);

            results.recordTest(testName, true, null);

            integration.shutdownAsync().get(10, TimeUnit.SECONDS);

        } catch (Exception e) {
            results.recordTest(testName, false, e.getMessage());
        }
    }

    /**
     * Runs event processing integration test.
     */
    private void runEventProcessingIntegrationTest() {
        String testName = "EventProcessingIntegration";
        try {
            AsyncScannerIntegration integration = createTestScanner();
            AsyncFileEventProcessor eventProcessor = integration.getEventProcessor();

            if (eventProcessor != null) {
                // Create test events
                List<FileChangeEvent> testEvents = new ArrayList<>();
                Path testFile = config.getTestDirectory().resolve("dir-0").resolve("test-event.txt");

                testEvents.add(new FileChangeEvent(
                        FileChangeEvent.EventType.ENTRY_CREATE,
                        testFile,
                        "test-registration"));

                // Process events
                CompletableFuture<Void> processingFuture = eventProcessor.processEventsAsync(testEvents);
                processingFuture.get(10, TimeUnit.SECONDS);

                results.recordTest(testName, true, null);
            } else {
                results.recordTest(testName, false, "Event processor not available");
            }

            integration.shutdownAsync().get(10, TimeUnit.SECONDS);

        } catch (Exception e) {
            results.recordTest(testName, false, e.getMessage());
        }
    }

    /**
     * Runs configuration integration test.
     */
    private void runConfigurationIntegrationTest() {
        String testName = "ConfigurationIntegration";
        try {
            AsyncScannerConfiguration scannerConfig = new AsyncScannerConfiguration();

            // Test configuration validation
            boolean configValid = scannerConfig.validateConfiguration();

            // Test profile switching
            boolean profileSwitched = scannerConfig.setActiveProfile("high-performance");

            // Test runtime overrides
            AsyncScanOptions baseOptions = new AsyncScanOptions();
            AsyncScanOptions overriddenOptions = scannerConfig.applyRuntimeOverrides(baseOptions);

            boolean passed = configValid && profileSwitched && overriddenOptions != null;

            results.recordTest(testName, passed, passed ? null : "Configuration integration failed");

        } catch (Exception e) {
            results.recordTest(testName, false, e.getMessage());
        }
    }

    /**
     * Creates a test scanner instance.
     */
    private AsyncScannerIntegration createTestScanner() {
        try {
            ThreadPoolManager threadPoolManager = ThreadPoolManager.getInstance();
            AsyncScannerIntegration.IntegrationConfig config = new AsyncScannerIntegration.IntegrationConfig()
                    .withMaxConcurrentScans(4)
                    .withChunkSize(64 * 1024)
                    .withBufferSize(1024 * 1024)
                    .withDebounceDelay(100)
                    .withEventBatchSize(100)
                    .withEventProcessing(true);

            return new AsyncScannerIntegration(config, threadPoolManager);

        } catch (Exception e) {
            logger.error("Failed to create test scanner", e);
            throw new RuntimeException("Test scanner creation failed", e);
        }
    }

    /**
     * Calculates memory efficiency from scan result.
     */
    private double calculateMemoryEfficiency(AsyncScanResult result) {
        if (result == null || result.getPeakMemoryUsage() <= 0) {
            return 0.0;
        }

        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        return (double) result.getPeakMemoryUsage() / totalMemory;
    }

    /**
     * Cleans up the test environment.
     */
    private void cleanupTestEnvironment() {
        try {
            logger.info("Cleaning up test environment");

            // Clean up test directory
            if (Files.exists(config.getTestDirectory())) {
                Files.walk(config.getTestDirectory())
                        .sorted((a, b) -> b.compareTo(a)) // Reverse order to delete files first
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                logger.warn("Failed to delete test file: {}", path, e);
                            }
                        });

                Files.delete(config.getTestDirectory());
            }

            logger.info("Test environment cleanup completed");

        } catch (Exception e) {
            logger.warn("Failed to cleanup test environment", e);
        }
    }
}