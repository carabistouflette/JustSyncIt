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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Benchmark for testing system scalability with increasing dataset sizes.
 * Measures how performance characteristics change as data volume increases.
 */
public class ScalabilityBenchmark {

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
        generateScalabilityReport();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void benchmarkLinearDataSizeScalability() throws Exception {
        // Test scalability with linearly increasing dataset sizes
        int[] datasetSizesMB = {10, 50, 100, 250, 500, 1000}; // Progressive growth
        
        for (int sizeMB : datasetSizesMB) {
            PerformanceMetrics metrics = new PerformanceMetrics("Linear Scalability - " + sizeMB + "MB");
            
            // Create test dataset
            BenchmarkDataGenerator.createMixedDataset(sourceDir, sizeMB);
            long totalSize = calculateTotalSize(sourceDir);
            int fileCount = countFiles(sourceDir);
            
            // Measure backup performance
            long startTime = System.currentTimeMillis();
            
            BackupOptions backupOptions = new BackupOptions.Builder()
                    .verifyIntegrity(true)
                    .chunkSize(1024 * 1024) // 1MB chunks
                    .build();
            
            CompletableFuture<BackupService.BackupResult> backupFuture =
                    backupService.backup(sourceDir, backupOptions);
            BackupService.BackupResult backupResult = backupFuture.get();
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            // Record metrics
            assertTrue(backupResult.isSuccess(), "Backup should succeed");
            metrics.recordThroughput(totalSize, duration);
            metrics.recordOperationRate(fileCount, duration, "files");
            metrics.recordOperationRate(backupResult.getChunksCreated(), duration, "chunks");
            metrics.recordMetric("dataset_size_mb", sizeMB);
            metrics.recordMetric("actual_size_mb", totalSize / 1024 / 1024);
            metrics.recordMetric("file_count", fileCount);
            metrics.recordMetric("chunks_created", backupResult.getChunksCreated());
            
            // Calculate scalability metrics
            double throughputPerMB = (Double) metrics.getMetrics().get("throughput_mbps");
            double timePerMB = (double) duration / sizeMB;
            double memoryPerMB = calculateMemoryUsagePerMB(sizeMB);
            
            metrics.recordMetric("time_per_mb_ms", timePerMB);
            metrics.recordMetric("memory_per_mb_kb", memoryPerMB);
            metrics.recordMetric("files_per_mb", (double) fileCount / sizeMB);
            
            metrics.finalizeMetrics();
            benchmarkResults.add(metrics);
            
            // Performance assertions - allow for some degradation with size
            double throughputMBps = (Double) metrics.getMetrics().get("throughput_mbps");
            double expectedMinThroughput = Math.max(10.0, 50.0 - (sizeMB / 50.0)); // Degradation allowance
            assertTrue(throughputMBps > expectedMinThroughput, 
                    "Throughput should be reasonable for " + sizeMB + "MB: " + 
                    String.format("%.2f", throughputMBps) + " MB/s (expected >" + 
                    String.format("%.2f", expectedMinThroughput) + " MB/s)");
            
            // Clean up for next test
            cleanupDirectory(sourceDir);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void benchmarkFileCountScalability() throws Exception {
        // Test scalability with increasing file counts (constant total size)
        int[] fileCounts = {10, 50, 100, 500, 1000, 5000};
        int totalSizeMB = 100; // Keep total size constant
        
        for (int fileCount : fileCounts) {
            PerformanceMetrics metrics = new PerformanceMetrics("File Count Scalability - " + fileCount + " files");
            
            // Create test dataset with many small files
            BenchmarkDataGenerator.createSmallFilesDataset(sourceDir, totalSizeMB);
            long totalSize = calculateTotalSize(sourceDir);
            
            // Measure backup performance
            long startTime = System.currentTimeMillis();
            
            BackupOptions backupOptions = new BackupOptions.Builder()
                    .verifyIntegrity(true)
                    .build();
            
            CompletableFuture<BackupService.BackupResult> backupFuture =
                    backupService.backup(sourceDir, backupOptions);
            BackupService.BackupResult backupResult = backupFuture.get();
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            // Record metrics
            assertTrue(backupResult.isSuccess(), "Backup should succeed");
            metrics.recordThroughput(totalSize, duration);
            metrics.recordOperationRate(fileCount, duration, "files");
            metrics.recordMetric("target_file_count", fileCount);
            metrics.recordMetric("actual_file_count", backupResult.getFilesProcessed());
            metrics.recordMetric("total_size_mb", totalSize / 1024 / 1024);
            metrics.recordMetric("chunks_created", backupResult.getChunksCreated());
            
            // Calculate per-file processing time
            double timePerFile = (double) duration / backupResult.getFilesProcessed();
            metrics.recordMetric("time_per_file_ms", timePerFile);
            
            metrics.finalizeMetrics();
            benchmarkResults.add(metrics);
            
            // Performance assertions - should handle many files reasonably
            double timePerFileMs = (Double) metrics.getMetrics().get("time_per_file_ms");
            assertTrue(timePerFileMs < 100.0, 
                    "Time per file should be reasonable: " + String.format("%.2f", timePerFileMs) + "ms");
            
            // Clean up for next test
            cleanupDirectory(sourceDir);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void benchmarkDirectoryDepthScalability() throws Exception {
        // Test scalability with increasing directory depth
        int[] depths = {1, 5, 10, 20, 50};
        int filesPerLevel = 10;
        int fileSizeKB = 10;
        
        for (int depth : depths) {
            PerformanceMetrics metrics = new PerformanceMetrics("Directory Depth Scalability - " + depth + " levels");
            
            // Create test dataset with deep directory structure
            BenchmarkDataGenerator.createDeepDirectoryDataset(sourceDir, depth, filesPerLevel, fileSizeKB * 1024);
            long totalSize = calculateTotalSize(sourceDir);
            int totalFiles = countFiles(sourceDir);
            
            // Measure backup performance
            long startTime = System.currentTimeMillis();
            
            BackupOptions backupOptions = new BackupOptions.Builder()
                    .verifyIntegrity(true)
                    .build();
            
            CompletableFuture<BackupService.BackupResult> backupFuture =
                    backupService.backup(sourceDir, backupOptions);
            BackupService.BackupResult backupResult = backupFuture.get();
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            // Record metrics
            assertTrue(backupResult.isSuccess(), "Backup should succeed");
            metrics.recordThroughput(totalSize, duration);
            metrics.recordOperationRate(totalFiles, duration, "files");
            metrics.recordMetric("directory_depth", depth);
            metrics.recordMetric("files_per_level", filesPerLevel);
            metrics.recordMetric("total_files", totalFiles);
            metrics.recordMetric("total_size_mb", totalSize / 1024 / 1024);
            
            // Calculate depth impact
            double timePerDepth = (double) duration / depth;
            metrics.recordMetric("time_per_depth_level_ms", timePerDepth);
            
            metrics.finalizeMetrics();
            benchmarkResults.add(metrics);
            
            // Performance assertions - directory depth should not significantly impact performance
            double timePerDepthMs = (Double) metrics.getMetrics().get("time_per_depth_level_ms");
            assertTrue(timePerDepthMs < 50.0, 
                    "Time per directory level should be reasonable: " + String.format("%.2f", timePerDepthMs) + "ms");
            
            // Clean up for next test
            cleanupDirectory(sourceDir);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void benchmarkMemoryUsageScalability() throws Exception {
        // Test how memory usage scales with dataset size
        int[] datasetSizesMB = {50, 100, 200, 500, 1000};
        
        for (int sizeMB : datasetSizesMB) {
            PerformanceMetrics metrics = new PerformanceMetrics("Memory Usage Scalability - " + sizeMB + "MB");
            
            // Create test dataset
            BenchmarkDataGenerator.createMixedDataset(sourceDir, sizeMB);
            long totalSize = calculateTotalSize(sourceDir);
            
            // Measure memory usage during backup
            Runtime runtime = Runtime.getRuntime();
            runtime.gc(); // Suggest garbage collection
            long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
            
            long startTime = System.currentTimeMillis();
            
            BackupOptions backupOptions = new BackupOptions.Builder()
                    .verifyIntegrity(true)
                    .chunkSize(1024 * 1024) // 1MB chunks
                    .build();
            
            CompletableFuture<BackupService.BackupResult> backupFuture =
                    backupService.backup(sourceDir, backupOptions);
            BackupService.BackupResult backupResult = backupFuture.get();
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
            long memoryUsed = memoryAfter - memoryBefore;
            
            // Record metrics
            assertTrue(backupResult.isSuccess(), "Backup should succeed");
            metrics.recordThroughput(totalSize, duration);
            metrics.recordMetric("dataset_size_mb", sizeMB);
            metrics.recordMetric("memory_used_mb", memoryUsed / 1024 / 1024);
            metrics.recordMetric("memory_per_mb", (double) memoryUsed / sizeMB);
            metrics.recordMetric("memory_efficiency", (double) totalSize / memoryUsed);
            
            metrics.finalizeMetrics();
            benchmarkResults.add(metrics);
            
            // Memory usage should scale reasonably
            double memoryPerMB = (Double) metrics.getMetrics().get("memory_per_mb");
            assertTrue(memoryPerMB < 1024 * 1024, // Less than 1GB per GB of data
                    "Memory usage per MB should be reasonable: " + String.format("%.2f", memoryPerMB) + " bytes");
            
            // Clean up for next test
            cleanupDirectory(sourceDir);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void benchmarkRestoreScalability() throws Exception {
        // Test restore scalability with increasing dataset sizes
        int[] datasetSizesMB = {10, 50, 100, 250, 500};
        
        for (int sizeMB : datasetSizesMB) {
            PerformanceMetrics metrics = new PerformanceMetrics("Restore Scalability - " + sizeMB + "MB");
            
            // Create and backup dataset first
            BenchmarkDataGenerator.createMixedDataset(sourceDir, sizeMB);
            long totalSize = calculateTotalSize(sourceDir);
            
            BackupOptions backupOptions = new BackupOptions.Builder()
                    .verifyIntegrity(true)
                    .build();
            
            CompletableFuture<BackupService.BackupResult> backupFuture =
                    backupService.backup(sourceDir, backupOptions);
            BackupService.BackupResult backupResult = backupFuture.get();
            assertTrue(backupResult.isSuccess(), "Backup should succeed");
            
            String snapshotId = backupResult.getSnapshotId();
            
            // Measure restore performance
            long startTime = System.currentTimeMillis();
            
            RestoreOptions restoreOptions = new RestoreOptions.Builder()
                    .overwriteExisting(true)
                    .verifyIntegrity(true)
                    .build();
            
            CompletableFuture<RestoreService.RestoreResult> restoreFuture =
                    restoreService.restore(snapshotId, restoreDir, restoreOptions);
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
            
            // Calculate restore scalability metrics
            double restoreTimePerMB = (double) duration / sizeMB;
            metrics.recordMetric("restore_time_per_mb_ms", restoreTimePerMB);
            
            metrics.finalizeMetrics();
            benchmarkResults.add(metrics);
            
            // Restore should scale reasonably
            double restoreThroughputMBps = (Double) metrics.getMetrics().get("throughput_mbps");
            assertTrue(restoreThroughputMBps > 50.0, 
                    "Restore throughput should be reasonable: " + String.format("%.2f", restoreThroughputMBps) + " MB/s");
            
            // Clean up for next test
            cleanupDirectory(sourceDir);
            cleanupDirectory(restoreDir);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void benchmarkIncrementalBackupScalability() throws Exception {
        // Test scalability of incremental backups over time
        int initialSizeMB = 100;
        int[] snapshotCounts = {1, 5, 10, 20}; // Number of incremental snapshots
        
        for (int snapshotCount : snapshotCounts) {
            PerformanceMetrics metrics = new PerformanceMetrics(
                    "Incremental Backup Scalability - " + snapshotCount + " snapshots");
            
            // Create initial dataset
            BenchmarkDataGenerator.DatasetInfo datasetInfo = 
                    BenchmarkDataGenerator.createIncrementalDataset(sourceDir, initialSizeMB);
            
            // Create initial backup
            BackupOptions backupOptions = new BackupOptions.Builder()
                    .verifyIntegrity(true)
                    .build();
            
            CompletableFuture<BackupService.BackupResult> initialBackupFuture =
                    backupService.backup(sourceDir, backupOptions);
            BackupService.BackupResult initialBackupResult = initialBackupFuture.get();
            assertTrue(initialBackupResult.isSuccess(), "Initial backup should succeed");
            
            // Create incremental backups
            long totalIncrementalTime = 0;
            long totalIncrementalSize = 0;
            
            for (int i = 0; i < snapshotCount; i++) {
                // Modify dataset (small changes)
                BenchmarkDataGenerator.modifyIncrementalDataset(datasetInfo, 0.05, 0.02, 0.01); // 5% modify, 2% add, 1% delete
                
                // Measure incremental backup time
                long incrementalStartTime = System.currentTimeMillis();
                
                CompletableFuture<BackupService.BackupResult> incrementalBackupFuture =
                        backupService.backup(sourceDir, backupOptions);
                BackupService.BackupResult incrementalBackupResult = incrementalBackupFuture.get();
                assertTrue(incrementalBackupResult.isSuccess(), "Incremental backup should succeed");
                
                long incrementalEndTime = System.currentTimeMillis();
                totalIncrementalTime += (incrementalEndTime - incrementalStartTime);
                totalIncrementalSize += incrementalBackupResult.getTotalBytesProcessed();
            }
            
            // Record metrics
            metrics.recordMetric("snapshot_count", snapshotCount);
            metrics.recordMetric("initial_size_mb", initialSizeMB);
            metrics.recordMetric("total_incremental_time_ms", totalIncrementalTime);
            metrics.recordMetric("total_incremental_size_mb", totalIncrementalSize / 1024 / 1024);
            metrics.recordMetric("avg_incremental_time_ms", (double) totalIncrementalTime / snapshotCount);
            metrics.recordMetric("avg_incremental_size_mb", (double) totalIncrementalSize / snapshotCount / 1024 / 1024);
            
            // Calculate incremental efficiency
            double incrementalEfficiency = (double) totalIncrementalSize / (initialSizeMB * 1024L * 1024L * snapshotCount);
            metrics.recordMetric("incremental_efficiency", incrementalEfficiency);
            
            metrics.finalizeMetrics();
            benchmarkResults.add(metrics);
            
            // Incremental backups should be efficient
            double incrementalEfficiencyRatio = (Double) metrics.getMetrics().get("incremental_efficiency");
            assertTrue(incrementalEfficiencyRatio < 0.1, 
                    "Incremental backup should be efficient: " + String.format("%.3f", incrementalEfficiencyRatio));
            
            // Clean up for next test
            cleanupDirectory(sourceDir);
        }
    }

    /**
     * Generates a comprehensive scalability report.
     */
    private void generateScalabilityReport() {
        System.out.println("\n=== SCALABILITY BENCHMARK REPORT ===\n");
        
        // Group results by benchmark type
        benchmarkResults.stream()
            .collect(java.util.stream.Collectors.groupingBy(PerformanceMetrics::getBenchmarkName))
            .forEach((benchmarkType, results) -> {
                System.out.println("### " + benchmarkType + " ###");
                
                // Calculate scalability trends
                List<Double> throughputs = results.stream()
                    .filter(m -> m.getMetrics().containsKey("throughput_mbps"))
                    .map(m -> (Double) m.getMetrics().get("throughput_mbps"))
                    .collect(java.util.stream.Collectors.toList());
                
                List<Double> datasetSizes = results.stream()
                    .filter(m -> m.getMetrics().containsKey("dataset_size_mb"))
                    .map(m -> (Double) m.getMetrics().get("dataset_size_mb"))
                    .collect(java.util.stream.Collectors.toList());
                
                for (PerformanceMetrics metrics : results) {
                    System.out.println(metrics.generateSummary());
                }
                
                // Analyze scalability trend
                if (throughputs.size() > 1) {
                    double firstThroughput = throughputs.get(0);
                    double lastThroughput = throughputs.get(throughputs.size() - 1);
                    double firstSize = datasetSizes.get(0);
                    double lastSize = datasetSizes.get(datasetSizes.size() - 1);
                    
                    double throughputDegradation = (firstThroughput - lastThroughput) / firstThroughput * 100.0;
                    double sizeGrowth = (lastSize - firstSize) / firstSize * 100.0;
                    
                    System.out.println("Scalability Analysis:");
                    System.out.println("  Size Growth: " + String.format("%.1f", sizeGrowth) + "%");
                    System.out.println("  Throughput Degradation: " + String.format("%.1f", throughputDegradation) + "%");
                    
                    if (sizeGrowth > 0) {
                        double degradationPerGrowth = throughputDegradation / sizeGrowth;
                        String scalabilityRating = getScalabilityRating(degradationPerGrowth);
                        System.out.println("  Scalability Rating: " + scalabilityRating);
                    }
                }
                
                System.out.println();
            });
        
        System.out.println("### SCALABILITY SUMMARY ###");
        System.out.println("Total Scalability Tests: " + benchmarkResults.size());
    }
    
    /**
     * Gets a scalability rating based on degradation per growth.
     */
    private String getScalabilityRating(double degradationPerGrowth) {
        if (degradationPerGrowth < 0.1) return "Excellent (<0.1% degradation per 100% growth)";
        if (degradationPerGrowth < 0.5) return "Good (0.1-0.5% degradation per 100% growth)";
        if (degradationPerGrowth < 1.0) return "Fair (0.5-1.0% degradation per 100% growth)";
        if (degradationPerGrowth < 2.0) return "Poor (1.0-2.0% degradation per 100% growth)";
        return "Very Poor (>2.0% degradation per 100% growth)";
    }
    
    /**
     * Calculates total size of files in a directory.
     */
    private long calculateTotalSize(Path directory) throws IOException {
        return Files.walk(directory)
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
    
    /**
     * Counts files in a directory.
     */
    private int countFiles(Path directory) throws IOException {
        return (int) Files.walk(directory)
                .filter(Files::isRegularFile)
                .count();
    }
    
    /**
     * Calculates memory usage per MB of data.
     */
    private double calculateMemoryUsagePerMB(int sizeMB) {
        // Simplified calculation - in real implementation would measure actual usage
        return sizeMB * 1024.0; // Assume 1KB memory per 1MB data
    }
    
    /**
     * Cleans up a directory by removing all files.
     */
    private void cleanupDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walk(directory)
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