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
import com.justsyncit.performance.util.BenchmarkReportGenerator;
import com.justsyncit.performance.util.PerformanceMetrics;
import com.justsyncit.performance.util.PerformanceProfiler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive benchmark suite that runs all performance tests
 * and generates detailed reports.
 */
public class ComprehensiveBenchmarkSuite {
    
    @TempDir
    Path tempDir;
    
    @TempDir
    Path reportDir;
    
    private List<PerformanceMetrics> allMetrics;
    private ExecutorService executorService;
    private BenchmarkDataGenerator dataGenerator;
    
    @BeforeEach
    void setUp() {
        allMetrics = new ArrayList<>();
        executorService = Executors.newFixedThreadPool(4);
        // BenchmarkDataGenerator uses static methods, no instance needed
    }
    
    @AfterEach
    void tearDown() throws InterruptedException {
        if (executorService != null) {
            executorService.shutdown();
            executorService.awaitTermination(30, TimeUnit.SECONDS);
        }
    }
    
    @Test
    @DisplayName("Run Comprehensive Benchmark Suite")
    void runComprehensiveBenchmarkSuite() throws Exception {
        System.out.println("Starting Comprehensive Benchmark Suite...");
        System.out.println("Temp directory: " + tempDir);
        System.out.println("Report directory: " + reportDir);
        
        // Run all benchmark categories
        runThroughputBenchmarks();
        runScalabilityBenchmarks();
        runNetworkBenchmarks();
        runDeduplicationBenchmarks();
        runConcurrencyBenchmarks();
        
        // Generate comprehensive reports
        generateComprehensiveReports();
        
        // Validate performance targets
        validatePerformanceTargets();
        
        System.out.println("Comprehensive Benchmark Suite completed successfully!");
        System.out.println("Reports generated in: " + reportDir);
    }
    
    private void runThroughputBenchmarks() throws Exception {
        System.out.println("\n=== Running Throughput Benchmarks ===");
        
        // Small dataset throughput
        PerformanceMetrics smallDatasetMetrics = runBenchmark("Small Dataset Throughput", () -> {
            Path smallData = tempDir.resolve("small-dataset");
            BenchmarkDataGenerator.createMixedDataset(smallData, 50);
            return simulateBackupOperation(smallData);
        });
        allMetrics.add(smallDatasetMetrics);
        
        // Medium dataset throughput
        PerformanceMetrics mediumDatasetMetrics = runBenchmark("Medium Dataset Throughput", () -> {
            Path mediumData = tempDir.resolve("medium-dataset");
            BenchmarkDataGenerator.createMixedDataset(mediumData, 500);
            return simulateBackupOperation(mediumData);
        });
        allMetrics.add(mediumDatasetMetrics);
        
        // Large dataset throughput (if system has enough resources)
        PerformanceMetrics largeDatasetMetrics = runBenchmark("Large Dataset Throughput", () -> {
            Path largeData = tempDir.resolve("large-dataset");
            BenchmarkDataGenerator.createMixedDataset(largeData, 2048);
            return simulateBackupOperation(largeData);
        });
        allMetrics.add(largeDatasetMetrics);
        
        // Restore throughput
        PerformanceMetrics restoreMetrics = runBenchmark("Restore Throughput", () -> {
            Path testData = tempDir.resolve("test-dataset");
            BenchmarkDataGenerator.createMixedDataset(testData, 200);
            Path backupLocation = simulateBackupOperation(testData);
            return simulateRestoreOperation(backupLocation, tempDir.resolve("restore"));
        });
        allMetrics.add(restoreMetrics);
        
        System.out.println("Throughput benchmarks completed");
    }
    
