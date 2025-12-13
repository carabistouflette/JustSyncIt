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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Benchmark for measuring data processing throughput.
 * Tests backup and restore throughput with various dataset sizes and
 * configurations.
 */
public class ThroughputBenchmark {

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
        generateBenchmarkReport();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void benchmarkSmallDatasetThroughput() throws Exception {
        // Test with small dataset (1-100 MB)
        int[] datasetSizes = { 1, 10, 50, 100 }; // MB

        for (int sizeMB : datasetSizes) {
            PerformanceMetrics metrics = new PerformanceMetrics("Small Dataset Backup - " + sizeMB + "MB");

            // Create test dataset
            BenchmarkDataGenerator.createMixedDataset(sourceDir, sizeMB);
            long totalSize = calculateTotalSize(sourceDir);

            // Measure backup throughput
            long startTime = System.currentTimeMillis();

            BackupOptions backupOptions = new BackupOptions.Builder()
                    .verifyIntegrity(true)
                    .chunkSize(1024 * 1024) // 1MB chunks
                    .build();

            CompletableFuture<BackupService.BackupResult> backupFuture = backupService.backup(sourceDir, backupOptions);
            BackupService.BackupResult backupResult = backupFuture.get();

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            // Record metrics
            assertTrue(backupResult.isSuccess(), "Backup should succeed");
            metrics.recordThroughput(totalSize, duration);
            metrics.recordOperationRate(backupResult.getFilesProcessed(), duration, "files");
            metrics.recordOperationRate(backupResult.getChunksCreated(), duration, "chunks");
            metrics.recordMetric("dataset_size_mb", sizeMB);
            metrics.recordMetric("files_processed", backupResult.getFilesProcessed());
            metrics.recordMetric("chunks_created", backupResult.getChunksCreated());

            metrics.finalizeMetrics();
            benchmarkResults.add(metrics);

            // Performance assertions
            double throughputMBps = (Double) metrics.getMetrics().get("throughput_mbps");
            assertTrue(throughputMBps > 10.0,
                    "Small dataset backup throughput should be >10 MB/s, was: "
                            + String.format("%.2f", throughputMBps));

            // Clean up for next test
            cleanupDirectory(sourceDir);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void benchmarkMediumDatasetThroughput() throws Exception {
        // Test with medium dataset (100 MB - 1 GB)
        int[] datasetSizes = { 100, 250, 500, 1024 }; // MB

        for (int sizeMB : datasetSizes) {
            PerformanceMetrics metrics = new PerformanceMetrics("Medium Dataset Backup - " + sizeMB + "MB");

            // Create test dataset
            BenchmarkDataGenerator.createMixedDataset(sourceDir, sizeMB);
            long totalSize = calculateTotalSize(sourceDir);

            // Measure backup throughput
            long startTime = System.currentTimeMillis();

            BackupOptions backupOptions = new BackupOptions.Builder()
                    .verifyIntegrity(true)
                    .chunkSize(2 * 1024 * 1024) // 2MB chunks for larger datasets
                    .build();

            CompletableFuture<BackupService.BackupResult> backupFuture = backupService.backup(sourceDir, backupOptions);
            BackupService.BackupResult backupResult = backupFuture.get();

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            // Record metrics
            assertTrue(backupResult.isSuccess(), "Backup should succeed");
            metrics.recordThroughput(totalSize, duration);
            metrics.recordOperationRate(backupResult.getFilesProcessed(), duration, "files");
            metrics.recordOperationRate(backupResult.getChunksCreated(), duration, "chunks");
            metrics.recordMetric("dataset_size_mb", sizeMB);
            metrics.recordMetric("files_processed", backupResult.getFilesProcessed());
            metrics.recordMetric("chunks_created", backupResult.getChunksCreated());

            metrics.finalizeMetrics();
            benchmarkResults.add(metrics);

            // Performance assertions
            double throughputMBps = (Double) metrics.getMetrics().get("throughput_mbps");
            assertTrue(throughputMBps > 25.0,
                    "Medium dataset backup throughput should be >25 MB/s, was: "
                            + String.format("%.2f", throughputMBps));

            // Clean up for next test
            cleanupDirectory(sourceDir);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void benchmarkLargeDatasetThroughput() throws Exception {
        // Test with large dataset (1-10 GB) - smaller sizes for testing
        int[] datasetSizes = { 1024, 2048 }; // MB (1GB, 2GB for testing)

        for (int sizeMB : datasetSizes) {
            PerformanceMetrics metrics = new PerformanceMetrics("Large Dataset Backup - " + sizeMB + "MB");

            // Create test dataset
            BenchmarkDataGenerator.createLargeFilesDataset(sourceDir, sizeMB);
            long totalSize = calculateTotalSize(sourceDir);

            // Measure backup throughput
            long startTime = System.currentTimeMillis();

            BackupOptions backupOptions = new BackupOptions.Builder()
                    .verifyIntegrity(true)
                    .chunkSize(4 * 1024 * 1024) // 4MB chunks for large datasets
                    .build();

            CompletableFuture<BackupService.BackupResult> backupFuture = backupService.backup(sourceDir, backupOptions);
            BackupService.BackupResult backupResult = backupFuture.get();

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            // Record metrics
            assertTrue(backupResult.isSuccess(), "Backup should succeed");
            metrics.recordThroughput(totalSize, duration);
            metrics.recordOperationRate(backupResult.getFilesProcessed(), duration, "files");
            metrics.recordOperationRate(backupResult.getChunksCreated(), duration, "chunks");
            metrics.recordMetric("dataset_size_mb", sizeMB);
            metrics.recordMetric("files_processed", backupResult.getFilesProcessed());
            metrics.recordMetric("chunks_created", backupResult.getChunksCreated());

            metrics.finalizeMetrics();
            benchmarkResults.add(metrics);

            // Performance assertions
            double throughputMBps = (Double) metrics.getMetrics().get("throughput_mbps");
            assertTrue(throughputMBps > 50.0,
                    "Large dataset backup throughput should be >50 MB/s, was: "
                            + String.format("%.2f", throughputMBps));

            // Clean up for next test
            cleanupDirectory(sourceDir);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void benchmarkRestoreThroughput() throws Exception {
        // Test restore throughput with various dataset sizes
        int[] datasetSizes = { 10, 100, 500 }; // MB

        for (int sizeMB : datasetSizes) {
            PerformanceMetrics metrics = new PerformanceMetrics("Restore Throughput - " + sizeMB + "MB");

            // Create and backup dataset first
            BenchmarkDataGenerator.createMixedDataset(sourceDir, sizeMB);

            BackupOptions backupOptions = new BackupOptions.Builder()
                    .verifyIntegrity(true)
                    .build();

            CompletableFuture<BackupService.BackupResult> backupFuture = backupService.backup(sourceDir, backupOptions);
            BackupService.BackupResult backupResult = backupFuture.get();
            assertTrue(backupResult.isSuccess(), "Backup should succeed");

            String snapshotId = backupResult.getSnapshotId();

            // Measure restore throughput
            long startTime = System.currentTimeMillis();

            RestoreOptions restoreOptions = new RestoreOptions.Builder()
                    .overwriteExisting(true)
                    .verifyIntegrity(true)
                    .build();

            CompletableFuture<RestoreService.RestoreResult> restoreFuture = restoreService.restore(snapshotId,
                    restoreDir, restoreOptions);
            RestoreService.RestoreResult restoreResult = restoreFuture.get();

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            // Record metrics
            assertTrue(restoreResult.isSuccess(), "Restore should succeed");
            metrics.recordThroughput(restoreResult.getTotalBytesRestored(), duration);
            metrics.recordOperationRate(restoreResult.getFilesRestored(), duration, "files");
            metrics.recordMetric("dataset_size_mb", sizeMB);
            metrics.recordMetric("files_restored", restoreResult.getFilesRestored());
            metrics.recordMetric("bytes_restored", restoreResult.getTotalBytesRestored());

            metrics.finalizeMetrics();
            benchmarkResults.add(metrics);

            // Performance assertions
            double throughputMBps = (Double) metrics.getMetrics().get("throughput_mbps");
            assertTrue(throughputMBps > 100.0,
                    "Restore throughput should be >100 MB/s, was: " + String.format("%.2f", throughputMBps));

            // Clean up for next test
            cleanupDirectory(sourceDir);
            cleanupDirectory(restoreDir);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void benchmarkChunkSizeThroughputImpact() throws Exception {
        // Test impact of different chunk sizes on throughput
        int[] chunkSizes = { 64 * 1024, 256 * 1024, 1024 * 1024, 4 * 1024 * 1024 }; // 64KB, 256KB, 1MB, 4MB
        int datasetSize = 100; // MB

        for (int chunkSize : chunkSizes) {
            PerformanceMetrics metrics = new PerformanceMetrics("Chunk Size Impact - " + (chunkSize / 1024) + "KB");

            // Create test dataset
            BenchmarkDataGenerator.createMixedDataset(sourceDir, datasetSize);
            long totalSize = calculateTotalSize(sourceDir);

            // Measure backup throughput with specific chunk size
            long startTime = System.currentTimeMillis();

            BackupOptions backupOptions = new BackupOptions.Builder()
                    .verifyIntegrity(true)
                    .chunkSize(chunkSize)
                    .build();

            CompletableFuture<BackupService.BackupResult> backupFuture = backupService.backup(sourceDir, backupOptions);
            BackupService.BackupResult backupResult = backupFuture.get();

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            // Record metrics
            assertTrue(backupResult.isSuccess(), "Backup should succeed");
            metrics.recordThroughput(totalSize, duration);
            metrics.recordOperationRate(backupResult.getFilesProcessed(), duration, "files");
            metrics.recordOperationRate(backupResult.getChunksCreated(), duration, "chunks");
            metrics.recordMetric("chunk_size_kb", chunkSize / 1024);
            metrics.recordMetric("files_processed", backupResult.getFilesProcessed());
            metrics.recordMetric("chunks_created", backupResult.getChunksCreated());

            // Calculate average chunk size
            double avgChunkSize = (double) totalSize / backupResult.getChunksCreated();
            metrics.recordMetric("avg_chunk_size_kb", avgChunkSize / 1024);

            metrics.finalizeMetrics();
            benchmarkResults.add(metrics);

            // Clean up for next test
            cleanupDirectory(sourceDir);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void benchmarkIntegrityVerificationThroughputImpact() throws Exception {
        // Test impact of integrity verification on throughput
        boolean[] verificationOptions = { false, true };
        int datasetSize = 100; // MB

        for (boolean verifyIntegrity : verificationOptions) {
            PerformanceMetrics metrics = new PerformanceMetrics(
                    "Integrity Verification Impact - " + (verifyIntegrity ? "Enabled" : "Disabled"));

            // Create test dataset
            BenchmarkDataGenerator.createMixedDataset(sourceDir, datasetSize);
            long totalSize = calculateTotalSize(sourceDir);

            // Measure backup throughput with/without integrity verification
            long startTime = System.currentTimeMillis();

            BackupOptions backupOptions = new BackupOptions.Builder()
                    .verifyIntegrity(verifyIntegrity)
                    .build();

            CompletableFuture<BackupService.BackupResult> backupFuture = backupService.backup(sourceDir, backupOptions);
            BackupService.BackupResult backupResult = backupFuture.get();

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            // Record metrics
            assertTrue(backupResult.isSuccess(), "Backup should succeed");
            metrics.recordThroughput(totalSize, duration);
            metrics.recordOperationRate(backupResult.getFilesProcessed(), duration, "files");
            metrics.recordMetric("verify_integrity", verifyIntegrity);
            metrics.recordMetric("files_processed", backupResult.getFilesProcessed());
            metrics.recordMetric("integrity_verified", backupResult.isIntegrityVerified());

            metrics.finalizeMetrics();
            benchmarkResults.add(metrics);

            // Clean up for next test
            cleanupDirectory(sourceDir);
        }
    }

    /**
     * Generates a comprehensive benchmark report.
     */
    private void generateBenchmarkReport() {
        System.out.println("\n=== THROUGHPUT BENCHMARK REPORT ===\n");

        // Group results by benchmark type
        benchmarkResults.stream()
                .collect(java.util.stream.Collectors.groupingBy(PerformanceMetrics::getBenchmarkName))
                .forEach((benchmarkType, results) -> {
                    System.out.println("### " + benchmarkType + " ###");

                    for (PerformanceMetrics metrics : results) {
                        System.out.println(metrics.generateSummary());

                        // Additional throughput-specific metrics
                        if (metrics.getMetrics().containsKey("throughput_mbps")) {
                            double throughput = (Double) metrics.getMetrics().get("throughput_mbps");
                            String rating = getThroughputRating(throughput);
                            System.out.println("Performance Rating: " + rating);
                        }

                        System.out.println();
                    }

                    System.out.println();
                });

        // Performance summary
        System.out.println("### PERFORMANCE SUMMARY ###");
        benchmarkResults.stream()
                .filter(m -> m.getMetrics().containsKey("throughput_mbps"))
                .mapToDouble(m -> (Double) m.getMetrics().get("throughput_mbps"))
                .average()
                .ifPresent(avg -> System.out.println("Average Throughput: " + String.format("%.2f", avg) + " MB/s"));

        System.out.println("Total Benchmarks Run: " + benchmarkResults.size());
    }

    /**
     * Gets a performance rating based on throughput.
     */
    private String getThroughputRating(double throughputMBps) {
        if (throughputMBps >= 100) {
            return "Excellent (>100 MB/s)";
        }
        if (throughputMBps >= 50) {
            return "Good (50-100 MB/s)";
        }
        if (throughputMBps >= 25) {
            return "Fair (25-50 MB/s)";
        }
        if (throughputMBps >= 10) {
            return "Poor (10-25 MB/s)";
        }
        return "Very Poor (<10 MB/s)";
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
     * Cleans up a directory by removing all files and subdirectories.
     */
    private void cleanupDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walkFileTree(directory, new java.nio.file.SimpleFileVisitor<Path>() {
                @Override
                public java.nio.file.FileVisitResult visitFile(Path file,
                        java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (!dir.equals(directory)) {
                        Files.delete(dir);
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
