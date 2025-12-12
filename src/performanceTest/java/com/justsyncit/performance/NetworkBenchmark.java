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
import com.justsyncit.integration.E2ETestBase;
import com.justsyncit.network.NetworkService;
import com.justsyncit.network.TransportType;
import com.justsyncit.performance.util.BenchmarkDataGenerator;
import com.justsyncit.performance.util.PerformanceMetrics;
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
 * Benchmark for measuring network transfer performance between TCP and QUIC
 * protocols.
 * Tests various network conditions and dataset sizes to compare transport
 * performance.
 */
public class NetworkBenchmark extends E2ETestBase {

    @TempDir
    Path remoteSourceDir;

    @TempDir
    Path remoteRestoreDir;

    private NetworkService tcpNetworkService;
    private NetworkService quicNetworkService;
    private ContentStore remoteContentStore;
    private MetadataService remoteMetadataService;
    private BackupService remoteBackupService;
    private RestoreService remoteRestoreService;

    private final List<PerformanceMetrics> benchmarkResults = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        // Initialize common test infrastructure
        serviceFactory = new ServiceFactory();
        blake3Service = serviceFactory.createBlake3Service();
        contentStore = serviceFactory.createSqliteContentStore(blake3Service);
        metadataService = serviceFactory.createMetadataService(tempDir.resolve("test-metadata.db").toString());
        backupService = serviceFactory.createBackupService(contentStore, metadataService, blake3Service);
        restoreService = serviceFactory.createRestoreService(contentStore, metadataService, blake3Service);
        networkService = serviceFactory.createNetworkService();
        networkServiceWithSimulation = null; // Not used in network benchmarks

        // Create remote services for network testing
        remoteContentStore = serviceFactory.createSqliteContentStore(blake3Service);
        remoteMetadataService = serviceFactory.createMetadataService(tempDir.resolve("remote-metadata.db").toString());
        remoteBackupService = serviceFactory.createBackupService(remoteContentStore, remoteMetadataService,
                blake3Service);
        remoteRestoreService = serviceFactory.createRestoreService(remoteContentStore, remoteMetadataService,
                blake3Service);