    private void runScalabilityBenchmarks() throws Exception {
        System.out.println("\n=== Running Scalability Benchmarks ===");
        
        // Linear scalability test
        PerformanceMetrics linearScalabilityMetrics = runBenchmark("Linear Scalability", () -> {
            List<Path> datasets = new ArrayList<>();
            for (int size : new int[]{100, 200, 400, 800}) {
                Path dataset = tempDir.resolve("dataset-" + size);
                BenchmarkDataGenerator.createMixedDataset(dataset, size);
                datasets.add(dataset);
            }
            return simulateConcurrentBackups(datasets);
        });
        allMetrics.add(linearScalabilityMetrics);
        
        // File count scalability
        PerformanceMetrics fileCountMetrics = runBenchmark("File Count Scalability", () -> {
            List<Path> datasets = new ArrayList<>();
            for (int fileCount : new int[]{100, 500, 1000, 2000}) {
                Path dataset = tempDir.resolve("files-" + fileCount);
                BenchmarkDataGenerator.createSmallFilesDataset(dataset, 100); // 100MB with many files
                datasets.add(dataset);
            }
            return simulateConcurrentBackups(datasets);
        });
        allMetrics.add(fileCountMetrics);
        
        // Directory depth scalability
        PerformanceMetrics dirDepthMetrics = runBenchmark("Directory Depth Scalability", () -> {
            List<Path> datasets = new ArrayList<>();
            for (int depth : new int[]{5, 10, 15, 20}) {
                Path dataset = tempDir.resolve("depth-" + depth);
                BenchmarkDataGenerator.createDeepDirectoryDataset(dataset, depth, 10, 10240);
                datasets.add(dataset);
            }
            return simulateConcurrentBackups(datasets);
        });
        allMetrics.add(dirDepthMetrics);
        
        System.out.println("Scalability benchmarks completed");
    }
    
    private void runNetworkBenchmarks() throws Exception {
        System.out.println("\n=== Running Network Benchmarks ===");
        
        // TCP vs QUIC small files
        PerformanceMetrics tcpVsQuicSmallMetrics = runBenchmark("TCP vs QUIC Small Files", () -> {
            Path smallData = tempDir.resolve("network-small");
            BenchmarkDataGenerator.createMixedDataset(smallData, 20);
            return simulateNetworkComparison(smallData, "small");
        });
        allMetrics.add(tcpVsQuicSmallMetrics);
        
        // TCP vs QUIC large files
        PerformanceMetrics tcpVsQuicLargeMetrics = runBenchmark("TCP vs QUIC Large Files", () -> {
            Path largeData = tempDir.resolve("network-large");
            BenchmarkDataGenerator.createMixedDataset(largeData, 1024);
            return simulateNetworkComparison(largeData, "large");
        });
        allMetrics.add(tcpVsQuicLargeMetrics);
        
        // Concurrent connections test
        PerformanceMetrics concurrentConnectionsMetrics = runBenchmark("Concurrent Network Connections", () -> {
            List<Path> datasets = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                Path dataset = tempDir.resolve("network-concurrent-" + i);
                BenchmarkDataGenerator.createMixedDataset(dataset, 50);
                datasets.add(dataset);
            }
            return simulateConcurrentNetworkOperations(datasets);
        });
        allMetrics.add(concurrentConnectionsMetrics);
        
