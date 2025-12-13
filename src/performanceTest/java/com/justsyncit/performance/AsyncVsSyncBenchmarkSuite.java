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

package com.justsyncit.performance;

import com.justsyncit.performance.util.BenchmarkDataGenerator;
import com.justsyncit.performance.util.BenchmarkEnvironmentValidator;
import com.justsyncit.performance.util.BenchmarkMetricsCollector;
import com.justsyncit.performance.util.BenchmarkReporter;
import com.justsyncit.performance.util.PerformanceMetrics;
import com.justsyncit.scanner.AsyncFileChunker;
import com.justsyncit.scanner.AsyncFileChunkerImpl;
import com.justsyncit.scanner.FileChunker;
import com.justsyncit.hash.Blake3Service;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Tag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive benchmark suite that compares async vs synchronous I/O
 * performance.
 * This is the main orchestrator for all performance benchmarks.
 */
public class AsyncVsSyncBenchmarkSuite {

    private static final java.util.Random RANDOM = new java.util.Random(42);

    @TempDir
    Path tempDir;

    @TempDir
    Path reportDir;

    @TempDir
    Path asyncDataDir;

    @TempDir
    Path syncDataDir;

    private List<PerformanceMetrics> allAsyncMetrics;
    private List<PerformanceMetrics> allSyncMetrics;
    private ExecutorService executorService;
    private Blake3Service blake3Service;
    private AsyncFileChunker asyncFileChunker;
    private FileChunker syncFileChunker;
    private BenchmarkDataGenerator dataGenerator;
    private com.justsyncit.ServiceFactory serviceFactory;
    private BenchmarkMetricsCollector metricsCollector;
    private BenchmarkReporter reporter;
    private BenchmarkEnvironmentValidator validator;

    // Performance targets
    private static final double TARGET_THROUGHPUT_NVME_GBPS = 3.0; // 3GB/s for NVMe
    private static final double TARGET_CPU_OVERHEAD_REDUCTION = 20.0; // 20% reduction
    private static final double TARGET_LATENCY_IMPROVEMENT = 15.0; // 15% improvement
    private static final double TARGET_SCALABILITY_IMPROVEMENT = 25.0; // 25% improvement
    private static final double TARGET_MEMORY_EFFICIENCY_IMPROVEMENT = 10.0; // 10% improvement

