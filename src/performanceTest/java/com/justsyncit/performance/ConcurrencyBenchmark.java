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

import com.justsyncit.ServiceFactory;
import com.justsyncit.backup.BackupOptions;
import com.justsyncit.backup.BackupService;
import com.justsyncit.hash.Blake3Service;
import com.justsyncit.performance.util.BenchmarkDataGenerator;
import com.justsyncit.performance.util.PerformanceMetrics;
import com.justsyncit.restore.RestoreOptions;
import com.justsyncit.restore.RestoreService;
import com.justsyncit.storage.ContentStore;
import com.justsyncit.storage.metadata.MetadataService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Benchmark for testing performance with concurrent operations.
 * Tests system behavior under various concurrency scenarios and resource
 * contention.
 */
public class ConcurrencyBenchmark {

    @TempDir
    Path tempDir;

    @TempDir
    Path storageDir;

    @TempDir
    Path sourceDir;

    @TempDir
    Path restoreDir;

    private ServiceFactory serviceFactory;
    private Blake3Service blake3Service;
    private ContentStore contentStore;
    private MetadataService metadataService;
    private BackupService backupService;
    private RestoreService restoreService;

    private final List<PerformanceMetrics> benchmarkResults = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        serviceFactory = new ServiceFactory();
        blake3Service = serviceFactory.createBlake3Service();
        contentStore = serviceFactory.createSqliteContentStore(blake3Service);
        metadataService = serviceFactory.createMetadataService(tempDir.resolve("test-metadata.db").toString());
        backupService = serviceFactory.createBackupService(contentStore, metadataService, blake3Service);
        restoreService = serviceFactory.createRestoreService(contentStore, metadataService, blake3Service);
    }

    @AfterEach
    void tearDown() throws Exception {
        // Generate benchmark report
        generateConcurrencyReport();

        // Clean up resources
        if (contentStore != null) {
            try {
                contentStore.close();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }

        if (metadataService != null) {
            try {
                metadataService.close();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void benchmarkConcurrentBackups() throws Exception {
        // Test concurrent backup operations
        int[] concurrencyLevels = { 1, 2, 4, 8, 16 };
        int datasetSizeMB = 25; // Per concurrent operation

        for (int concurrency : concurrencyLevels) {
            PerformanceMetrics metrics = new PerformanceMetrics(
                    "Concurrent Backups - " + concurrency + " threads");

            // Create datasets for concurrent operations
            List<Path> datasets = createConcurrentDatasets(concurrency, datasetSizeMB);

            // Measure concurrent backup performance
            ExecutorService executor = Executors.newFixedThreadPool(concurrency);
            List<CompletableFuture<BackupService.BackupResult>> futures = new ArrayList<>();

            long startTime = System.currentTimeMillis();
            final long[] totalSizeRef = { 0 };

            for (int i = 0; i < concurrency; i++) {
                final int index = i;
                final Path dataset = datasets.get(index);
                totalSizeRef[0] += calculateTotalSize(dataset);

                CompletableFuture<BackupService.BackupResult> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        BackupOptions backupOptions = new BackupOptions.Builder()
                                .verifyIntegrity(true)
                                .build();

                        return backupService.backup(dataset, backupOptions).get();
                    } catch (Exception e) {
                        fail("Concurrent backup should succeed", e);
                        return null;
                    }
                }, executor);

                futures.add(future);
            }

            // Wait for all operations to complete
            List<BackupService.BackupResult> results = futures.stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            fail("Concurrent backup should succeed", e);
                            return null;
                        }
                    })
                    .collect(java.util.stream.Collectors.toList());

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            long totalSize = totalSizeRef[0];

            // Shutdown executor
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);

            // Verify all backups succeeded
            for (BackupService.BackupResult result : results) {
                assertTrue(result.isSuccess(), "Each concurrent backup should succeed");
            }

            // Record metrics
            metrics.recordThroughput(totalSize, duration);
            metrics.recordOperationRate(concurrency, duration, "operations");
            metrics.recordMetric("concurrency_level", concurrency);
            metrics.recordMetric("dataset_size_per_operation_mb", datasetSizeMB);
            metrics.recordMetric("total_size_mb", totalSize / 1024 / 1024);
            metrics.recordMetric("operations_completed", results.size());

            // Calculate concurrency efficiency
            double sequentialTime = estimateSequentialTime(concurrency, datasetSizeMB);
            double concurrencyEfficiency = sequentialTime / (double) duration;
            metrics.recordMetric("concurrency_efficiency", concurrencyEfficiency);

            metrics.finalizeMetrics();
            benchmarkResults.add(metrics);

            // Performance assertions
            assertTrue(results.size() == concurrency, "All concurrent operations should complete");
            assertTrue(concurrencyEfficiency > 0.5,
                    "Concurrency efficiency should be reasonable: " + String.format("%.2f", concurrencyEfficiency));

            // Clean up for next test
            cleanupDirectory(sourceDir);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void benchmarkConcurrentRestores() throws Exception {
        // Test concurrent restore operations
        int[] concurrencyLevels = { 1, 2, 4, 8 };
        int datasetSizeMB = 20; // Per concurrent operation

        for (int concurrency : concurrencyLevels) {
            PerformanceMetrics metrics = new PerformanceMetrics(
                    "Concurrent Restores - " + concurrency + " threads");

            // First create backups to restore from
            List<String> snapshotIds = new ArrayList<>();
            for (int i = 0; i < concurrency; i++) {
                Path dataset = sourceDir.resolve("backup_source_" + i);
                Files.createDirectories(dataset);
                BenchmarkDataGenerator.createMixedDataset(dataset, datasetSizeMB);

                BackupOptions backupOptions = new BackupOptions.Builder()
                        .verifyIntegrity(true)
                        .build();

                CompletableFuture<BackupService.BackupResult> backupFuture = backupService.backup(dataset,
                        backupOptions);
                BackupService.BackupResult backupResult = backupFuture.get();
                assertTrue(backupResult.isSuccess(), "Backup should succeed");

                snapshotIds.add(backupResult.getSnapshotId());
            }

            // Measure concurrent restore performance
            ExecutorService executor = Executors.newFixedThreadPool(concurrency);
            List<CompletableFuture<RestoreService.RestoreResult>> futures = new ArrayList<>();

            long startTime = System.currentTimeMillis();
            final long[] totalSizeRef = { 0 };

            for (int i = 0; i < concurrency; i++) {
                final String snapshotId = snapshotIds.get(i);
                final Path restoreTarget = restoreDir.resolve("restore_" + i);
                Files.createDirectories(restoreTarget);

                CompletableFuture<RestoreService.RestoreResult> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        RestoreOptions restoreOptions = new RestoreOptions.Builder()
                                .overwriteExisting(true)
                                .verifyIntegrity(true)
                                .build();

                        return restoreService.restore(snapshotId, restoreTarget, restoreOptions).get();
                    } catch (Exception e) {
                        fail("Concurrent restore should succeed", e);
                        return null;
                    }
                }, executor);

                futures.add(future);
            }

            // Wait for all operations to complete
            List<RestoreService.RestoreResult> results = futures.stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            fail("Concurrent restore should succeed", e);
                            return null;
                        }
                    })
                    .collect(java.util.stream.Collectors.toList());

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            long totalSize = totalSizeRef[0];

            // Calculate total restored size
            for (RestoreService.RestoreResult result : results) {
                totalSizeRef[0] += result.getTotalBytesRestored();
            }

            // Shutdown executor
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);

            // Verify all restores succeeded
            for (RestoreService.RestoreResult result : results) {
                assertTrue(result.isSuccess(), "Each concurrent restore should succeed");
            }

            // Record metrics
            metrics.recordThroughput(totalSize, duration);
            metrics.recordOperationRate(concurrency, duration, "operations");
            metrics.recordMetric("concurrency_level", concurrency);
            metrics.recordMetric("dataset_size_per_operation_mb", datasetSizeMB);
            metrics.recordMetric("total_size_mb", totalSize / 1024 / 1024);
            metrics.recordMetric("operations_completed", results.size());

            // Calculate concurrency efficiency
            double sequentialTime = estimateSequentialRestoreTime(concurrency, datasetSizeMB);
            double concurrencyEfficiency = sequentialTime / (double) duration;
            metrics.recordMetric("concurrency_efficiency", concurrencyEfficiency);

            metrics.finalizeMetrics();
            benchmarkResults.add(metrics);

            // Performance assertions
            assertTrue(results.size() == concurrency, "All concurrent operations should complete");

            // Clean up for next test
            cleanupDirectory(sourceDir);
            cleanupDirectory(restoreDir);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void benchmarkMixedConcurrentOperations() throws Exception {
        // Test mixed concurrent backup and restore operations
        int[] concurrencyLevels = { 2, 4, 8 };
        int operationCount = 10; // Total operations per test
        int datasetSizeMB = 15;

        for (int concurrency : concurrencyLevels) {
            PerformanceMetrics metrics = new PerformanceMetrics(
                    "Mixed Concurrent Operations - " + concurrency + " threads");

            // Create initial snapshots for restore operations
            List<String> snapshotIds = new ArrayList<>();
            for (int i = 0; i < operationCount / 2; i++) {
                Path dataset = sourceDir.resolve("initial_backup_" + i);
                Files.createDirectories(dataset);
                BenchmarkDataGenerator.createMixedDataset(dataset, datasetSizeMB);

                BackupOptions backupOptions = new BackupOptions.Builder()
                        .verifyIntegrity(true)
                        .build();

                CompletableFuture<BackupService.BackupResult> backupFuture = backupService.backup(dataset,
                        backupOptions);
                BackupService.BackupResult backupResult = backupFuture.get();
                assertTrue(backupResult.isSuccess(), "Initial backup should succeed");

                snapshotIds.add(backupResult.getSnapshotId());
            }

            // Measure mixed concurrent operations
            ExecutorService executor = Executors.newFixedThreadPool(concurrency);
            List<CompletableFuture<Object>> futures = new ArrayList<>();

            long startTime = System.currentTimeMillis();
            final long[] totalSizeRef = { 0 };
            final int[] backupCountRef = { 0 };
            final int[] restoreCountRef = { 0 };

            for (int i = 0; i < operationCount; i++) {
                final boolean isBackup = i % 2 == 0;

                CompletableFuture<Object> future;
                if (isBackup) {
                    // Create new dataset for backup
                    Path dataset = sourceDir.resolve("concurrent_backup_" + backupCountRef[0]);
                    Files.createDirectories(dataset);
                    BenchmarkDataGenerator.createMixedDataset(dataset, datasetSizeMB);
                    totalSizeRef[0] += calculateTotalSize(dataset);

                    future = CompletableFuture.supplyAsync(() -> {
                        try {
                            BackupOptions backupOptions = new BackupOptions.Builder()
                                    .verifyIntegrity(true)
                                    .build();

                            return backupService.backup(dataset, backupOptions).get();
                        } catch (Exception e) {
                            fail("Concurrent backup should succeed", e);
                            return null;
                        }
                    }, executor);
                } else {
                    // Restore existing snapshot
                    final String snapshotId = snapshotIds.get(restoreCountRef[0] % snapshotIds.size());
                    final Path restoreTarget = restoreDir.resolve("concurrent_restore_" + restoreCountRef[0]);
                    Files.createDirectories(restoreTarget);

                    future = CompletableFuture.supplyAsync(() -> {
                        try {
                            RestoreOptions restoreOptions = new RestoreOptions.Builder()
                                    .overwriteExisting(true)
                                    .verifyIntegrity(true)
                                    .build();

                            RestoreService.RestoreResult result = restoreService
                                    .restore(snapshotId, restoreTarget, restoreOptions).get();
                            totalSizeRef[0] += result.getTotalBytesRestored();
                            return result;
                        } catch (Exception e) {
                            fail("Concurrent restore should succeed", e);
                            return null;
                        }
                    }, executor);
                }

                futures.add(future);
            }

            // Wait for all operations to complete
            List<Object> results = futures.stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            fail("Concurrent operation should succeed", e);
                            return null;
                        }
                    })
                    .collect(java.util.stream.Collectors.toList());

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            long totalSize = totalSizeRef[0];

            // Shutdown executor
            executor.shutdown();
            executor.awaitTermination(60, TimeUnit.SECONDS);

            // Verify all operations succeeded
            for (Object result : results) {
                if (result instanceof BackupService.BackupResult) {
                    assertTrue(((BackupService.BackupResult) result).isSuccess(), "Backup should succeed");
                } else if (result instanceof RestoreService.RestoreResult) {
                    assertTrue(((RestoreService.RestoreResult) result).isSuccess(), "Restore should succeed");
                }
            }

            // Record metrics
            metrics.recordThroughput(totalSize, duration);
            metrics.recordOperationRate(operationCount, duration, "operations");
            metrics.recordMetric("concurrency_level", concurrency);
            metrics.recordMetric("total_operations", operationCount);
            metrics.recordMetric("backup_operations", backupCountRef[0]);
            metrics.recordMetric("restore_operations", restoreCountRef[0]);
            metrics.recordMetric("total_size_mb", totalSize / 1024 / 1024);
            metrics.recordMetric("operations_completed", results.size());

            // Calculate resource contention metrics
            double operationsPerSecond = operationCount * 1000.0 / duration;
            metrics.recordMetric("operations_per_second", operationsPerSecond);

            metrics.finalizeMetrics();
            benchmarkResults.add(metrics);

            // Performance assertions
            assertTrue(results.size() == operationCount, "All mixed operations should complete");

            // Clean up for next test
            cleanupDirectory(sourceDir);
            cleanupDirectory(restoreDir);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void benchmarkResourceContention() throws Exception {
        // Test performance under resource contention scenarios
        int[] concurrencyLevels = { 4, 8, 16, 32 };
        int datasetSizeMB = 10;

        for (int concurrency : concurrencyLevels) {
            PerformanceMetrics metrics = new PerformanceMetrics(
                    "Resource Contention - " + concurrency + " threads");

            // Create datasets that will cause resource contention
            List<Path> datasets = createContentionDatasets(concurrency, datasetSizeMB);

            // Measure performance under contention
            ExecutorService executor = Executors.newFixedThreadPool(concurrency);
            List<CompletableFuture<BackupService.BackupResult>> futures = new ArrayList<>();

            long startTime = System.currentTimeMillis();
            final long[] totalSizeRef = { 0 };

            for (int i = 0; i < concurrency; i++) {
                final int index = i;
                final Path dataset = datasets.get(index);
                totalSizeRef[0] += calculateTotalSize(dataset);

                CompletableFuture<BackupService.BackupResult> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        // Use smaller chunks to increase contention
                        BackupOptions backupOptions = new BackupOptions.Builder()
                                .verifyIntegrity(true)
                                .chunkSize(32 * 1024) // 32KB chunks
                                .build();

                        return backupService.backup(dataset, backupOptions).get();
                    } catch (Exception e) {
                        fail("Contentioned backup should succeed", e);
                        return null;
                    }
                }, executor);

                futures.add(future);
            }

            // Wait for all operations to complete
            List<BackupService.BackupResult> results = futures.stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            fail("Contentioned backup should succeed", e);
                            return null;
                        }
                    })
                    .collect(java.util.stream.Collectors.toList());

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            long totalSize = totalSizeRef[0];

            // Shutdown executor
            executor.shutdown();
            executor.awaitTermination(60, TimeUnit.SECONDS);

            // Verify all operations succeeded
            for (BackupService.BackupResult result : results) {
                assertTrue(result.isSuccess(), "Each contentioned backup should succeed");
            }

            // Record metrics
            metrics.recordThroughput(totalSize, duration);
            metrics.recordOperationRate(concurrency, duration, "operations");
            metrics.recordMetric("concurrency_level", concurrency);
            metrics.recordMetric("dataset_size_per_operation_mb", datasetSizeMB);
            metrics.recordMetric("total_size_mb", totalSize / 1024 / 1024);
            metrics.recordMetric("operations_completed", results.size());

            // Calculate contention impact
            double baselineTime = estimateSequentialTime(concurrency, datasetSizeMB);
            double contentionImpact = duration / baselineTime;
            metrics.recordMetric("contention_impact", contentionImpact);

            // Calculate resource efficiency
            double efficiency = 1.0 / contentionImpact;
            metrics.recordMetric("resource_efficiency", efficiency);

            metrics.finalizeMetrics();
            benchmarkResults.add(metrics);

            // Performance assertions
            assertTrue(results.size() == concurrency, "All contentioned operations should complete");
            assertTrue(efficiency > 0.2,
                    "Resource efficiency should be reasonable under contention: " + String.format("%.2f", efficiency));

            // Clean up for next test
            cleanupDirectory(sourceDir);
        }
    }

    /**
     * Creates datasets for concurrent operations.
     */
    private List<Path> createConcurrentDatasets(int count, int sizeMB) throws IOException {
        List<Path> datasets = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            Path dataset = sourceDir.resolve("concurrent_" + i);
            Files.createDirectories(dataset);
            BenchmarkDataGenerator.createMixedDataset(dataset, sizeMB);
            datasets.add(dataset);
        }

        return datasets;
    }

    /**
     * Creates datasets designed to cause resource contention.
     */
    private List<Path> createContentionDatasets(int count, int sizeMB) throws IOException {
        List<Path> datasets = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            Path dataset = sourceDir.resolve("contention_" + i);
            Files.createDirectories(dataset);

            // Create many small files to increase contention
            BenchmarkDataGenerator.createSmallFilesDataset(dataset, sizeMB);
            datasets.add(dataset);
        }

        return datasets;
    }

    /**
     * Estimates sequential execution time for comparison.
     */
    private double estimateSequentialTime(int operations, int sizeMB) {
        // Simplified estimation based on typical performance
        return operations * sizeMB * 50.0; // 50ms per MB per operation
    }

    /**
     * Estimates sequential restore time for comparison.
     */
    private double estimateSequentialRestoreTime(int operations, int sizeMB) {
        // Simplified estimation based on typical performance
        return operations * sizeMB * 25.0; // 25ms per MB per restore operation
    }

    /**
     * Generates a comprehensive concurrency benchmark report.
     */
    private void generateConcurrencyReport() {
        System.out.println("\n=== CONCURRENCY BENCHMARK REPORT ===\n");

        // Group results by benchmark type
        benchmarkResults.stream()
                .collect(java.util.stream.Collectors.groupingBy(PerformanceMetrics::getBenchmarkName))
                .forEach((benchmarkType, results) -> {
                    System.out.println("### " + benchmarkType + " ###");

                    for (PerformanceMetrics metrics : results) {
                        System.out.println(metrics.generateSummary());

                        // Additional concurrency-specific metrics
                        if (metrics.getMetrics().containsKey("concurrency_efficiency")) {
                            double efficiency = (Double) metrics.getMetrics().get("concurrency_efficiency");
                            String efficiencyRating = getConcurrencyEfficiencyRating(efficiency);
                            System.out.println("Concurrency Efficiency: " + efficiencyRating);
                        }

                        if (metrics.getMetrics().containsKey("resource_efficiency")) {
                            double resourceEfficiency = (Double) metrics.getMetrics().get("resource_efficiency");
                            String resourceRating = getResourceEfficiencyRating(resourceEfficiency);
                            System.out.println("Resource Efficiency: " + resourceRating);
                        }

                        System.out.println();
                    }
                });

        // Overall concurrency analysis
        System.out.println("### CONCURRENCY ANALYSIS ###");

        List<Double> concurrencyEfficiencies = benchmarkResults.stream()
                .filter(m -> m.getMetrics().containsKey("concurrency_efficiency"))
                .map(m -> (Double) m.getMetrics().get("concurrency_efficiency"))
                .collect(java.util.stream.Collectors.toList());

        List<Double> resourceEfficiencies = benchmarkResults.stream()
                .filter(m -> m.getMetrics().containsKey("resource_efficiency"))
                .map(m -> (Double) m.getMetrics().get("resource_efficiency"))
                .collect(java.util.stream.Collectors.toList());

        if (!concurrencyEfficiencies.isEmpty()) {
            double avgConcurrencyEfficiency = concurrencyEfficiencies.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);
            System.out.println("Average Concurrency Efficiency: " + String.format("%.2f", avgConcurrencyEfficiency));
        }

        if (!resourceEfficiencies.isEmpty()) {
            double avgResourceEfficiency = resourceEfficiencies.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);
            System.out.println("Average Resource Efficiency: " + String.format("%.2f", avgResourceEfficiency));
        }

        // Find optimal concurrency level
        benchmarkResults.stream()
                .filter(m -> m.getMetrics().containsKey("concurrency_level") &&
                        m.getMetrics().containsKey("throughput_mbps"))
                .collect(java.util.stream.Collectors.groupingBy(
                        m -> (Integer) m.getMetrics().get("concurrency_level"),
                        java.util.stream.Collectors
                                .averagingDouble(m -> (Double) m.getMetrics().get("throughput_mbps"))))
                .entrySet()
                .stream()
                .max(java.util.Map.Entry.comparingByValue())
                .ifPresent(entry -> {
                    System.out.println("Optimal Concurrency Level: " + entry.getKey() +
                            " threads (" + String.format("%.2f", entry.getValue()) + " MB/s)");
                });

        System.out.println("Total Concurrency Benchmarks: " + benchmarkResults.size());
    }

    /**
     * Gets a concurrency efficiency rating based on efficiency value.
     */
    private String getConcurrencyEfficiencyRating(double efficiency) {
        if (efficiency >= 0.8)
            return "Excellent (>=80%)";
        if (efficiency >= 0.6)
            return "Good (60-80%)";
        if (efficiency >= 0.4)
            return "Fair (40-60%)";
        if (efficiency >= 0.2)
            return "Poor (20-40%)";
        return "Very Poor (<20%)";
    }

    /**
     * Gets a resource efficiency rating based on efficiency value.
     */
    private String getResourceEfficiencyRating(double efficiency) {
        if (efficiency >= 0.8)
            return "Excellent (>=80%)";
        if (efficiency >= 0.6)
            return "Good (60-80%)";
        if (efficiency >= 0.4)
            return "Fair (40-60%)";
        if (efficiency >= 0.2)
            return "Poor (20-40%)";
        return "Very Poor (<20%)";
    }

    /**
     * Calculates total size of files in a directory.
     */
    private long calculateTotalSize(Path directory) throws IOException {
        try (java.util.stream.Stream<Path> stream = Files.walk(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .mapToLong(file -> {
                        try {
                            return Files.size(file);
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .sum();
        }
    }

    /**
     * Cleans up a directory by removing all files.
     */
    private void cleanupDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (java.util.stream.Stream<Path> stream = Files.walk(directory)) {
                stream
                        .filter(Files::isRegularFile)
                        .forEach(file -> {
                            try {
                                Files.delete(file);
                            } catch (IOException e) {
                                // Ignore cleanup errors
                            }
                        });
            }
        }
    }
}