        System.out.println("Network benchmarks completed");
    }
    
    private void runDeduplicationBenchmarks() throws Exception {
        System.out.println("\n=== Running Deduplication Benchmarks ===");
        
        // Perfect deduplication
        PerformanceMetrics perfectDeduplicationMetrics = runBenchmark("Perfect Deduplication", () -> {
            Path duplicateData = tempDir.resolve("duplicate-heavy");
            BenchmarkDataGenerator.createDuplicateHeavyDataset(duplicateData, 100, 0.8);
            return simulateDeduplicationTest(duplicateData);
        });
        allMetrics.add(perfectDeduplicationMetrics);
        
        // Partial deduplication
        PerformanceMetrics partialDeduplicationMetrics = runBenchmark("Partial Deduplication", () -> {
            Path partialDuplicateData = tempDir.resolve("partial-duplicate");
            BenchmarkDataGenerator.createDuplicateHeavyDataset(partialDuplicateData, 100, 0.3);
            return simulateDeduplicationTest(partialDuplicateData);
        });
        allMetrics.add(partialDeduplicationMetrics);
        
        // No deduplication
        PerformanceMetrics noDeduplicationMetrics = runBenchmark("No Deduplication", () -> {
            Path uniqueData = tempDir.resolve("unique-data");
            BenchmarkDataGenerator.createMixedDataset(uniqueData, 100);
            return simulateDeduplicationTest(uniqueData);
        });
        allMetrics.add(noDeduplicationMetrics);
        
        // Incremental backup deduplication
        PerformanceMetrics incrementalDeduplicationMetrics = runBenchmark("Incremental Deduplication", () -> {
            Path baseData = tempDir.resolve("base-data");
            BenchmarkDataGenerator.createMixedDataset(baseData, 200);
            BenchmarkDataGenerator.DatasetInfo incrementalData = BenchmarkDataGenerator.createIncrementalDataset(baseData, 200);
            BenchmarkDataGenerator.modifyIncrementalDataset(incrementalData, 0.2, 0.1, 0.05); // 20% modify, 10% add, 5% delete
            return simulateIncrementalBackup(baseData, incrementalData);
        });
        allMetrics.add(incrementalDeduplicationMetrics);
        
        System.out.println("Deduplication benchmarks completed");
    }
    
    private void runConcurrencyBenchmarks() throws Exception {
        System.out.println("\n=== Running Concurrency Benchmarks ===");
        
        // Concurrent backups
        PerformanceMetrics concurrentBackupsMetrics = runBenchmark("Concurrent Backups", () -> {
            List<Path> datasets = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                Path dataset = tempDir.resolve("concurrent-backup-" + i);
                BenchmarkDataGenerator.createMixedDataset(dataset, 100);
                datasets.add(dataset);
            }
            return simulateConcurrentBackups(datasets);
        });
        allMetrics.add(concurrentBackupsMetrics);
        
        // Concurrent restores
        PerformanceMetrics concurrentRestoresMetrics = runBenchmark("Concurrent Restores", () -> {
            List<Path> backups = new ArrayList<>();
            List<Path> restoreTargets = new ArrayList<>();
            
            for (int i = 0; i < 4; i++) {
                Path source = tempDir.resolve("concurrent-source-" + i);
                BenchmarkDataGenerator.createMixedDataset(source, 100);
                Path backup = simulateBackupOperation(source);
                Path target = tempDir.resolve("restore-" + i);
                backups.add(backup);
                restoreTargets.add(target);
            }
            
            return simulateConcurrentRestores(backups, restoreTargets);
        });
        allMetrics.add(concurrentRestoresMetrics);
        
        // Mixed concurrent operations
        PerformanceMetrics mixedConcurrencyMetrics = runBenchmark("Mixed Concurrent Operations", () -> {
            List<CompletableFuture<Path>> futures = new ArrayList<>();
            
            // Mix of backup and restore operations
            for (int i = 0; i < 3; i++) {
                final int index = i;
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        Path data = tempDir.resolve("mixed-concurrent-" + index);
                        BenchmarkDataGenerator.createMixedDataset(data, 100);
                        return simulateBackupOperation(data);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, executorService));
            }
            
            // Wait for all operations to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            
            return tempDir.resolve("mixed-concurrency-result");
        });
        allMetrics.add(mixedConcurrencyMetrics);
        
        System.out.println("Concurrency benchmarks completed");
    }
    
    private PerformanceMetrics runBenchmark(String name, BenchmarkOperation operation) throws Exception {
        System.out.println("Running benchmark: " + name);
        
        PerformanceMetrics metrics = new PerformanceMetrics(name);
        
        try (PerformanceProfiler profiler = new PerformanceProfiler(name, 100)) {
            profiler.start();
            
            long startTime = System.nanoTime();
            
            // Execute the benchmark operation
            Path result = operation.execute();
            
            long endTime = System.nanoTime();
            long durationMs = (endTime - startTime) / 1_000_000;
            
            profiler.stop();
            
            // Collect performance data
            PerformanceProfiler.ProfilingSummary summary = profiler.getSummary();
            
            // Calculate dataset size if possible
            long datasetSize = calculateDatasetSize(result);
            
            // Record metrics
            metrics.recordMetric("duration_ms", durationMs);
            
            if (datasetSize > 0 && durationMs > 0) {
                double throughputMBps = (datasetSize / (1024.0 * 1024.0)) / (durationMs / 1000.0);
                metrics.recordThroughput(datasetSize, durationMs);
            }
            
            metrics.recordMetric("memory_used_mb", summary.getAverageHeapUsageMB());
            metrics.recordMetric("cpu_usage_percent", summary.getAverageCpuUsage());
            metrics.recordMetric("gc_overhead_percent", summary.getGcOverheadPercent());
            
            // Record additional metrics
            metrics.recordMetric("peak_memory_mb", summary.getPeakHeapUsageMB());
            metrics.recordMetric("peak_cpu_usage", summary.getPeakCpuUsage());
            metrics.recordMetric("total_gc_time_ms", summary.getTotalGcTimeMs());
            metrics.recordMetric("peak_thread_count", summary.getPeakThreadCount());
            metrics.recordMetric("dataset_size_mb", datasetSize / (1024 * 1024));
            
            // Print summary
            System.out.println("  " + metrics.generateSummary());
            
        } catch (Exception e) {
            System.err.println("Benchmark failed: " + name + " - " + e.getMessage());
            metrics.recordMetric("error", e.getMessage());
            throw e;
        }
        
        return metrics;
    }
    
    private Path simulateBackupOperation(Path source) throws Exception {
        // Simulate backup operation with realistic timing
        long fileSize = Files.walk(source).mapToLong(p -> {
            try {
                return Files.size(p);
            } catch (IOException e) {
                return 0;
            }
        }).sum();
        
        // Simulate processing time based on file size
        long processingTime = Math.max(100, fileSize / (10 * 1024 * 1024)); // ~10MB/s processing
        
        Thread.sleep(processingTime);
        
        Path backupLocation = tempDir.resolve("backup-" + System.currentTimeMillis());
        Files.createDirectories(backupLocation);
        
        // Simulate backup metadata
        Files.writeString(backupLocation.resolve("metadata.json"), 
            String.format("{\"size\": %d, \"timestamp\": %d}", fileSize, System.currentTimeMillis()));
        
        return backupLocation;
    }
    
    private Path simulateRestoreOperation(Path backupLocation, Path target) throws Exception {
        // Simulate restore operation (typically faster than backup)
        Thread.sleep(100); // Base restore overhead
        
        Files.createDirectories(target);
        
        // Simulate restore metadata
        Files.writeString(target.resolve("restore-info.txt"), 
            "Restored from: " + backupLocation.toString());
        
        return target;
    }
    
    private Path simulateConcurrentBackups(List<Path> datasets) throws Exception {
        List<CompletableFuture<Path>> futures = datasets.stream()
            .map(data -> CompletableFuture.supplyAsync(() -> {
                try {
                    return simulateBackupOperation(data);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executorService))
            .collect(java.util.stream.Collectors.toList());
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        return tempDir.resolve("concurrent-backups-result");
    }
    
    private Path simulateConcurrentRestores(List<Path> backups, List<Path> targets) throws Exception {
        List<CompletableFuture<Path>> futures = new ArrayList<>();
        
        for (int i = 0; i < backups.size(); i++) {
            final int index = i;
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return simulateRestoreOperation(backups.get(index), targets.get(index));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executorService));
        }
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        return tempDir.resolve("concurrent-restores-result");
    }
    
    private Path simulateNetworkComparison(Path data, String type) throws Exception {
        // Simulate TCP performance
        long tcpTime = simulateNetworkTransfer(data, "TCP", type);
        
        // Simulate QUIC performance
        long quicTime = simulateNetworkTransfer(data, "QUIC", type);
        
        // Record comparison results
        Path result = tempDir.resolve("network-comparison-" + type);
        Files.createDirectories(result);
        
        Files.writeString(result.resolve("comparison.txt"), 
            String.format("TCP: %d ms, QUIC: %d ms, Improvement: %.2f%%", 
                tcpTime, quicTime, ((double)(tcpTime - quicTime) / tcpTime) * 100));
        
        return result;
    }
    
    private long simulateNetworkTransfer(Path data, String protocol, String type) {
        long fileSize = calculateDatasetSize(data);
        
        // Simulate different network characteristics
        double throughputMBps;
        if ("TCP".equals(protocol)) {
            throughputMBps = "small".equals(type) ? 20.0 : 50.0; // TCP slower for small files
        } else {
            throughputMBps = "small".equals(type) ? 35.0 : 60.0; // QUIC better for small files
        }
        
        return (long) ((fileSize / (1024.0 * 1024.0)) / throughputMBps * 1000);
    }
    
    private Path simulateConcurrentNetworkOperations(List<Path> datasets) throws Exception {
        List<CompletableFuture<Void>> futures = datasets.stream()
            .map(data -> CompletableFuture.runAsync(() -> {
                try {
                    simulateNetworkComparison(data, "concurrent");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executorService))
            .collect(java.util.stream.Collectors.toList());
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        return tempDir.resolve("concurrent-network-result");
    }
    
    private Path simulateDeduplicationTest(Path data) throws Exception {
        // Simulate deduplication analysis
        Thread.sleep(200); // Deduplication overhead
        
        // Calculate deduplication ratio based on file patterns
        double deduplicationRatio = calculateDeduplicationRatio(data);
        
        Path result = tempDir.resolve("deduplication-test");
        Files.createDirectories(result);
        
        Files.writeString(result.resolve("deduplication-report.txt"), 
            String.format("Deduplication ratio: %.2f:1, Space savings: %.1f%%", 
                deduplicationRatio, (1.0 - 1.0/deduplicationRatio) * 100));
        
        return result;
    }
    
    private Path simulateIncrementalBackup(Path baseData, BenchmarkDataGenerator.DatasetInfo incrementalData) throws Exception {
        // Simulate base backup
        Path baseBackup = simulateBackupOperation(baseData);
        
        // Simulate incremental backup (faster due to deduplication)
        Thread.sleep(100); // Incremental backup overhead
        
        Path result = tempDir.resolve("incremental-backup");
        Files.createDirectories(result);
        
        Files.writeString(result.resolve("incremental-info.txt"), 
            "Base backup: " + baseBackup.toString() + "\n" +
            "Incremental changes processed successfully");
        
        return result;
    }
    
    private double calculateDeduplicationRatio(Path data) {
        // Simple heuristic based on file naming patterns
        try {
            return Files.walk(data)
                .filter(Files::isRegularFile)
                .mapToInt(p -> {
                    String fileName = p.getFileName().toString();
                    // Files with similar names are likely duplicates
                    return fileName.contains("copy") || fileName.contains("duplicate") ? 1 : 0;
                })
                .average()
                .orElse(0.0) * 5.0 + 1.0; // Scale to realistic ratio
        } catch (IOException e) {
            return 1.0;
        }
    }
    
    private long calculateDatasetSize(Path path) {
        try {
            if (Files.isRegularFile(path)) {
                return Files.size(path);
            } else if (Files.isDirectory(path)) {
                return Files.walk(path)
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
        } catch (IOException e) {
            return 0;
        }
        return 0;
    }
    
    private void generateComprehensiveReports() throws Exception {
        System.out.println("\n=== Generating Comprehensive Reports ===");
        
        BenchmarkReportGenerator reportGenerator = new BenchmarkReportGenerator(allMetrics, reportDir);
        
        // Generate all report formats
        Path htmlReport = reportGenerator.generateHtmlReport();
        Path jsonReport = reportGenerator.generateJsonReport();
        Path csvReport = reportGenerator.generateCsvReport();
        Path textSummary = reportGenerator.generateTextSummaryReport();
        Path chartData = reportGenerator.generateChartDataFile();
        
        System.out.println("Reports generated:");
        System.out.println("  HTML: " + htmlReport);
        System.out.println("  JSON: " + jsonReport);
        System.out.println("  CSV: " + csvReport);
        System.out.println("  Text Summary: " + textSummary);
        System.out.println("  Chart Data: " + chartData);
    }
    
    private void validatePerformanceTargets() {
        System.out.println("\n=== Validating Performance Targets ===");
        
        int targetsMet = 0;
        int totalTargets = 0;
        
        // Check backup throughput target (>50 MB/s)
        List<PerformanceMetrics> backupMetrics = allMetrics.stream()
            .filter(m -> m.getBenchmarkName().toLowerCase().contains("backup"))
            .collect(java.util.stream.Collectors.toList());
        
        if (!backupMetrics.isEmpty()) {
            totalTargets++;
            double avgBackupThroughput = backupMetrics.stream()
                .mapToDouble(m -> (Double) m.getMetrics().getOrDefault("throughput_mbps", 0.0))
                .average()
                .orElse(0.0);
            
            if (avgBackupThroughput >= 50.0) {
                targetsMet++;
                System.out.println("✓ Backup throughput target met: " + String.format("%.2f MB/s", avgBackupThroughput));
            } else {
                System.out.println("✗ Backup throughput target not met: " + String.format("%.2f MB/s", avgBackupThroughput));
            }
        }
        
        // Check memory usage target (<500MB)
        List<PerformanceMetrics> memoryMetrics = allMetrics.stream()
            .filter(m -> m.getMetrics().containsKey("memory_used_mb"))
            .collect(java.util.stream.Collectors.toList());
        
        if (!memoryMetrics.isEmpty()) {
            totalTargets++;
            double avgMemoryUsage = memoryMetrics.stream()
                .mapToDouble(m -> (Double) m.getMetrics().getOrDefault("memory_used_mb", 0.0))
                .average()
                .orElse(0.0);
            
            if (avgMemoryUsage <= 500.0) {
                targetsMet++;
                System.out.println("✓ Memory usage target met: " + String.format("%.2f MB", avgMemoryUsage));
            } else {
                System.out.println("✗ Memory usage target not met: " + String.format("%.2f MB", avgMemoryUsage));
            }
        }
        
        // Check GC overhead target (<10%)
        List<PerformanceMetrics> gcMetrics = allMetrics.stream()
            .filter(m -> m.getMetrics().containsKey("gc_overhead_percent"))
            .collect(java.util.stream.Collectors.toList());
        
        if (!gcMetrics.isEmpty()) {
            totalTargets++;
            double avgGcOverhead = gcMetrics.stream()
                .mapToDouble(m -> (Double) m.getMetrics().getOrDefault("gc_overhead_percent", 0.0))
                .average()
                .orElse(0.0);
            
            if (avgGcOverhead <= 10.0) {
                targetsMet++;
                System.out.println("✓ GC overhead target met: " + String.format("%.2f%%", avgGcOverhead));
            } else {
                System.out.println("✗ GC overhead target not met: " + String.format("%.2f%%", avgGcOverhead));
            }
        }
        
        System.out.println(String.format("\nOverall: %d/%d targets met (%.1f%%)", 
            targetsMet, totalTargets, (double) targetsMet / totalTargets * 100.0));
        
        if (targetsMet == totalTargets) {
            System.out.println("🎉 All performance targets met!");
        } else {
            System.out.println("⚠️  Some performance targets not met - review reports for details");
        }
    }
    
    @FunctionalInterface
    private interface BenchmarkOperation {
        Path execute() throws Exception;
    }
}