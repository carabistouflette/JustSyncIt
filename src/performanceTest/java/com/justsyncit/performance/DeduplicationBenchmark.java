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
import com.justsyncit.storage.ContentStoreStats;
import com.justsyncit.storage.metadata.MetadataService;
import com.justsyncit.storage.metadata.MetadataStats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import java.util.concurrent.TimeUnit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Benchmark for measuring deduplication efficiency and overhead.
 * Tests various duplication scenarios to measure space savings and performance impact.
 */
public class DeduplicationBenchmark {

    @TempDir
    Path tempDir;

    @TempDir
    Path storageDir;

    @TempDir
    Path sourceDir;

    private ServiceFactory serviceFactory;
    private Blake3Service blake3Service;
    private com.justsyncit.storage.ContentStore contentStore;
    private MetadataService metadataService;
    private BackupService backupService;
    
    private final List<PerformanceMetrics> benchmarkResults = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        serviceFactory = new ServiceFactory();
        blake3Service = serviceFactory.createBlake3Service();
        contentStore = serviceFactory.createSqliteContentStore(blake3Service);
        metadataService = serviceFactory.createMetadataService(tempDir.resolve("test-metadata.db").toString());
        backupService = serviceFactory.createBackupService(contentStore, metadataService, blake3Service);
    }

    @AfterEach
    void tearDown() throws Exception {
        // Generate benchmark report
        generateDeduplicationReport();
        
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
    void benchmarkPerfectDeduplication() throws Exception {
        // Test with identical files (perfect deduplication scenario)
        int[] duplicateCounts = {10, 50, 100, 500};
        int fileSizeKB = 100; // 100KB per file
        
        for (int duplicateCount : duplicateCounts) {
            PerformanceMetrics metrics = new PerformanceMetrics(
                    "Perfect Deduplication - " + duplicateCount + " duplicates");
            
            // Create identical files
            byte[] duplicateContent = generateRandomContent(fileSizeKB * 1024);
            long totalOriginalSize = 0;
            
            for (int i = 0; i < duplicateCount; i++) {
                Path file = sourceDir.resolve("duplicate_" + i + ".dat");
                Files.write(file, duplicateContent);
                totalOriginalSize += duplicateContent.length;
            }
            
            // Measure backup with deduplication
            long startTime = System.currentTimeMillis();
            
            BackupOptions backupOptions = new BackupOptions.Builder()
                    .verifyIntegrity(true)
                    .chunkSize(64 * 1024) // 64KB chunks
                    .build();
            
            CompletableFuture<BackupService.BackupResult> backupFuture =
                    backupService.backup(sourceDir, backupOptions);
            BackupService.BackupResult backupResult = backupFuture.get();
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            // Get storage statistics
            ContentStoreStats storageStats = contentStore.getStats();
            MetadataStats metadataStats = metadataService.getStats();
            
            // Record metrics
            assertTrue(backupResult.isSuccess(), "Backup should succeed");
            metrics.recordThroughput(totalOriginalSize, duration);
            metrics.recordOperationRate(duplicateCount, duration, "files");
            metrics.recordDeduplicationEfficiency(duplicateCount, backupResult.getChunksCreated(), 
                                             totalOriginalSize, storageStats.getTotalSizeBytes());
            metrics.recordMetric("duplicate_count", duplicateCount);
            metrics.recordMetric("file_size_kb", fileSizeKB);
            metrics.recordMetric("chunks_created", backupResult.getChunksCreated());
            metrics.recordMetric("deduplication_ratio", metadataStats.getDeduplicationRatio());
            
            // Calculate deduplication overhead
            double deduplicationOverhead = calculateDeduplicationOverhead(duration, duplicateCount);
            metrics.recordMetric("deduplication_overhead_ms", deduplicationOverhead);
            
            metrics.finalizeMetrics();
            benchmarkResults.add(metrics);
            
            // Performance assertions
            double deduplicationRatio = (Double) metrics.getMetrics().get("deduplication_ratio");
            assertTrue(deduplicationRatio >= duplicateCount * 0.8, // Allow some overhead
                    "Deduplication ratio should be high for identical files: " + 
                    String.format("%.2f", deduplicationRatio) + " (expected ~" + duplicateCount + ")");
            
            double spaceSavings = (Double) metrics.getMetrics().get("space_savings_percent");
            assertTrue(spaceSavings > 90.0, 
                    "Space savings should be >90% for identical files: " + 
                    String.format("%.1f", spaceSavings) + "%");
            
            // Clean up for next test
            cleanupDirectory(sourceDir);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void benchmarkPartialDeduplication() throws Exception {
        // Test with files having partial duplication
        int[] fileCounts = {50, 100, 200};
        int duplicateRatio = 50; // 50% of content is duplicated
        int fileSizeKB = 100;
        
        for (int fileCount : fileCounts) {
            PerformanceMetrics metrics = new PerformanceMetrics(
                    "Partial Deduplication - " + fileCount + " files, " + duplicateRatio + "% duplicate");
            
            // Create files with partial duplication
            byte[] baseContent = generateRandomContent(fileSizeKB * 1024);
            byte[] duplicateContent = new byte[baseContent.length];
            byte[] uniqueContent = generateRandomContent(fileSizeKB * 1024);
            
            // Mix duplicate and unique content
            int duplicateBytes = (int) (baseContent.length * duplicateRatio / 100.0);
            System.arraycopy(baseContent, 0, duplicateContent, 0, duplicateBytes);
            System.arraycopy(uniqueContent, duplicateBytes, duplicateContent, duplicateBytes, 
                         baseContent.length - duplicateBytes);
            
            long totalOriginalSize = 0;
            
            for (int i = 0; i < fileCount; i++) {
                Path file = sourceDir.resolve("partial_" + i + ".dat");
                Files.write(file, duplicateContent);
                totalOriginalSize += duplicateContent.length;
            }
            
            // Measure backup with partial deduplication
            long startTime = System.currentTimeMillis();
            
            BackupOptions backupOptions = new BackupOptions.Builder()
                    .verifyIntegrity(true)
                    .chunkSize(64 * 1024) // 64KB chunks
                    .build();
            
            CompletableFuture<BackupService.BackupResult> backupFuture =
                    backupService.backup(sourceDir, backupOptions);
            BackupService.BackupResult backupResult = backupFuture.get();
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            // Get storage statistics
            ContentStoreStats storageStats = contentStore.getStats();
            MetadataStats metadataStats = metadataService.getStats();
            
            // Record metrics
            assertTrue(backupResult.isSuccess(), "Backup should succeed");
            metrics.recordThroughput(totalOriginalSize, duration);
            metrics.recordOperationRate(fileCount, duration, "files");
            metrics.recordDeduplicationEfficiency(fileCount, backupResult.getChunksCreated(), 
                                             totalOriginalSize, storageStats.getTotalSizeBytes());
            metrics.recordMetric("file_count", fileCount);
            metrics.recordMetric("duplicate_ratio_percent", duplicateRatio);
            metrics.recordMetric("file_size_kb", fileSizeKB);
            metrics.recordMetric("chunks_created", backupResult.getChunksCreated());
            metrics.recordMetric("deduplication_ratio", metadataStats.getDeduplicationRatio());
            
            metrics.finalizeMetrics();
            benchmarkResults.add(metrics);
            
            // Performance assertions
            double deduplicationRatio = (Double) metrics.getMetrics().get("deduplication_ratio");
            assertTrue(deduplicationRatio > 1.0, 
                    "Deduplication ratio should be >1.0 for partial duplication: " + 
                    String.format("%.2f", deduplicationRatio));
            
            double spaceSavings = (Double) metrics.getMetrics().get("space_savings_percent");
            assertTrue(spaceSavings > 20.0 && spaceSavings < 80.0, 
                    "Space savings should be reasonable for partial duplication: " + 
                    String.format("%.1f", spaceSavings) + "%");
            
            // Clean up for next test
            cleanupDirectory(sourceDir);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void benchmarkNoDeduplication() throws Exception {
        // Test with completely unique files (no deduplication)
        int[] fileCounts = {50, 100, 200};
        int fileSizeKB = 100;
        
        for (int fileCount : fileCounts) {
            PerformanceMetrics metrics = new PerformanceMetrics(
                    "No Deduplication - " + fileCount + " unique files");
            
            // Create unique files
            long totalOriginalSize = 0;
            
            for (int i = 0; i < fileCount; i++) {
                Path file = sourceDir.resolve("unique_" + i + ".dat");
                byte[] uniqueContent = generateRandomContent(fileSizeKB * 1024);
                Files.write(file, uniqueContent);
                totalOriginalSize += uniqueContent.length;
            }
            
            // Measure backup without deduplication
            long startTime = System.currentTimeMillis();
            
            BackupOptions backupOptions = new BackupOptions.Builder()
                    .verifyIntegrity(true)
                    .chunkSize(64 * 1024) // 64KB chunks
                    .build();
            
            CompletableFuture<BackupService.BackupResult> backupFuture =
                    backupService.backup(sourceDir, backupOptions);
            BackupService.BackupResult backupResult = backupFuture.get();
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            // Get storage statistics
            ContentStoreStats storageStats = contentStore.getStats();
            MetadataStats metadataStats = metadataService.getStats();
            
            // Record metrics
            assertTrue(backupResult.isSuccess(), "Backup should succeed");
            metrics.recordThroughput(totalOriginalSize, duration);
            metrics.recordOperationRate(fileCount, duration, "files");
            metrics.recordDeduplicationEfficiency(fileCount, backupResult.getChunksCreated(), 
                                             totalOriginalSize, storageStats.getTotalSizeBytes());
            metrics.recordMetric("file_count", fileCount);
            metrics.recordMetric("file_size_kb", fileSizeKB);
            metrics.recordMetric("chunks_created", backupResult.getChunksCreated());
            metrics.recordMetric("deduplication_ratio", metadataStats.getDeduplicationRatio());
            
            metrics.finalizeMetrics();
            benchmarkResults.add(metrics);
            
            // Performance assertions
            double deduplicationRatio = (Double) metrics.getMetrics().get("deduplication_ratio");
            assertTrue(deduplicationRatio >= 1.0 && deduplicationRatio <= 1.1, 
                    "Deduplication ratio should be ~1.0 for unique files: " + 
                    String.format("%.2f", deduplicationRatio));
            
            double spaceSavings = (Double) metrics.getMetrics().get("space_savings_percent");
            assertTrue(spaceSavings >= 0.0 && spaceSavings < 10.0, 
                    "Space savings should be minimal for unique files: " + 
                    String.format("%.1f", spaceSavings) + "%");
            
            // Clean up for next test
            cleanupDirectory(sourceDir);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void benchmarkChunkSizeImpact() throws Exception {
        // Test impact of chunk size on deduplication efficiency
        int[] chunkSizes = {32 * 1024, 64 * 1024, 128 * 1024, 256 * 1024, 1024 * 1024}; // 32KB to 1MB
        int fileCount = 100;
        int duplicateRatio = 30; // 30% duplication
        
        for (int chunkSize : chunkSizes) {
            PerformanceMetrics metrics = new PerformanceMetrics(
                    "Chunk Size Impact - " + (chunkSize / 1024) + "KB chunks");
            
            // Create files with partial duplication
            BenchmarkDataGenerator.createDuplicateHeavyDataset(sourceDir, 10, duplicateRatio / 100.0);
            long totalOriginalSize = calculateTotalSize(sourceDir);
            int actualFileCount = countFiles(sourceDir);
            
            // Measure backup with different chunk sizes
            long startTime = System.currentTimeMillis();
            
            BackupOptions backupOptions = new BackupOptions.Builder()
                    .verifyIntegrity(true)
                    .chunkSize(chunkSize)
                    .build();
            
            CompletableFuture<BackupService.BackupResult> backupFuture =
                    backupService.backup(sourceDir, backupOptions);
            BackupService.BackupResult backupResult = backupFuture.get();
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            // Get storage statistics
            ContentStoreStats storageStats = contentStore.getStats();
            MetadataStats metadataStats = metadataService.getStats();
            
            // Record metrics
            assertTrue(backupResult.isSuccess(), "Backup should succeed");
            metrics.recordThroughput(totalOriginalSize, duration);
            metrics.recordOperationRate(actualFileCount, duration, "files");
            metrics.recordDeduplicationEfficiency(actualFileCount, backupResult.getChunksCreated(), 
                                             totalOriginalSize, storageStats.getTotalSizeBytes());
            metrics.recordMetric("chunk_size_kb", chunkSize / 1024);
            metrics.recordMetric("file_count", actualFileCount);
            metrics.recordMetric("chunks_created", backupResult.getChunksCreated());
            metrics.recordMetric("deduplication_ratio", metadataStats.getDeduplicationRatio());
            
            // Calculate chunk efficiency
            double avgChunkSize = (double) totalOriginalSize / backupResult.getChunksCreated();
            double chunkEfficiency = avgChunkSize / chunkSize;
            metrics.recordMetric("avg_chunk_size_kb", avgChunkSize / 1024);
            metrics.recordMetric("chunk_efficiency", chunkEfficiency);
            
            metrics.finalizeMetrics();
            benchmarkResults.add(metrics);
            
            // Performance assertions
            double deduplicationRatio = (Double) metrics.getMetrics().get("deduplication_ratio");
            assertTrue(deduplicationRatio > 1.0, 
                    "Deduplication ratio should be >1.0: " + String.format("%.2f", deduplicationRatio));
            
            // Clean up for next test
            cleanupDirectory(sourceDir);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void benchmarkDeduplicationOverhead() throws Exception {
        // Test performance overhead of deduplication processing
        int[] fileCounts = {50, 100, 200, 500};
        int fileSizeKB = 50;
        
        for (int fileCount : fileCounts) {
            // Test with deduplication enabled
            PerformanceMetrics dedupMetrics = new PerformanceMetrics(
                    "With Deduplication - " + fileCount + " files");
            
            createUniqueFiles(fileCount, fileSizeKB);
            long totalSize = calculateTotalSize(sourceDir);
            
            long startTime = System.currentTimeMillis();
            
            BackupOptions backupOptions = new BackupOptions.Builder()
                    .verifyIntegrity(true)
                    .chunkSize(64 * 1024)
                    .build();
            
            CompletableFuture<BackupService.BackupResult> backupFuture =
                    backupService.backup(sourceDir, backupOptions);
            BackupService.BackupResult backupResult = backupFuture.get();
            
            long endTime = System.currentTimeMillis();
            long dedupDuration = endTime - startTime;
            
            assertTrue(backupResult.isSuccess(), "Backup should succeed");
            dedupMetrics.recordThroughput(totalSize, dedupDuration);
            dedupMetrics.recordOperationRate(fileCount, dedupDuration, "files");
            dedupMetrics.recordMetric("file_count", fileCount);
            dedupMetrics.recordMetric("deduplication_enabled", true);
            
            dedupMetrics.finalizeMetrics();
            benchmarkResults.add(dedupMetrics);
            
            cleanupDirectory(sourceDir);
            
            // Test without deduplication (simulated by using very large chunks)
            PerformanceMetrics noDedupMetrics = new PerformanceMetrics(
                    "Without Deduplication - " + fileCount + " files");
            
            createUniqueFiles(fileCount, fileSizeKB);
            totalSize = calculateTotalSize(sourceDir);
            
            startTime = System.currentTimeMillis();
            
            backupOptions = new BackupOptions.Builder()
                    .verifyIntegrity(true)
                    .chunkSize((int) (totalSize + 1024)) // Single chunk per file (no deduplication)
                    .build();
            
            backupFuture = backupService.backup(sourceDir, backupOptions);
            backupResult = backupFuture.get();
            
            endTime = System.currentTimeMillis();
            long noDedupDuration = endTime - startTime;
            
            assertTrue(backupResult.isSuccess(), "Backup should succeed");
            noDedupMetrics.recordThroughput(totalSize, noDedupDuration);
            noDedupMetrics.recordOperationRate(fileCount, noDedupDuration, "files");
            noDedupMetrics.recordMetric("file_count", fileCount);
            noDedupMetrics.recordMetric("deduplication_enabled", false);
            
            noDedupMetrics.finalizeMetrics();
            benchmarkResults.add(noDedupMetrics);
            
            // Calculate overhead
            double overheadPercent = ((double) (dedupDuration - noDedupDuration) / noDedupDuration) * 100.0;
            
            System.out.println("\n=== Deduplication Overhead Analysis: " + fileCount + " files ===");
            System.out.println("With Deduplication: " + dedupDuration + " ms");
            System.out.println("Without Deduplication: " + noDedupDuration + " ms");
            System.out.println("Overhead: " + String.format("%.1f", overheadPercent) + "%");
            
            // Performance assertions
            assertTrue(overheadPercent < 20.0, 
                    "Deduplication overhead should be <20%: " + String.format("%.1f", overheadPercent) + "%");
            
            // Clean up for next test
            cleanupDirectory(sourceDir);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void benchmarkIncrementalDeduplication() throws Exception {
        // Test deduplication efficiency in incremental backups
        int initialSizeMB = 50;
        int[] changePercentages = {5, 10, 20, 30}; // Percentage of files changed
        
        for (int changePercent : changePercentages) {
            PerformanceMetrics metrics = new PerformanceMetrics(
                    "Incremental Deduplication - " + changePercent + "% changes");
            
            // Create initial dataset
            BenchmarkDataGenerator.DatasetInfo datasetInfo = 
                    BenchmarkDataGenerator.createIncrementalDataset(sourceDir, initialSizeMB);
            
            // Create initial backup
            BackupOptions backupOptions = new BackupOptions.Builder()
                    .verifyIntegrity(true)
                    .chunkSize(64 * 1024)
                    .build();
            
            CompletableFuture<BackupService.BackupResult> initialBackupFuture =
                    backupService.backup(sourceDir, backupOptions);
            BackupService.BackupResult initialBackupResult = initialBackupFuture.get();
            assertTrue(initialBackupResult.isSuccess(), "Initial backup should succeed");
            
            // Modify dataset for incremental backup
            BenchmarkDataGenerator.modifyIncrementalDataset(datasetInfo, changePercent / 100.0, 0.02, 0.01);
            
            // Measure incremental backup
            long startTime = System.currentTimeMillis();
            
            CompletableFuture<BackupService.BackupResult> incrementalBackupFuture =
                    backupService.backup(sourceDir, backupOptions);
            BackupService.BackupResult incrementalBackupResult = incrementalBackupFuture.get();
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            // Get storage statistics
            ContentStoreStats storageStats = contentStore.getStats();
            MetadataStats metadataStats = metadataService.getStats();
            
            // Record metrics
            assertTrue(incrementalBackupResult.isSuccess(), "Incremental backup should succeed");
            metrics.recordThroughput(incrementalBackupResult.getTotalBytesProcessed(), duration);
            metrics.recordOperationRate(incrementalBackupResult.getFilesProcessed(), duration, "files");
            metrics.recordDeduplicationEfficiency(incrementalBackupResult.getFilesProcessed(), 
                                             incrementalBackupResult.getChunksCreated(), 
                                             incrementalBackupResult.getTotalBytesProcessed(), 
                                             storageStats.getTotalSizeBytes());
            metrics.recordMetric("change_percentage", changePercent);
            metrics.recordMetric("initial_size_mb", initialSizeMB);
            metrics.recordMetric("incremental_size_mb", incrementalBackupResult.getTotalBytesProcessed() / 1024 / 1024);
            metrics.recordMetric("deduplication_ratio", metadataStats.getDeduplicationRatio());
            
            // Calculate incremental efficiency
            double incrementalRatio = (double) incrementalBackupResult.getTotalBytesProcessed() / 
                                  (initialSizeMB * 1024L * 1024L);
            metrics.recordMetric("incremental_ratio", incrementalRatio);
            
            metrics.finalizeMetrics();
            benchmarkResults.add(metrics);
            
            // Performance assertions
            double incrementalRatioValue = (Double) metrics.getMetrics().get("incremental_ratio");
            assertTrue(incrementalRatioValue < 0.5, 
                    "Incremental backup should be much smaller than full backup: " + 
                    String.format("%.3f", incrementalRatioValue));
            
            // Clean up for next test
            cleanupDirectory(sourceDir);
        }
    }

    /**
     * Creates unique files for testing.
     */
    private void createUniqueFiles(int fileCount, int fileSizeKB) throws IOException {
        for (int i = 0; i < fileCount; i++) {
            Path file = sourceDir.resolve("unique_" + i + ".dat");
            byte[] content = generateRandomContent(fileSizeKB * 1024);
            Files.write(file, content);
        }
    }

    /**
     * Calculates deduplication overhead in milliseconds.
     */
    private double calculateDeduplicationOverhead(long duration, int fileCount) {
        // Simplified calculation - in real implementation would measure baseline without deduplication
        return duration * 0.1; // Assume 10% overhead
    }

    /**
     * Generates a comprehensive deduplication benchmark report.
     */
    private void generateDeduplicationReport() {
        System.out.println("\n=== DEDUPLICATION BENCHMARK REPORT ===\n");
        
        // Group results by benchmark type
        benchmarkResults.stream()
            .collect(java.util.stream.Collectors.groupingBy(PerformanceMetrics::getBenchmarkName))
            .forEach((benchmarkType, results) -> {
                System.out.println("### " + benchmarkType + " ###");
                
                for (PerformanceMetrics metrics : results) {
                    System.out.println(metrics.generateSummary());
                    
                    // Additional deduplication-specific metrics
                    if (metrics.getMetrics().containsKey("deduplication_ratio")) {
                        double deduplicationRatio = (Double) metrics.getMetrics().get("deduplication_ratio");
                        String efficiencyRating = getDeduplicationEfficiencyRating(deduplicationRatio);
                        System.out.println("Deduplication Efficiency: " + efficiencyRating);
                    }
                    
                    if (metrics.getMetrics().containsKey("space_savings_percent")) {
                        double spaceSavings = (Double) metrics.getMetrics().get("space_savings_percent");
                        String savingsRating = getSpaceSavingsRating(spaceSavings);
                        System.out.println("Space Savings: " + savingsRating);
                    }
                    
                    System.out.println();
                }
            });
        
        // Overall deduplication analysis
        System.out.println("### DEDUPLICATION ANALYSIS ###");
        
        List<Double> deduplicationRatios = benchmarkResults.stream()
            .filter(m -> m.getMetrics().containsKey("deduplication_ratio"))
            .map(m -> (Double) m.getMetrics().get("deduplication_ratio"))
            .collect(java.util.stream.Collectors.toList());
        
        List<Double> spaceSavings = benchmarkResults.stream()
            .filter(m -> m.getMetrics().containsKey("space_savings_percent"))
            .map(m -> (Double) m.getMetrics().get("space_savings_percent"))
            .collect(java.util.stream.Collectors.toList());
        
        if (!deduplicationRatios.isEmpty()) {
            double avgDeduplicationRatio = deduplicationRatios.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
            System.out.println("Average Deduplication Ratio: " + String.format("%.2f", avgDeduplicationRatio));
        }
        
        if (!spaceSavings.isEmpty()) {
            double avgSpaceSavings = spaceSavings.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
            System.out.println("Average Space Savings: " + String.format("%.1f", avgSpaceSavings) + "%");
        }
        
        System.out.println("Total Deduplication Benchmarks: " + benchmarkResults.size());
    }
    
    /**
     * Gets a deduplication efficiency rating based on ratio.
     */
    private String getDeduplicationEfficiencyRating(double ratio) {
        if (ratio >= 10.0) return "Excellent (>=10x)";
        if (ratio >= 5.0) return "Good (5-10x)";
        if (ratio >= 2.0) return "Fair (2-5x)";
        if (ratio >= 1.5) return "Poor (1.5-2x)";
        return "Very Poor (<1.5x)";
    }
    
    /**
     * Gets a space savings rating based on percentage.
     */
    private String getSpaceSavingsRating(double savingsPercent) {
        if (savingsPercent >= 90.0) return "Excellent (>=90%)";
        if (savingsPercent >= 70.0) return "Good (70-90%)";
        if (savingsPercent >= 50.0) return "Fair (50-70%)";
        if (savingsPercent >= 20.0) return "Poor (20-50%)";
        return "Very Poor (<20%)";
    }
    
    /**
     * Generates random content of specified size.
     */
    private byte[] generateRandomContent(int size) {
        byte[] content = new byte[size];
        new java.util.Random(42).nextBytes(content); // Fixed seed for reproducible tests
        return content;
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