    @BeforeEach
    void setUp() throws Exception {
        // Ensure temp directories are properly initialized
        if (tempDir == null) {
            tempDir = java.nio.file.Files.createTempDirectory("benchmark-temp");
        }
        if (reportDir == null) {
            reportDir = java.nio.file.Files.createTempDirectory("benchmark-reports");
        }
        if (asyncDataDir == null) {
            asyncDataDir = java.nio.file.Files.createTempDirectory("benchmark-async-data");
        }
        if (syncDataDir == null) {
            syncDataDir = java.nio.file.Files.createTempDirectory("benchmark-sync-data");
        }

        allAsyncMetrics = new ArrayList<>();
        allSyncMetrics = new ArrayList<>();
        executorService = Executors.newFixedThreadPool(8);

        // Initialize services
        serviceFactory = new com.justsyncit.ServiceFactory();
        blake3Service = serviceFactory.createBlake3Service();
        asyncFileChunker = AsyncFileChunkerImpl.create(blake3Service);
        syncFileChunker = com.justsyncit.scanner.FixedSizeFileChunker.create(blake3Service);

        // Initialize benchmark infrastructure
        // BenchmarkDataGenerator uses static methods, no instance needed
        metricsCollector = new BenchmarkMetricsCollector();
        reporter = new BenchmarkReporter(reportDir);
        validator = new BenchmarkEnvironmentValidator();

        // Validate benchmark environment
        validator.validateEnvironment();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (executorService != null) {
            executorService.shutdown();
            executorService.awaitTermination(30, TimeUnit.SECONDS);
        }

        if (asyncFileChunker != null) {
            try {
                asyncFileChunker.closeAsync().get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }

        // Clean up temporary directories aggressively
        try {
            if (asyncDataDir != null && java.nio.file.Files.exists(asyncDataDir)) {
                try (java.util.stream.Stream<Path> stream = java.nio.file.Files.walk(asyncDataDir)) {
                    stream
                            .sorted(java.util.Comparator.reverseOrder())
                            .forEach(path -> {
                                try {
                                    java.nio.file.Files.deleteIfExists(path);
                                } catch (IOException e) {
                                    // Ignore cleanup errors
                                }
                            });
                }
            }
            if (syncDataDir != null && java.nio.file.Files.exists(syncDataDir)) {
                try (java.util.stream.Stream<Path> stream = java.nio.file.Files.walk(syncDataDir)) {
                    stream
                            .sorted(java.util.Comparator.reverseOrder())
                            .forEach(path -> {
                                try {
                                    java.nio.file.Files.deleteIfExists(path);
                                } catch (IOException e) {
                                    // Ignore cleanup errors
                                }
                            });
                }
            }
        } catch (IOException e) {
            // Ignore cleanup errors
        }
    }

    @Test
    @Tag("performance")
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    @DisplayName("Run Comprehensive Async vs Sync Benchmark Suite")
    void runComprehensiveBenchmarkSuite() throws Exception {
        System.out.println("Starting Comprehensive Async vs Sync Benchmark Suite...");
        System.out.println("Temp directory: " + tempDir);
        System.out.println("Report directory: " + reportDir);
        System.out.println("Started at: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        // Run all benchmark categories
        runCorePerformanceBenchmarks();
        runFileOperationBenchmarks();
        runResourceUtilizationBenchmarks();
        runRealWorldScenarioBenchmarks();

        // Generate comprehensive reports
        generateComprehensiveReports();

        // Validate performance targets
        validatePerformanceTargets();

        System.out.println("Comprehensive Async vs Sync Benchmark Suite completed successfully!");
        System.out.println("Reports generated in: " + reportDir);
    }

    /**
     * Runs core performance benchmarks comparing async vs sync implementations.
     */
    private void runCorePerformanceBenchmarks() throws Exception {
        System.out.println("\n=== Running Core Performance Benchmarks ===");

        // Throughput benchmarks
        runThroughputBenchmarks();

        // Latency benchmarks
        runLatencyBenchmarks();

        // CPU overhead benchmarks
        runCpuOverheadBenchmarks();

        // Scalability benchmarks
        runScalabilityBenchmarks();

        // Memory efficiency benchmarks
        runMemoryEfficiencyBenchmarks();

        System.out.println("Core performance benchmarks completed");
    }

    /**
     * Runs file operation benchmarks.
     */
    private void runFileOperationBenchmarks() throws Exception {
        System.out.println("\n=== Running File Operation Benchmarks ===");

        runFileChunkingBenchmarks();
        runDirectoryScanningBenchmarks();
        runBatchProcessingBenchmarks();
        runConcurrentOperationsBenchmarks();

        System.out.println("File operation benchmarks completed");
    }

    /**
     * Runs resource utilization benchmarks.
     */
    private void runResourceUtilizationBenchmarks() throws Exception {
        System.out.println("\n=== Running Resource Utilization Benchmarks ===");

        runThreadUtilizationBenchmarks();
        runMemoryAllocationBenchmarks();
        runGcPressureBenchmarks();
        runResourceContentionBenchmarks();

        System.out.println("Resource utilization benchmarks completed");
    }

    /**
     * Runs real-world scenario benchmarks.
     */
    private void runRealWorldScenarioBenchmarks() throws Exception {
        System.out.println("\n=== Running Real-World Scenario Benchmarks ===");

        runBackupSimulationBenchmarks();
        runLargeFileSetBenchmarks();
        runMixedWorkloadBenchmarks();
        runLongRunningBenchmarks();

        System.out.println("Real-world scenario benchmarks completed");
    }

    /**
     * Runs throughput benchmarks comparing async vs sync.
     */
    private void runThroughputBenchmarks() throws Exception {
        System.out.println("Running Throughput benchmarks...");

        int[] fileSizesMB = {1, 2, 3, 4}; // Very small sizes to avoid disk quota issues

        for (int sizeMB : fileSizesMB) {
            // Async throughput test
            PerformanceMetrics asyncMetrics = runThroughputBenchmark("Async", sizeMB, true);
            allAsyncMetrics.add(asyncMetrics);

            // Sync throughput test
            PerformanceMetrics syncMetrics = runThroughputBenchmark("Sync", sizeMB, false);
            allSyncMetrics.add(syncMetrics);

            // Calculate and record improvement
            double asyncThroughput = (Double) asyncMetrics.getMetrics().getOrDefault("throughput_mbps", 0.0);
            double syncThroughput = (Double) syncMetrics.getMetrics().getOrDefault("throughput_mbps", 0.0);
            double improvement = ((asyncThroughput - syncThroughput) / syncThroughput) * 100.0;

            System.out.println(String.format("  %dMB - Async: %.2f MB/s, Sync: %.2f MB/s, Improvement: %.1f%%",
                    sizeMB, asyncThroughput, syncThroughput, improvement));
        }
    }

    /**
     * Runs latency benchmarks for small file operations.
     */
    private void runLatencyBenchmarks() throws Exception {
        System.out.println("Running Latency benchmarks...");

        int[] smallFileSizes = {1, 4, 16, 64}; // KB

        for (int sizeKB : smallFileSizes) {
            // Async latency test
            PerformanceMetrics asyncMetrics = runLatencyBenchmark("Async", sizeKB, true);
            allAsyncMetrics.add(asyncMetrics);

            // Sync latency test
            PerformanceMetrics syncMetrics = runLatencyBenchmark("Sync", sizeKB, false);
            allSyncMetrics.add(syncMetrics);

            // Calculate and record improvement
            double asyncLatency = (Double) asyncMetrics.getMetrics().getOrDefault("average_latency_ms", 0.0);
            double syncLatency = (Double) syncMetrics.getMetrics().getOrDefault("average_latency_ms", 0.0);
            double improvement = ((syncLatency - asyncLatency) / syncLatency) * 100.0;

            System.out.println(String.format("  %dKB - Async: %.2f ms, Sync: %.2f ms, Improvement: %.1f%%",
                    sizeKB, asyncLatency, syncLatency, improvement));
        }
    }

    /**
     * Runs CPU overhead benchmarks.
     */
    private void runCpuOverheadBenchmarks() throws Exception {
        System.out.println("Running CPU Overhead benchmarks...");

        int[] workloads = {2, 5, 8, 10}; // Very small sizes to avoid disk quota issues

        for (int workloadMB : workloads) {
            // Async CPU test
            PerformanceMetrics asyncMetrics = runCpuOverheadBenchmark("Async", workloadMB, true);
            allAsyncMetrics.add(asyncMetrics);

            // Sync CPU test
            PerformanceMetrics syncMetrics = runCpuOverheadBenchmark("Sync", workloadMB, false);
            allSyncMetrics.add(syncMetrics);

            // Calculate and record reduction
            double asyncCpuUsage = (Double) asyncMetrics.getMetrics().getOrDefault("cpu_usage_percent", 0.0);
            double syncCpuUsage = (Double) syncMetrics.getMetrics().getOrDefault("cpu_usage_percent", 0.0);
            double reduction = ((syncCpuUsage - asyncCpuUsage) / syncCpuUsage) * 100.0;

            System.out.println(String.format("  %dMB - Async: %.1f%%, Sync: %.1f%%, Reduction: %.1f%%",
                    workloadMB, asyncCpuUsage, syncCpuUsage, reduction));
        }
    }

    /**
     * Runs scalability benchmarks under increasing load.
     */
    private void runScalabilityBenchmarks() throws Exception {
        System.out.println("Running Scalability benchmarks...");

        int[] concurrentOperations = {1, 4, 8, 16, 32};

        for (int concurrency : concurrentOperations) {
            // Async scalability test
            PerformanceMetrics asyncMetrics = runScalabilityBenchmark("Async", concurrency, true);
            allAsyncMetrics.add(asyncMetrics);

            // Sync scalability test
            PerformanceMetrics syncMetrics = runScalabilityBenchmark("Sync", concurrency, false);
            allSyncMetrics.add(syncMetrics);

            // Calculate and record improvement
            double asyncThroughput = (Double) asyncMetrics.getMetrics().getOrDefault("throughput_mbps", 0.0);
            double syncThroughput = (Double) syncMetrics.getMetrics().getOrDefault("throughput_mbps", 0.0);
            double improvement = ((asyncThroughput - syncThroughput) / syncThroughput) * 100.0;

            System.out.println(String.format("  %d concurrent - Async: %.2f MB/s, Sync: %.2f MB/s, Improvement: %.1f%%",
                    concurrency, asyncThroughput, syncThroughput, improvement));
        }
    }

    /**
     * Runs memory efficiency benchmarks.
     */
    private void runMemoryEfficiencyBenchmarks() throws Exception {
        System.out.println("Running Memory Efficiency benchmarks...");

        int[] datasetSizes = {5, 10, 15}; // Very small sizes to avoid disk quota issues

        for (int sizeMB : datasetSizes) {
            // Async memory test
            PerformanceMetrics asyncMetrics = runMemoryEfficiencyBenchmark("Async", sizeMB, true);
            allAsyncMetrics.add(asyncMetrics);

            // Sync memory test
            PerformanceMetrics syncMetrics = runMemoryEfficiencyBenchmark("Sync", sizeMB, false);
            allSyncMetrics.add(syncMetrics);

            // Calculate and record improvement
            Object asyncMemoryObj = asyncMetrics.getMetrics().getOrDefault("peak_memory_mb", 0.0);
            Object syncMemoryObj = syncMetrics.getMetrics().getOrDefault("peak_memory_mb", 0.0);

            double asyncMemoryUsage = (asyncMemoryObj instanceof Long)
                    ? ((Long) asyncMemoryObj).doubleValue()
                    : ((Double) asyncMemoryObj);
            double syncMemoryUsage = (syncMemoryObj instanceof Long)
                    ? ((Long) syncMemoryObj).doubleValue()
                    : ((Double) syncMemoryObj);
            double improvement = ((syncMemoryUsage - asyncMemoryUsage) / syncMemoryUsage) * 100.0;

            System.out.println(String.format("  %dMB - Async: %.1f MB, Sync: %.1f MB, Improvement: %.1f%%",
                    sizeMB, asyncMemoryUsage, syncMemoryUsage, improvement));
        }
    }

    /**
     * Runs individual throughput benchmark.
     */
    private PerformanceMetrics runThroughputBenchmark(String type, int sizeMB, boolean useAsync) throws Exception {
        String benchmarkName = String.format("%s Throughput - %dMB", type, sizeMB);
        PerformanceMetrics metrics = new PerformanceMetrics(benchmarkName);

        // Use in-memory data instead of writing to disk to avoid quota issues
        byte[] testData = createTestDataInMemory(sizeMB);

        // Create a temporary file only for the chunking operation
        Path tempFile = null;
        try {
            tempFile = java.nio.file.Files.createTempFile("benchmark-throughput-" + sizeMB, ".dat");
            java.nio.file.Files.write(tempFile, testData);

            // Measure throughput
            long startTime = System.nanoTime();

            if (useAsync) {
                CompletableFuture<FileChunker.ChunkingResult> future = asyncFileChunker.chunkFileAsync(tempFile,
                        new FileChunker.ChunkingOptions());
                FileChunker.ChunkingResult result = future.get();

                long endTime = System.nanoTime();
                long durationMs = (endTime - startTime) / 1_000_000;

                metrics.recordThroughput(testData.length, durationMs);
                metrics.recordMetric("chunks_created", result.getChunkCount());
                metrics.recordMetric("success", result.isSuccess());
            } else {
                FileChunker.ChunkingResult result = syncFileChunker
                        .chunkFile(tempFile, new FileChunker.ChunkingOptions()).get();

                long endTime = System.nanoTime();
                long durationMs = (endTime - startTime) / 1_000_000;

                metrics.recordThroughput(testData.length, durationMs);
                metrics.recordMetric("chunks_created", result.getChunkCount());
                metrics.recordMetric("success", result.isSuccess());
            }
        } finally {
            // Clean up temporary file immediately
            if (tempFile != null) {
                try {
                    java.nio.file.Files.deleteIfExists(tempFile);
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
        }

        metrics.finalizeMetrics();
        return metrics;
    }

    /**
     * Creates test data in memory to avoid disk quota issues.
     */
    private byte[] createTestDataInMemory(int sizeMB) {
        int sizeBytes = sizeMB * 1024 * 1024;
        byte[] content = new byte[sizeBytes];

        // Fill with some pattern to make it more realistic
        RANDOM.nextBytes(content);

        return content;
    }

    /**
     * Runs individual latency benchmark.
     */
    private PerformanceMetrics runLatencyBenchmark(String type, int sizeKB, boolean useAsync) throws Exception {
        String benchmarkName = String.format("%s Latency - %dKB", type, sizeKB);
        PerformanceMetrics metrics = new PerformanceMetrics(benchmarkName);

        // Use in-memory data instead of writing to disk
        byte[] testData = createTestDataInMemoryKB(sizeKB);

        // Create a temporary file only for the chunking operation
        Path tempFile = null;
        try {
            tempFile = java.nio.file.Files.createTempFile("benchmark-latency-" + sizeKB, ".dat");
            java.nio.file.Files.write(tempFile, testData);

            // Measure latency with multiple iterations
            int iterations = 100;
            List<Long> latencies = new ArrayList<>();

            for (int i = 0; i < iterations; i++) {
                long startTime = System.nanoTime();

                if (useAsync) {
                    CompletableFuture<FileChunker.ChunkingResult> future = asyncFileChunker.chunkFileAsync(tempFile,
                            new FileChunker.ChunkingOptions());
                    future.get();
                } else {
                    syncFileChunker.chunkFile(tempFile, new FileChunker.ChunkingOptions()).get();
                }

                long endTime = System.nanoTime();
                latencies.add((endTime - startTime) / 1_000_000); // Convert to ms
            }

            // Calculate average latency
            double averageLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
            double minLatency = latencies.stream().mapToLong(Long::longValue).min().orElse(0L);
            double maxLatency = latencies.stream().mapToLong(Long::longValue).max().orElse(0L);

            metrics.recordMetric("average_latency_ms", averageLatency);
            metrics.recordMetric("min_latency_ms", minLatency);
            metrics.recordMetric("max_latency_ms", maxLatency);
            metrics.recordMetric("iterations", iterations);
        } finally {
            // Clean up temporary file immediately
            if (tempFile != null) {
                try {
                    java.nio.file.Files.deleteIfExists(tempFile);
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
        }

        metrics.finalizeMetrics();
        return metrics;
    }

    /**
     * Creates test data in memory for KB sizes to avoid disk quota issues.
     */
    private byte[] createTestDataInMemoryKB(int sizeKB) {
        int sizeBytes = sizeKB * 1024;
        byte[] content = new byte[sizeBytes];

        // Fill with some pattern to make it more realistic
        RANDOM.nextBytes(content);

        return content;
    }

    /**
     * Runs individual CPU overhead benchmark.
     */
    private PerformanceMetrics runCpuOverheadBenchmark(String type, int workloadMB, boolean useAsync) throws Exception {
        String benchmarkName = String.format("%s CPU Overhead - %dMB", type, workloadMB);
        PerformanceMetrics metrics = new PerformanceMetrics(benchmarkName);

        // Use in-memory data instead of writing to disk
        byte[] testData = createTestDataInMemory(workloadMB);

        // Create a temporary file only for the chunking operation
        Path tempFile = null;
        try {
            tempFile = java.nio.file.Files.createTempFile("benchmark-cpu-" + workloadMB, ".dat");
            java.nio.file.Files.write(tempFile, testData);

            // Measure CPU usage during processing
            metricsCollector.startCpuMonitoring();

            long startTime = System.nanoTime();

            if (useAsync) {
                CompletableFuture<FileChunker.ChunkingResult> future = asyncFileChunker.chunkFileAsync(tempFile,
                        new FileChunker.ChunkingOptions());
                future.get();
            } else {
                syncFileChunker.chunkFile(tempFile, new FileChunker.ChunkingOptions()).get();
            }

            long endTime = System.nanoTime();

            double cpuUsage = metricsCollector.stopCpuMonitoring();
            long durationMs = (endTime - startTime) / 1_000_000;

            metrics.recordMetric("cpu_usage_percent", cpuUsage);
            metrics.recordMetric("duration_ms", durationMs);
            metrics.recordMetric("workload_mb", workloadMB);
        } finally {
            // Clean up temporary file immediately
            if (tempFile != null) {
                try {
                    java.nio.file.Files.deleteIfExists(tempFile);
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
        }

        metrics.finalizeMetrics();
        return metrics;
    }

    /**
     * Runs individual scalability benchmark.
     */
    private PerformanceMetrics runScalabilityBenchmark(String type, int concurrency, boolean useAsync)
            throws Exception {
        String benchmarkName = String.format("%s Scalability - %d concurrent", type, concurrency);
        PerformanceMetrics metrics = new PerformanceMetrics(benchmarkName);

        List<CompletableFuture<FileChunker.ChunkingResult>> futures = new ArrayList<>();
        List<Path> testFiles = new ArrayList<>();

        // Use in-memory data instead of writing to disk
        byte[] testData = createTestDataInMemory(10); // 10MB each

        // Create temporary files only for the chunking operations
        try {
            for (int i = 0; i < concurrency; i++) {
                Path tempFile = java.nio.file.Files.createTempFile("benchmark-scalability-" + concurrency + "-" + i,
                        ".dat");
                java.nio.file.Files.write(tempFile, testData);
                testFiles.add(tempFile);
            }

            // Measure concurrent processing
            long startTime = System.nanoTime();

            if (useAsync) {
                for (Path file : testFiles) {
                    CompletableFuture<FileChunker.ChunkingResult> future = asyncFileChunker.chunkFileAsync(file,
                            new FileChunker.ChunkingOptions());
                    futures.add(future);
                }

                // Wait for all to complete
                CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0])).get();
            } else {
                // For sync, process sequentially (simulating single-threaded behavior)
                for (Path file : testFiles) {
                    syncFileChunker.chunkFile(file, new FileChunker.ChunkingOptions()).get();
                }
            }

            long endTime = System.nanoTime();
            long durationMs = (endTime - startTime) / 1_000_000;

            // Calculate total data processed
            long totalSize = (long) testData.length * concurrency;

            metrics.recordThroughput(totalSize, durationMs);
            metrics.recordMetric("concurrent_operations", concurrency);
            metrics.recordMetric("files_processed", testFiles.size());
        } finally {
            // Clean up temporary files immediately
            for (Path file : testFiles) {
                try {
                    java.nio.file.Files.deleteIfExists(file);
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
        }

        metrics.finalizeMetrics();
        return metrics;
    }

    /**
     * Runs individual memory efficiency benchmark.
     */
    private PerformanceMetrics runMemoryEfficiencyBenchmark(String type, int sizeMB, boolean useAsync)
            throws Exception {
        String benchmarkName = String.format("%s Memory Efficiency - %dMB", type, sizeMB);
        PerformanceMetrics metrics = new PerformanceMetrics(benchmarkName);

        // Use in-memory data instead of writing to disk
        byte[] testData = createTestDataInMemory(sizeMB);

        // Create a temporary file only for the chunking operation
        Path tempFile = null;
        try {
            tempFile = java.nio.file.Files.createTempFile("benchmark-memory-" + sizeMB, ".dat");
            java.nio.file.Files.write(tempFile, testData);

            // Measure memory usage
            metricsCollector.startMemoryMonitoring();

            long startTime = System.nanoTime();

            if (useAsync) {
                CompletableFuture<FileChunker.ChunkingResult> future = asyncFileChunker.chunkFileAsync(tempFile,
                        new FileChunker.ChunkingOptions());
                future.get();
            } else {
                syncFileChunker.chunkFile(tempFile, new FileChunker.ChunkingOptions()).get();
            }

            long endTime = System.nanoTime();

            BenchmarkMetricsCollector.MemoryStats memoryStats = metricsCollector.stopMemoryMonitoring();
            long durationMs = (endTime - startTime) / 1_000_000;

            metrics.recordMetric("peak_memory_mb", memoryStats.peakMemoryMB);
            metrics.recordMetric("average_memory_mb", memoryStats.averageMemoryMB);
            metrics.recordMetric("memory_allocations", memoryStats.allocations);
            metrics.recordMetric("duration_ms", durationMs);
        } finally {
            // Clean up temporary file immediately
            if (tempFile != null) {
                try {
                    java.nio.file.Files.deleteIfExists(tempFile);
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
        }

        metrics.finalizeMetrics();
        return metrics;
    }

    /**
     * Calculates total size of files in a directory.
     */
    private long calculateDatasetSize(Path path) {
        try {
            if (Files.isRegularFile(path)) {
                return Files.size(path);
            } else if (Files.isDirectory(path)) {
                try (java.util.stream.Stream<Path> stream = Files.walk(path)) {
                    return stream
                            .filter(Files::isRegularFile)
                            .mapToLong(p -> {
                                try {
                                    return Files.size(p);
                                } catch (IOException e) {
                                    return 0;
                                }
                            })
                            .sum();
                }
            }
        } catch (IOException e) {
            return 0;
        }
        return 0;
    }

    /**
     * Creates a single test file with the specified size in MB.
     */
    private void createSingleTestFileMB(Path filePath, int sizeMB) throws IOException {
        int sizeBytes = sizeMB * 1024 * 1024;
        byte[] content = new byte[sizeBytes];

        // Fill with some pattern to make it more realistic
        RANDOM.nextBytes(content);

        Files.createDirectories(filePath.getParent());
        Files.write(filePath, content);
    }

    /**
     * Creates a single test file with the specified size in KB.
     */
    private void createSingleTestFileKB(Path filePath, int sizeKB) throws IOException {
        int sizeBytes = sizeKB * 1024;
        byte[] content = new byte[sizeBytes];

        // Fill with some pattern to make it more realistic
        RANDOM.nextBytes(content);

        Files.createDirectories(filePath.getParent());
        Files.write(filePath, content);
    }

    // Placeholder methods for other benchmark categories
    private void runFileChunkingBenchmarks() throws Exception {
        System.out.println("File chunking benchmarks - TODO");
    }

    private void runDirectoryScanningBenchmarks() throws Exception {
        System.out.println("Directory scanning benchmarks - TODO");
    }

    private void runBatchProcessingBenchmarks() throws Exception {
        System.out.println("Batch processing benchmarks - TODO");
    }

    private void runConcurrentOperationsBenchmarks() throws Exception {
        System.out.println("Concurrent operations benchmarks - TODO");
    }

    private void runThreadUtilizationBenchmarks() throws Exception {
        System.out.println("Thread utilization benchmarks - TODO");
    }

    private void runMemoryAllocationBenchmarks() throws Exception {
        System.out.println("Memory allocation benchmarks - TODO");
    }

    private void runGcPressureBenchmarks() throws Exception {
        System.out.println("GC pressure benchmarks - TODO");
    }

    private void runResourceContentionBenchmarks() throws Exception {
        System.out.println("Resource contention benchmarks - TODO");
    }

    private void runBackupSimulationBenchmarks() throws Exception {
        System.out.println("Backup simulation benchmarks - TODO");
    }

    private void runLargeFileSetBenchmarks() throws Exception {
        System.out.println("Large file set benchmarks - TODO");
    }

    private void runMixedWorkloadBenchmarks() throws Exception {
        System.out.println("Mixed workload benchmarks - TODO");
    }

    private void runLongRunningBenchmarks() throws Exception {
        System.out.println("Long running benchmarks - TODO");
    }

    private void generateComprehensiveReports() throws Exception {
        System.out.println("\n=== Generating Comprehensive Reports ===");

        // Generate comparative reports
        reporter.generateComparativeReport(allAsyncMetrics, allSyncMetrics);

        // Generate trend analysis
        reporter.generateTrendAnalysis(allAsyncMetrics, allSyncMetrics);

        // Generate performance regression report
        reporter.generateRegressionReport(allAsyncMetrics, allSyncMetrics);

        // Generate visualization data
        reporter.generateVisualizationData(allAsyncMetrics, allSyncMetrics);

        System.out.println("Comprehensive reports generated");
    }

    private void validatePerformanceTargets() {
        System.out.println("\n=== Validating Performance Targets ===");

        // Calculate overall improvements
        double avgThroughputImprovement = calculateAverageMetricChange("throughput_mbps",
                (async, sync) -> (async - sync) / sync);
        double avgCpuReduction = calculateAverageMetricChange("cpu_usage_percent",
                (async, sync) -> (sync - async) / sync);
        double avgLatencyImprovement = calculateAverageMetricChange("average_latency_ms",
                (async, sync) -> (sync - async) / sync);
        double avgScalabilityImprovement = calculateAverageMetricChange("scalability_throughput",
                (async, sync) -> (async - sync) / sync);
        double avgMemoryImprovement = calculateAverageMetricChange("peak_memory_mb",
                (async, sync) -> (sync - async) / sync);

        // Validate targets
        validateTarget("NVMe Throughput (>3GB/s)", avgThroughputImprovement, TARGET_THROUGHPUT_NVME_GBPS * 1024);
        validateTarget("CPU Overhead Reduction (>20%)", avgCpuReduction, TARGET_CPU_OVERHEAD_REDUCTION);
        validateTarget("Latency Improvement (>15%)", avgLatencyImprovement, TARGET_LATENCY_IMPROVEMENT);
        validateTarget("Scalability Improvement (>25%)", avgScalabilityImprovement, TARGET_SCALABILITY_IMPROVEMENT);
        validateTarget("Memory Efficiency Improvement (>10%)", avgMemoryImprovement,
                TARGET_MEMORY_EFFICIENCY_IMPROVEMENT);

        System.out.println("\nPerformance target validation completed");
    }

    @FunctionalInterface
    private interface MetricCalculator {
        double calculate(double asyncValue, double syncValue);
    }

    private double calculateAverageMetricChange(String metricKey, MetricCalculator calculator) {
        double totalChange = 0.0;
        int count = 0;

        for (int i = 0; i < Math.min(allAsyncMetrics.size(), allSyncMetrics.size()); i++) {
            PerformanceMetrics asyncMetric = allAsyncMetrics.get(i);
            PerformanceMetrics syncMetric = allSyncMetrics.get(i);

            if (asyncMetric.getMetrics().containsKey(metricKey)
                    && syncMetric.getMetrics().containsKey(metricKey)) {

                double asyncValue = getDoubleValue(asyncMetric.getMetrics().get(metricKey));
                double syncValue = getDoubleValue(syncMetric.getMetrics().get(metricKey));

                if (syncValue > 0) {
                    totalChange += calculator.calculate(asyncValue, syncValue) * 100.0;
                    count++;
                }
            }
        }

        return count > 0 ? totalChange / count : 0.0;
    }

    private double getDoubleValue(Object value) {
        if (value instanceof Long) {
            return ((Long) value).doubleValue();
        } else if (value instanceof Double) {
            return (Double) value;
        } else if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }

    private void validateTarget(String targetName, double actualValue, double targetValue) {
        boolean met = actualValue >= targetValue;
        String status = met ? "✓ MET" : "✗ NOT MET";

        System.out.println(String.format("%s: %.1f%% (%s) - Target: %.1f%%",
                targetName, actualValue, status, targetValue));
    }

    /**
     * Sets the directories for the benchmark suite.
     * This allows manual configuration without using reflection.
     */
    public void setDirectories(Path tempDir, Path reportDir, Path asyncDataDir, Path syncDataDir) {
        this.tempDir = tempDir;
        this.reportDir = reportDir;
        this.asyncDataDir = asyncDataDir;
        this.syncDataDir = syncDataDir;
    }

    /**
     * Main method for running benchmarks as a standalone application.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        try {
            System.out.println("Starting Async vs Sync Benchmark Suite as standalone application...");

            // Create instance and run benchmarks
            AsyncVsSyncBenchmarkSuite benchmarkSuite = new AsyncVsSyncBenchmarkSuite();

            // Initialize temporary directories for standalone execution
            Path tempDir = java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"),
                    "benchmark-" + System.currentTimeMillis());
            Path reportDir = tempDir.resolve("reports");
            Path asyncDataDir = tempDir.resolve("async-data");
            Path syncDataDir = tempDir.resolve("sync-data");

            java.nio.file.Files.createDirectories(tempDir);
            java.nio.file.Files.createDirectories(reportDir);
            java.nio.file.Files.createDirectories(asyncDataDir);
            java.nio.file.Files.createDirectories(syncDataDir);

            // Configure directories directly
            benchmarkSuite.setDirectories(tempDir, reportDir, asyncDataDir, syncDataDir);

            // Run the lifecycle
            benchmarkSuite.setUp();

            try {
                benchmarkSuite.runComprehensiveBenchmarkSuite();
            } finally {
                benchmarkSuite.tearDown();
            }

            System.out.println("\nBenchmark suite completed successfully!");
            System.out.println("Reports available in: " + reportDir);

        } catch (Exception e) {
            System.err.println("Error running benchmark suite: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}