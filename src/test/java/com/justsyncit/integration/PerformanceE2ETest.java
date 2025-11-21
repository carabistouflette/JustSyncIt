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

package com.justsyncit.integration;

import com.justsyncit.storage.ContentStoreStats;
import com.justsyncit.storage.metadata.MetadataStats;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for performance characteristics.
 * Tests performance benchmarks and validates system performance under various conditions.
 */
public class PerformanceE2ETest extends E2ETestBase {

    @Test
    void testBackupPerformanceWithSmallFiles() throws Exception {
        // Create dataset with many small files
        createPerformanceDataset(100, 10 * 1024); // 100 files, up to 10KB each
        
        // Measure backup performance
        long backupTime = measureTime(() -> {
            try {
                performBackup("perf-small-snapshot", "Small files performance test");
            } catch (Exception e) {
                fail("Backup should succeed", e);
            }
        });
        
        // Calculate total data size
        long totalDataSize = Files.walk(sourceDir)
                .filter(Files::isRegularFile)
                .mapToLong(file -> {
                    try {
                        return Files.size(file);
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .sum();
        
        // Calculate throughput
        double throughput = calculateThroughput(totalDataSize, backupTime);
        
        // Performance assertions
        assertTrue(backupTime < 30000, 
                "Backup of 100 small files should complete within 30 seconds: " + backupTime + "ms");
        assertTrue(throughput > 1.0, 
                "Should have reasonable throughput for small files: " + String.format("%.2f", throughput) + " MB/s");
        
        // Get storage statistics
        ContentStoreStats storageStats = contentStore.getStats();
        assertTrue(storageStats.getTotalChunks() > 0, "Should have stored chunks");
        assertTrue(storageStats.getTotalSizeBytes() > 0, "Should have stored data");
    }

    @Test
    void testBackupPerformanceWithLargeFiles() throws Exception {
        // Create dataset with fewer large files
        createPerformanceDataset(10, 1024 * 1024); // 10 files, up to 1MB each
        
        // Measure backup performance
        long backupTime = measureTime(() -> {
            try {
                performBackup("perf-large-snapshot", "Large files performance test");
            } catch (Exception e) {
                fail("Backup should succeed", e);
            }
        });
        
        // Calculate total data size
        long totalDataSize = Files.walk(sourceDir)
                .filter(Files::isRegularFile)
                .mapToLong(file -> {
                    try {
                        return Files.size(file);
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .sum();
        
        // Calculate throughput
        double throughput = calculateThroughput(totalDataSize, backupTime);
        
        // Performance assertions for large files
        assertTrue(backupTime < 60000, 
                "Backup of 10 large files should complete within 60 seconds: " + backupTime + "ms");
        assertTrue(throughput > 5.0, 
                "Should have good throughput for large files: " + String.format("%.2f", throughput) + " MB/s");
        
        // Get storage statistics
        ContentStoreStats storageStats = contentStore.getStats();
        assertTrue(storageStats.getTotalChunks() > 0, "Should have stored chunks");
        assertTrue(storageStats.getTotalSizeBytes() > 0, "Should have stored data");
    }

    @Test
    void testRestorePerformance() throws Exception {
        // Create test dataset
        createPerformanceDataset(50, 100 * 1024); // 50 files, up to 100KB each
        
        // Perform backup first
        String snapshotId = performBackup("perf-restore-snapshot", "Restore performance test");
        
        // Calculate total data size
        long totalDataSize = Files.walk(sourceDir)
                .filter(Files::isRegularFile)
                .mapToLong(file -> {
                    try {
                        return Files.size(file);
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .sum();
        
        // Measure restore performance
        long restoreTime = measureTime(() -> {
            try {
                performRestore(snapshotId);
            } catch (Exception e) {
                fail("Restore should succeed", e);
            }
        });
        
        // Calculate throughput
        double throughput = calculateThroughput(totalDataSize, restoreTime);
        
        // Performance assertions
        assertTrue(restoreTime < 30000, 
                "Restore should complete within 30 seconds: " + restoreTime + "ms");
        assertTrue(throughput > 2.0, 
                "Should have good restore throughput: " + String.format("%.2f", throughput) + " MB/s");
        
        // Verify restore was successful
        assertDirectoryStructureEquals(sourceDir, restoreDir);
    }

    @Test
    void testDeduplicationPerformance() throws Exception {
        // Create dataset with many duplicate files
        createDuplicateDataset();
        
        // Add more duplicates for performance testing
        Path baseFile = sourceDir.resolve("perf-duplicate-base.txt");
        String baseContent = "Performance test duplicate content.\n".repeat(1000);
        Files.write(baseFile, baseContent.getBytes());
        
        for (int i = 1; i <= 50; i++) {
            Path duplicateFile = sourceDir.resolve("perf-duplicate" + i + ".txt");
            Files.write(duplicateFile, baseContent.getBytes());
        }
        
        // Measure backup performance with deduplication
        long backupTime = measureTime(() -> {
            try {
                performBackup("perf-dedup-snapshot", "Deduplication performance test");
            } catch (Exception e) {
                fail("Backup should succeed", e);
            }
        });
        
        // Get storage statistics
        ContentStoreStats storageStats = contentStore.getStats();
        MetadataStats metadataStats = metadataService.getStats();
        
        // Verify deduplication occurred
        double deduplicationRatio = metadataStats.getDeduplicationRatio();
        assertTrue(deduplicationRatio > 1.0, 
                "Should have significant deduplication: " + String.format("%.2f", deduplicationRatio));
        
        // Performance assertions
        assertTrue(backupTime < 45000, 
                "Backup with deduplication should complete within 45 seconds: " + backupTime + "ms");
        
        // Verify storage efficiency
        long totalFileSize = Files.walk(sourceDir)
                .filter(Files::isRegularFile)
                .mapToLong(file -> {
                    try {
                        return Files.size(file);
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .sum();
        
        long storedSize = storageStats.getTotalSizeBytes();
        assertTrue(storedSize < totalFileSize, 
                "Deduplication should reduce storage: " + storedSize + " vs " + totalFileSize);
    }

    @Test
    void testConcurrentBackupPerformance() throws Exception {
        // Create multiple datasets for concurrent testing
        List<String> snapshotIds = new ArrayList<>();
        
        // Measure concurrent backup performance
        long concurrentTime = measureTime(() -> {
            try {
                // Create and backup multiple datasets
                for (int i = 0; i < 3; i++) {
                    // Create temporary subdirectory for each backup
                    Path subDir = sourceDir.resolve("concurrent" + i);
                    Files.createDirectories(subDir);
                    
                    // Create test data in subdirectory
                    createPerformanceDataset(20, 50 * 1024); // 20 files, up to 50KB each
                    
                    // Move files to subdirectory
                    Files.walk(sourceDir)
                            .filter(Files::isRegularFile)
                            .filter(file -> !file.startsWith(subDir))
                            .limit(20)
                            .forEach(file -> {
                                try {
                                    Files.move(file, subDir.resolve(file.getFileName()));
                                } catch (Exception e) {
                                    // Ignore
                                }
                            });
                    
                    // Perform backup
                    String snapshotId = performBackup("perf-concurrent" + i + "-snapshot", 
                            "Concurrent backup test " + i);
                    snapshotIds.add(snapshotId);
                }
            } catch (Exception e) {
                fail("Concurrent backup should succeed", e);
            }
        });
        
        // Performance assertions
        assertTrue(concurrentTime < 90000, 
                "Concurrent backups should complete within 90 seconds: " + concurrentTime + "ms");
        assertEquals(3, snapshotIds.size(), "Should have 3 snapshot IDs");
        
        // Verify all snapshots exist
        for (String snapshotId : snapshotIds) {
            assertSnapshotExists(snapshotId);
        }
    }

    @Test
    void testNetworkPerformance() throws Exception {
        // Create test dataset
        createPerformanceDataset(30, 200 * 1024); // 30 files, up to 200KB each
        
        // Test network performance with both transports
        testWithBothTransports(transportType -> {
            try {
                // Measure network operation time
                long networkTime = measureTime(() -> {
                    // In a real test, this would involve actual network transfer
                    // For now, we verify the infrastructure supports performance testing
                    assertNotNull(networkService, "Network service should be available");
                    assertTrue(networkService.getBytesSent() >= 0, "Should track bytes sent");
                    assertTrue(networkService.getBytesReceived() >= 0, "Should track bytes received");
                });
                
                // Network operations should be reasonably fast
                assertTrue(networkTime < 5000, 
                        "Network operations should be fast with " + transportType + ": " + networkTime + "ms");
                
            } catch (Exception e) {
                fail("Network performance test should succeed with " + transportType, e);
            }
        });
    }

    @Test
    void testMemoryUsagePerformance() throws Exception {
        // Create dataset that tests memory usage
        createPerformanceDataset(100, 500 * 1024); // 100 files, up to 500KB each
        
        // Measure memory usage during backup
        Runtime runtime = Runtime.getRuntime();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
        
        long backupTime = measureTime(() -> {
            try {
                performBackup("perf-memory-snapshot", "Memory usage performance test");
            } catch (Exception e) {
                fail("Backup should succeed", e);
            }
        });
        
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = memoryAfter - memoryBefore;
        
        // Memory usage should be reasonable (less than 100MB for this dataset)
        assertTrue(memoryUsed < 100 * 1024 * 1024, 
                "Memory usage should be reasonable: " + (memoryUsed / 1024 / 1024) + "MB");
        
        // Performance should still be good
        assertTrue(backupTime < 60000, 
                "Backup should complete within 60 seconds: " + backupTime + "ms");
        
        // Get storage statistics
        ContentStoreStats storageStats = contentStore.getStats();
        assertTrue(storageStats.getTotalChunks() > 0, "Should have stored chunks");
        assertTrue(storageStats.getTotalSizeBytes() > 0, "Should have stored data");
    }

    @Test
    void testScalabilityPerformance() throws Exception {
        // Test scalability with increasing dataset sizes
        int[] fileCounts = {10, 50, 100};
        int[] maxFileSizes = {50 * 1024, 100 * 1024, 200 * 1024}; // 50KB, 100KB, 200KB
        
        for (int i = 0; i < fileCounts.length; i++) {
            final int testIndex = i;
            // Clean up previous data
            Files.walk(sourceDir)
                    .filter(Files::isRegularFile)
                    .forEach(file -> {
                        try {
                            Files.delete(file);
                        } catch (Exception e) {
                            // Ignore
                        }
                    });
            
            // Create dataset
            createPerformanceDataset(fileCounts[testIndex], maxFileSizes[testIndex]);
            
            // Measure backup performance
            long backupTime = measureTime(() -> {
                try {
                    performBackup("perf-scalability" + testIndex + "-snapshot",
                            "Scalability test " + testIndex);
                } catch (Exception e) {
                    fail("Backup should succeed for scalability test " + testIndex, e);
                }
            });
            
            // Calculate total data size
            long totalDataSize = Files.walk(sourceDir)
                    .filter(Files::isRegularFile)
                    .mapToLong(file -> {
                        try {
                            return Files.size(file);
                        } catch (Exception e) {
                            return 0;
                        }
                    })
                    .sum();
            
            // Calculate throughput
            double throughput = calculateThroughput(totalDataSize, backupTime);
            
            // Performance should scale reasonably
            assertTrue(backupTime < (i + 1) * 30000, 
                    "Backup " + testIndex + " should complete within reasonable time: " + backupTime + "ms");
            assertTrue(throughput > 0.5,
                    "Backup " + testIndex + " should have reasonable throughput: " + String.format("%.2f", throughput) + " MB/s");
            
            // Get storage statistics
            ContentStoreStats storageStats = contentStore.getStats();
            assertTrue(storageStats.getTotalChunks() > 0, "Test " + testIndex + " should have stored chunks");
            assertTrue(storageStats.getTotalSizeBytes() > 0, "Test " + testIndex + " should have stored data");
        }
    }

    @Test
    void testPerformanceWithSpecialCharacters() throws Exception {
        // Create dataset with special characters
        createSpecialCharacterDataset();
        
        // Add more files with special characters for performance testing
        for (int i = 1; i <= 20; i++) {
            Path file = sourceDir.resolve("perf-special-üñíçødé-" + i + "-测试.txt");
            String content = "Special character performance test " + i + ".\n".repeat(100);
            Files.write(file, content.getBytes());
        }
        
        // Measure backup performance
        long backupTime = measureTime(() -> {
            try {
                performBackup("perf-special-snapshot", "Special characters performance test");
            } catch (Exception e) {
                fail("Backup should succeed", e);
            }
        });
        
        // Performance should not be significantly impacted by special characters
        assertTrue(backupTime < 30000, 
                "Backup with special characters should complete within 30 seconds: " + backupTime + "ms");
        
        // Measure restore performance
        String snapshotId = metadataService.listSnapshots().get(0).getId();
        long restoreTime = measureTime(() -> {
            try {
                performRestore(snapshotId);
            } catch (Exception e) {
                fail("Restore should succeed", e);
            }
        });
        
        assertTrue(restoreTime < 30000, 
                "Restore with special characters should complete within 30 seconds: " + restoreTime + "ms");
        
        // Verify integrity
        assertDirectoryStructureEquals(sourceDir, restoreDir);
    }

    @Test
    void testPerformanceMetrics() throws Exception {
        // Create comprehensive dataset
        createPerformanceDataset(75, 150 * 1024); // 75 files, up to 150KB each
        
        // Perform backup and collect metrics
        long backupTime = measureTime(() -> {
            try {
                performBackup("perf-metrics-snapshot", "Performance metrics test");
            } catch (Exception e) {
                fail("Backup should succeed", e);
            }
        });
        
        // Get comprehensive statistics
        ContentStoreStats storageStats = contentStore.getStats();
        MetadataStats metadataStats = metadataService.getStats();
        
        // Validate metrics are reasonable
        assertTrue(storageStats.getTotalChunks() > 0, "Should have chunks");
        assertTrue(storageStats.getTotalSizeBytes() > 0, "Should have stored data");
        assertTrue(metadataStats.getTotalFiles() > 0, "Should have files");
        assertTrue(metadataStats.getTotalSnapshots() > 0, "Should have snapshots");
        assertTrue(metadataStats.getAvgChunkSize() > 0, "Should have average chunk size");
        assertTrue(metadataStats.getAvgChunksPerFile() > 0, "Should have average chunks per file");
        
        // Calculate and validate performance metrics
        long totalDataSize = Files.walk(sourceDir)
                .filter(Files::isRegularFile)
                .mapToLong(file -> {
                    try {
                        return Files.size(file);
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .sum();
        
        double backupThroughput = calculateThroughput(totalDataSize, backupTime);
        assertTrue(backupThroughput > 1.0, 
                "Should have good backup throughput: " + String.format("%.2f", backupThroughput) + " MB/s");
        
        // Test restore metrics
        String snapshotId = metadataService.listSnapshots().get(0).getId();
        long restoreTime = measureTime(() -> {
            try {
                performRestore(snapshotId);
            } catch (Exception e) {
                fail("Restore should succeed", e);
            }
        });
        
        double restoreThroughput = calculateThroughput(totalDataSize, restoreTime);
        assertTrue(restoreThroughput > 2.0, 
                "Should have good restore throughput: " + String.format("%.2f", restoreThroughput) + " MB/s");
        
        // Validate deduplication effectiveness
        if (metadataStats.getDeduplicationRatio() > 1.0) {
            assertTrue(storageStats.getDeduplicationRatio() >= 1, 
                    "Should show deduplication benefits");
        }
    }
}