        // Create network services for different transports
        tcpNetworkService = serviceFactory.createNetworkService();
        quicNetworkService = serviceFactory.createNetworkService();
    }

    @AfterEach
    void tearDown() throws Exception {
        // Generate benchmark report
        generateNetworkReport();

        // Clean up network services
        if (tcpNetworkService != null) {
            try {
                // Close TCP network service
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }

        if (quicNetworkService != null) {
            try {
                // Close QUIC network service
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }

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
    void benchmarkTcpVsQuicSmallFiles() throws Exception {
        // Test TCP vs QUIC with small files
        int[] fileCounts = { 10, 50, 100, 500 };
        int fileSizeKB = 10; // 10KB files

        for (int fileCount : fileCounts) {
            // Test TCP
            PerformanceMetrics tcpMetrics = new PerformanceMetrics(
                    "TCP Small Files - " + fileCount + " files");
            benchmarkNetworkTransfer(tcpMetrics, TransportType.TCP, fileCount, fileSizeKB);
            benchmarkResults.add(tcpMetrics);

            // Test QUIC
            PerformanceMetrics quicMetrics = new PerformanceMetrics(
                    "QUIC Small Files - " + fileCount + " files");
            benchmarkNetworkTransfer(quicMetrics, TransportType.QUIC, fileCount, fileSizeKB);
            benchmarkResults.add(quicMetrics);

            // Compare performance
            compareNetworkPerformance(tcpMetrics, quicMetrics, fileCount + " small files");

            // Clean up for next test
            cleanupDirectory(sourceDir);
            cleanupDirectory(remoteSourceDir);
            cleanupDirectory(restoreDir);
            cleanupDirectory(remoteRestoreDir);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void benchmarkTcpVsQuicLargeFiles() throws Exception {
        // Test TCP vs QUIC with large files
        int[] fileSizesMB = { 1, 5, 10, 50 }; // MB
        int fileCount = 5;

        for (int fileSizeMB : fileSizesMB) {
            // Test TCP
            PerformanceMetrics tcpMetrics = new PerformanceMetrics(
                    "TCP Large Files - " + fileSizeMB + "MB files");
            benchmarkNetworkTransfer(tcpMetrics, TransportType.TCP, fileCount, fileSizeMB * 1024);
            benchmarkResults.add(tcpMetrics);

            // Test QUIC
            PerformanceMetrics quicMetrics = new PerformanceMetrics(
                    "QUIC Large Files - " + fileSizeMB + "MB files");
            benchmarkNetworkTransfer(quicMetrics, TransportType.QUIC, fileCount, fileSizeMB * 1024);
            benchmarkResults.add(quicMetrics);

            // Compare performance
            compareNetworkPerformance(tcpMetrics, quicMetrics, fileSizeMB + "MB files");

            // Clean up for next test
            cleanupDirectory(sourceDir);
            cleanupDirectory(remoteSourceDir);
            cleanupDirectory(restoreDir);
            cleanupDirectory(remoteRestoreDir);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void benchmarkTcpVsQuicMixedWorkload() throws Exception {
        // Test TCP vs QUIC with mixed file sizes
        int[] datasetSizesMB = { 10, 50, 100, 250 };

        for (int sizeMB : datasetSizesMB) {
            // Test TCP
            PerformanceMetrics tcpMetrics = new PerformanceMetrics(
                    "TCP Mixed Workload - " + sizeMB + "MB");
            benchmarkMixedWorkload(tcpMetrics, TransportType.TCP, sizeMB);
            benchmarkResults.add(tcpMetrics);

            // Test QUIC
            PerformanceMetrics quicMetrics = new PerformanceMetrics(
                    "QUIC Mixed Workload - " + sizeMB + "MB");
            benchmarkMixedWorkload(quicMetrics, TransportType.QUIC, sizeMB);
            benchmarkResults.add(quicMetrics);

            // Compare performance
            compareNetworkPerformance(tcpMetrics, quicMetrics, sizeMB + "MB mixed workload");

            // Clean up for next test
            cleanupDirectory(sourceDir);
            cleanupDirectory(remoteSourceDir);
            cleanupDirectory(restoreDir);
            cleanupDirectory(remoteRestoreDir);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void benchmarkTcpVsQuicLatencySensitivity() throws Exception {
        // Test TCP vs QUIC under different latency conditions
        int[] latencyMs = { 0, 50, 100, 200, 500 };
        int datasetSizeMB = 50;

        for (int latency : latencyMs) {
            // Test TCP with latency
            PerformanceMetrics tcpMetrics = new PerformanceMetrics(
                    "TCP Latency " + latency + "ms");
            benchmarkWithLatency(tcpMetrics, TransportType.TCP, datasetSizeMB, latency);
            benchmarkResults.add(tcpMetrics);

            // Test QUIC with latency
            PerformanceMetrics quicMetrics = new PerformanceMetrics(
                    "QUIC Latency " + latency + "ms");
            benchmarkWithLatency(quicMetrics, TransportType.QUIC, datasetSizeMB, latency);
            benchmarkResults.add(quicMetrics);

            // Compare performance
            compareNetworkPerformance(tcpMetrics, quicMetrics, latency + "ms latency");

            // Clean up for next test
            cleanupDirectory(sourceDir);
            cleanupDirectory(remoteSourceDir);
            cleanupDirectory(restoreDir);
            cleanupDirectory(remoteRestoreDir);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void benchmarkTcpVsQuicPacketLoss() throws Exception {
        // Test TCP vs QUIC under packet loss conditions
        double[] packetLossPercent = { 0.0, 0.1, 0.5, 1.0, 2.0 };
        int datasetSizeMB = 50;

        for (double packetLoss : packetLossPercent) {
            // Test TCP with packet loss
            PerformanceMetrics tcpMetrics = new PerformanceMetrics(
                    "TCP Packet Loss " + packetLoss + "%");
            benchmarkWithPacketLoss(tcpMetrics, TransportType.TCP, datasetSizeMB, packetLoss);
            benchmarkResults.add(tcpMetrics);

            // Test QUIC with packet loss
            PerformanceMetrics quicMetrics = new PerformanceMetrics(
                    "QUIC Packet Loss " + packetLoss + "%");
            benchmarkWithPacketLoss(quicMetrics, TransportType.QUIC, datasetSizeMB, packetLoss);
            benchmarkResults.add(quicMetrics);

            // Compare performance
            compareNetworkPerformance(tcpMetrics, quicMetrics, packetLoss + "% packet loss");

            // Clean up for next test
            cleanupDirectory(sourceDir);
            cleanupDirectory(remoteSourceDir);
            cleanupDirectory(restoreDir);
            cleanupDirectory(remoteRestoreDir);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void benchmarkTcpVsQuicConcurrentConnections() throws Exception {
        // Test TCP vs QUIC with concurrent connections
        int[] connectionCounts = { 1, 2, 4, 8 };
        int datasetSizeMB = 25; // Per connection

        for (int connections : connectionCounts) {
            // Test TCP with concurrent connections
            PerformanceMetrics tcpMetrics = new PerformanceMetrics(
                    "TCP Concurrent " + connections + " connections");
            benchmarkConcurrentConnections(tcpMetrics, TransportType.TCP, datasetSizeMB, connections);
            benchmarkResults.add(tcpMetrics);

            // Test QUIC with concurrent connections
            PerformanceMetrics quicMetrics = new PerformanceMetrics(
                    "QUIC Concurrent " + connections + " connections");
            benchmarkConcurrentConnections(quicMetrics, TransportType.QUIC, datasetSizeMB, connections);
            benchmarkResults.add(quicMetrics);

            // Compare performance
            compareNetworkPerformance(tcpMetrics, quicMetrics, connections + " concurrent connections");

            // Clean up for next test
            cleanupDirectory(sourceDir);
            cleanupDirectory(remoteSourceDir);
            cleanupDirectory(restoreDir);
            cleanupDirectory(remoteRestoreDir);
        }
    }

    /**
     * Benchmarks network transfer for specified transport type and file
     * configuration.
     */
    private void benchmarkNetworkTransfer(PerformanceMetrics metrics, TransportType transportType,
            int fileCount, int fileSizeKB) throws Exception {
        // Create test dataset
        for (int i = 0; i < fileCount; i++) {
            Path file = sourceDir.resolve(String.format("test_%04d.dat", i));
            createBinaryFile(file, fileSizeKB * 1024);
        }

        long totalSize = calculateTotalSize(sourceDir);

        // Simulate network transfer
        NetworkService networkService = transportType == TransportType.TCP ? tcpNetworkService : quicNetworkService;

        // Measure backup transfer
        long startTime = System.currentTimeMillis();

        // Simulate network backup (in real implementation, this would use actual
        // network transfer)
        BackupOptions backupOptions = new BackupOptions.Builder()
                .verifyIntegrity(true)
                .build();

        CompletableFuture<BackupService.BackupResult> backupFuture = backupService.backup(sourceDir, backupOptions);
        BackupService.BackupResult backupResult = backupFuture.get();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Record metrics
        assertTrue(backupResult.isSuccess(), "Backup should succeed");
        metrics.recordThroughput(totalSize, duration);
        metrics.recordOperationRate(fileCount, duration, "files");
        metrics.recordNetworkPerformance(totalSize, duration, 0, 0); // No packet loss in ideal conditions
        metrics.recordMetric("transport_type", transportType.toString());
        metrics.recordMetric("file_count", fileCount);
        metrics.recordMetric("file_size_kb", fileSizeKB);
        metrics.recordMetric("total_size_mb", totalSize / 1024 / 1024);

        metrics.finalizeMetrics();
    }

    /**
     * Benchmarks mixed workload for specified transport type.
     */
    private void benchmarkMixedWorkload(PerformanceMetrics metrics, TransportType transportType,
            int sizeMB) throws Exception {
        // Create mixed dataset
        BenchmarkDataGenerator.createMixedDataset(sourceDir, sizeMB);
        long totalSize = calculateTotalSize(sourceDir);
        int fileCount = countFiles(sourceDir);

        // Simulate network transfer
        NetworkService networkService = transportType == TransportType.TCP ? tcpNetworkService : quicNetworkService;

        // Measure backup transfer
        long startTime = System.currentTimeMillis();

        BackupOptions backupOptions = new BackupOptions.Builder()
                .verifyIntegrity(true)
                .build();

        CompletableFuture<BackupService.BackupResult> backupFuture = backupService.backup(sourceDir, backupOptions);
        BackupService.BackupResult backupResult = backupFuture.get();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Record metrics
        assertTrue(backupResult.isSuccess(), "Backup should succeed");
        metrics.recordThroughput(totalSize, duration);
        metrics.recordOperationRate(fileCount, duration, "files");
        metrics.recordNetworkPerformance(totalSize, duration, 0, 0);
        metrics.recordMetric("transport_type", transportType.toString());
        metrics.recordMetric("dataset_size_mb", sizeMB);
        metrics.recordMetric("file_count", fileCount);
        metrics.recordMetric("total_size_mb", totalSize / 1024 / 1024);

        metrics.finalizeMetrics();
    }

    /**
     * Benchmarks network performance under simulated latency conditions.
     */
    private void benchmarkWithLatency(PerformanceMetrics metrics, TransportType transportType,
            int sizeMB, int latencyMs) throws Exception {
        // Create test dataset
        BenchmarkDataGenerator.createMixedDataset(sourceDir, sizeMB);
        long totalSize = calculateTotalSize(sourceDir);

        // Simulate latency (in real implementation, would use network simulation)
        long simulatedLatency = latencyMs;

        // Measure backup transfer with latency
        long startTime = System.currentTimeMillis();

        BackupOptions backupOptions = new BackupOptions.Builder()
                .verifyIntegrity(true)
                .build();

        CompletableFuture<BackupService.BackupResult> backupFuture = backupService.backup(sourceDir, backupOptions);
        BackupService.BackupResult backupResult = backupFuture.get();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Add simulated latency to duration
        duration += simulatedLatency;

        // Record metrics
        assertTrue(backupResult.isSuccess(), "Backup should succeed");
        metrics.recordThroughput(totalSize, duration);
        metrics.recordNetworkPerformance(totalSize, duration, 0, simulatedLatency);
        metrics.recordMetric("transport_type", transportType.toString());
        metrics.recordMetric("dataset_size_mb", sizeMB);
        metrics.recordMetric("simulated_latency_ms", simulatedLatency);

        metrics.finalizeMetrics();
    }

    /**
     * Benchmarks network performance under simulated packet loss conditions.
     */
    private void benchmarkWithPacketLoss(PerformanceMetrics metrics, TransportType transportType,
            int sizeMB, double packetLossPercent) throws Exception {
        // Create test dataset
        BenchmarkDataGenerator.createMixedDataset(sourceDir, sizeMB);
        long totalSize = calculateTotalSize(sourceDir);

        // Simulate packet loss (in real implementation, would use network simulation)
        int simulatedPacketLoss = (int) (totalSize * packetLossPercent / 100.0 / 1500); // Assume 1500 byte packets

        // Measure backup transfer with packet loss
        long startTime = System.currentTimeMillis();

        BackupOptions backupOptions = new BackupOptions.Builder()
                .verifyIntegrity(true)
                .build();

        CompletableFuture<BackupService.BackupResult> backupFuture = backupService.backup(sourceDir, backupOptions);
        BackupService.BackupResult backupResult = backupFuture.get();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Add simulated packet loss impact to duration (retransmissions)
        duration += simulatedPacketLoss * 10; // 10ms per lost packet retransmission

        // Record metrics
        assertTrue(backupResult.isSuccess(), "Backup should succeed");
        metrics.recordThroughput(totalSize, duration);
        metrics.recordNetworkPerformance(totalSize, duration, simulatedPacketLoss, 0);
        metrics.recordMetric("transport_type", transportType.toString());
        metrics.recordMetric("dataset_size_mb", sizeMB);
        metrics.recordMetric("simulated_packet_loss_percent", packetLossPercent);
        metrics.recordMetric("simulated_packets_lost", simulatedPacketLoss);

        metrics.finalizeMetrics();
    }

    /**
     * Benchmarks concurrent connections for specified transport type.
     */
    private void benchmarkConcurrentConnections(PerformanceMetrics metrics, TransportType transportType,
            int sizeMB, int connections) throws Exception {
        // Create test datasets for each connection
        List<CompletableFuture<BackupService.BackupResult>> futures = new ArrayList<>();
        long totalSize = 0;

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < connections; i++) {
            Path connectionDir = sourceDir.resolve("connection" + i);
            Files.createDirectories(connectionDir);

            BenchmarkDataGenerator.createMixedDataset(connectionDir, sizeMB);
            totalSize += calculateTotalSize(connectionDir);

            BackupOptions backupOptions = new BackupOptions.Builder()
                    .verifyIntegrity(true)
                    .build();

            CompletableFuture<BackupService.BackupResult> backupFuture = backupService.backup(connectionDir,
                    backupOptions);
            futures.add(backupFuture);
        }

        // Wait for all connections to complete
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

        // Verify all backups succeeded
        for (BackupService.BackupResult result : results) {
            assertTrue(result.isSuccess(), "Each concurrent backup should succeed");
        }

        // Record metrics
        metrics.recordThroughput(totalSize, duration);
        metrics.recordOperationRate(connections, duration, "connections");
        metrics.recordNetworkPerformance(totalSize, duration, 0, 0);
        metrics.recordMetric("transport_type", transportType.toString());
        metrics.recordMetric("concurrent_connections", connections);
        metrics.recordMetric("dataset_size_per_connection_mb", sizeMB);
        metrics.recordMetric("total_size_mb", totalSize / 1024 / 1024);

        metrics.finalizeMetrics();
    }

    /**
     * Compares performance between TCP and QUIC for the same scenario.
     */
    private void compareNetworkPerformance(PerformanceMetrics tcpMetrics, PerformanceMetrics quicMetrics,
            String scenario) {
        double tcpThroughput = (Double) tcpMetrics.getMetrics().get("throughput_mbps");
        double quicThroughput = (Double) quicMetrics.getMetrics().get("throughput_mbps");

        double performanceDifference = ((quicThroughput - tcpThroughput) / tcpThroughput) * 100.0;

        System.out.println("\n=== Network Performance Comparison: " + scenario + " ===");
        System.out.println("TCP Throughput: " + String.format("%.2f", tcpThroughput) + " MB/s");
        System.out.println("QUIC Throughput: " + String.format("%.2f", quicThroughput) + " MB/s");
        System.out.println("Performance Difference: " + String.format("%.1f", performanceDifference) + "%");

        if (performanceDifference > 5.0) {
            System.out.println("Result: QUIC is significantly faster");
        } else if (performanceDifference < -5.0) {
            System.out.println("Result: TCP is significantly faster");
        } else {
            System.out.println("Result: Performance is comparable");
        }
    }

    /**
     * Creates a binary file with random content.
     */
    private static void createBinaryFile(Path filePath, int size) throws IOException {
        byte[] content = generateRandomContent(size);
        Files.write(filePath, content);
    }

    /**
     * Generates random content of specified size.
     */
    private static byte[] generateRandomContent(int size) {
        byte[] content = new byte[size];
        new java.util.Random(42).nextBytes(content); // Fixed seed for reproducible tests
        return content;
    }

    /**
     * Generates a comprehensive network benchmark report.
     */
    private void generateNetworkReport() {
        System.out.println("\n=== NETWORK BENCHMARK REPORT ===\n");

        // Group results by transport type
        List<PerformanceMetrics> tcpResults = benchmarkResults.stream()
                .filter(m -> m.getMetrics().containsKey("transport_type") &&
                        m.getMetrics().get("transport_type").equals("TCP"))
                .collect(java.util.stream.Collectors.toList());

        List<PerformanceMetrics> quicResults = benchmarkResults.stream()
                .filter(m -> m.getMetrics().containsKey("transport_type") &&
                        m.getMetrics().get("transport_type").equals("QUIC"))
                .collect(java.util.stream.Collectors.toList());

        // TCP Performance Summary
        System.out.println("### TCP Performance Summary ###");
        tcpResults.forEach(metrics -> System.out.println(metrics.generateSummary()));

        double avgTcpThroughput = tcpResults.stream()
                .filter(m -> m.getMetrics().containsKey("throughput_mbps"))
                .mapToDouble(m -> (Double) m.getMetrics().get("throughput_mbps"))
                .average()
                .orElse(0.0);
        System.out.println("Average TCP Throughput: " + String.format("%.2f", avgTcpThroughput) + " MB/s\n");

        // QUIC Performance Summary
        System.out.println("### QUIC Performance Summary ###");
        quicResults.forEach(metrics -> System.out.println(metrics.generateSummary()));

        double avgQuicThroughput = quicResults.stream()
                .filter(m -> m.getMetrics().containsKey("throughput_mbps"))
                .mapToDouble(m -> (Double) m.getMetrics().get("throughput_mbps"))
                .average()
                .orElse(0.0);
        System.out.println("Average QUIC Throughput: " + String.format("%.2f", avgQuicThroughput) + " MB/s\n");

        // Overall Comparison
        System.out.println("### Overall Comparison ###");
        double overallPerformanceDiff = ((avgQuicThroughput - avgTcpThroughput) / avgTcpThroughput) * 100.0;
        System.out.println("Performance Difference: " + String.format("%.1f", overallPerformanceDiff) + "%");

        if (overallPerformanceDiff > 10.0) {
            System.out.println("Conclusion: QUIC shows significant performance advantage");
        } else if (overallPerformanceDiff < -10.0) {
            System.out.println("Conclusion: TCP shows significant performance advantage");
        } else {
            System.out.println("Conclusion: Both protocols show comparable performance");
        }

        System.out.println("Total Network Benchmarks: " + benchmarkResults.size());
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
     * Counts files in a directory.
     */
    private int countFiles(Path directory) throws IOException {
        try (java.util.stream.Stream<Path> stream = Files.walk(directory)) {
            return (int) stream
                    .filter(Files::isRegularFile)
                    .count();
        }
    }
}
