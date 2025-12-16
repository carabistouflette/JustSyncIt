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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.justsyncit.scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Basic tests for batch processing components.
 * Tests core functionality without complex setup.
 */
@DisplayName("Batch Processing Basic Tests")
public class BatchProcessingBasicTest {

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Should create batch options successfully")
    void shouldCreateBatchOptionsSuccessfully() {
        // When
        BatchOptions options = new BatchOptions();

        // Then
        assertNotNull(options);
        assertEquals(50, options.getBatchSize());
        assertEquals(1000, options.getMaxBatchSize());
        assertEquals(300, options.getBatchTimeoutSeconds());
        assertTrue(options.isAdaptiveSizing());
        assertTrue(options.isPriorityScheduling());
        assertTrue(options.isBackpressureControl());
        assertEquals(64 * 1024, options.getChunkSize());
        assertEquals(4, options.getMaxConcurrentChunks());
        assertEquals(BatchStrategy.SIZE_BASED, options.getStrategy());
        assertEquals(0.8, options.getMemoryPressureThreshold());
        assertEquals(0.9, options.getCpuUtilizationThreshold());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Should create batch options with builder pattern")
    void shouldCreateBatchOptionsWithBuilderPattern() {
        // When
        BatchOptions options = new BatchOptions()
                .withBatchSize(100)
                .withAdaptiveSizing(false)
                .withPriorityScheduling(true)
                .withStrategy(BatchStrategy.RESOURCE_AWARE);

        // Then
        assertNotNull(options);
        assertEquals(100, options.getBatchSize());
        assertFalse(options.isAdaptiveSizing());
        assertTrue(options.isPriorityScheduling());
        assertEquals(BatchStrategy.RESOURCE_AWARE, options.getStrategy());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Should create batch configuration successfully")
    void shouldCreateBatchConfigurationSuccessfully() {
        // When
        BatchConfiguration config = new BatchConfiguration();

        // Then
        assertNotNull(config);
        assertEquals(10, config.getMaxConcurrentBatches());
        assertEquals(1000, config.getMaxBatchSize());
        assertEquals(1, config.getMinBatchSize());
        assertTrue(config.isAdaptiveSizing());
        assertTrue(config.isPriorityScheduling());
        assertTrue(config.isBackpressureControl());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Should create batch configuration with builder pattern")
    void shouldCreateBatchConfigurationWithBuilderPattern() {
        // When
        BatchConfiguration config = new BatchConfiguration()
                .withMaxConcurrentBatches(20)
                .withMaxBatchSize(100)
                .withAdaptiveSizing(false);

        // Then
        assertNotNull(config);
        assertEquals(20, config.getMaxConcurrentBatches());
        assertEquals(100, config.getMaxBatchSize());
        assertFalse(config.isAdaptiveSizing());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Should validate batch priority enum")
    void shouldValidateBatchPriorityEnum() {
        // Then
        assertEquals(5, BatchPriority.values().length);

        // Test priority comparisons
        assertTrue(BatchPriority.CRITICAL.isHigherThan(BatchPriority.HIGH));
        assertTrue(BatchPriority.HIGH.isHigherThan(BatchPriority.NORMAL));
        assertTrue(BatchPriority.NORMAL.isHigherThan(BatchPriority.LOW));
        assertTrue(BatchPriority.LOW.isHigherThan(BatchPriority.BACKGROUND));

        assertFalse(BatchPriority.NORMAL.isHigherThan(BatchPriority.HIGH));
        assertFalse(BatchPriority.LOW.isHigherThan(BatchPriority.NORMAL));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Should validate batch strategy enum")
    void shouldValidateBatchStrategyEnum() {
        // Then
        assertEquals(7, BatchStrategy.values().length);

        // Test all strategies exist
        assertNotNull(BatchStrategy.SIZE_BASED);
        assertNotNull(BatchStrategy.LOCATION_BASED);
        assertNotNull(BatchStrategy.PRIORITY_BASED);
        assertNotNull(BatchStrategy.RESOURCE_AWARE);
        assertNotNull(BatchStrategy.BALANCED);
        assertNotNull(BatchStrategy.NVME_OPTIMIZED);
        assertNotNull(BatchStrategy.HDD_OPTIMIZED);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Should validate batch operation type enum")
    void shouldValidateBatchOperationTypeEnum() {
        // Then
        assertEquals(10, BatchOperationType.values().length);

        // Test all operation types exist
        assertNotNull(BatchOperationType.CHUNKING);
        assertNotNull(BatchOperationType.HASHING);
        assertNotNull(BatchOperationType.STORAGE);
        assertNotNull(BatchOperationType.TRANSFER);
        assertNotNull(BatchOperationType.VERIFICATION);
        assertNotNull(BatchOperationType.COMPRESSION);
        assertNotNull(BatchOperationType.DEDUPLICATION);
        assertNotNull(BatchOperationType.METADATA);
        assertNotNull(BatchOperationType.RECOVERY);
        assertNotNull(BatchOperationType.MAINTENANCE);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Should create batch operation successfully")
    void shouldCreateBatchOperationSuccessfully() {
        // Given
        List<Path> files = Arrays.asList(
                Paths.get("test1.txt"),
                Paths.get("test2.txt"));

        BatchOperation.ResourceRequirements requirements = new BatchOperation.ResourceRequirements(
                1024 * 1024, // 1MB memory
                1, // 1 CPU core
                10, // 10MB/s I/O
                30000 // 30s timeout
        );

        // When
        BatchOperation operation = new BatchOperation(
                "test-op-123",
                BatchOperationType.CHUNKING,
                files,
                BatchPriority.HIGH,
                requirements);

        // Then
        assertNotNull(operation);
        assertEquals("test-op-123", operation.getOperationId());
        assertEquals(BatchOperationType.CHUNKING, operation.getOperationType());
        assertEquals(2, operation.getFiles().size());
        assertEquals(BatchPriority.HIGH, operation.getPriority());
        assertEquals(0, operation.getDependencies().size());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Should create batch result successfully")
    void shouldCreateBatchResultSuccessfully() {
        // Given
        List<Path> files = Arrays.asList(Paths.get("test.txt"));
        Instant startTime = Instant.now();
        Instant endTime = startTime.plusSeconds(10);

        // When
        BatchResult result = new BatchResult(
                "test-batch-456",
                files,
                startTime,
                endTime,
                95,
                5,
                1024L,
                null,
                null,
                null);

        // Then
        assertNotNull(result);
        assertEquals("test-batch-456", result.getBatchId());
        assertEquals(files, result.getFiles());
        assertEquals(startTime, result.getStartTime());
        assertEquals(endTime, result.getEndTime());
        assertTrue(result.isSuccess());
        assertEquals(95, result.getSuccessfulFiles());
        assertEquals(5, result.getFailedFiles());
        assertEquals(10000, result.getProcessingTimeMs());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Should create batch performance metrics successfully")
    void shouldCreateBatchPerformanceMetricsSuccessfully() {
        // When
        BatchPerformanceMetrics metrics = new BatchPerformanceMetrics(
                100.0, // throughput MB/s
                50.0, // avg processing time per file
                1000.0, // avg processing time per batch
                512.0, // peak memory MB
                256.0, // avg memory MB
                75.0, // CPU utilization %
                10.0, // I/O wait %
                90.0, // cache hit rate %
                85.0, // efficiency %
                75.0 // resource utilization score
        );

        // Then
        assertNotNull(metrics);
        assertEquals(100.0, metrics.getThroughputMBps());
        assertEquals(50.0, metrics.getAverageProcessingTimePerFileMs());
        assertEquals(1000.0, metrics.getAverageProcessingTimePerBatchMs());
        assertEquals(512.0, metrics.getPeakMemoryUsageMB());
        assertEquals(256.0, metrics.getAverageMemoryUsageMB());
        assertEquals(75.0, metrics.getCpuUtilizationPercent());
        assertEquals(10.0, metrics.getIoWaitTimePercent());
        assertEquals(90.0, metrics.getCacheHitRatePercent());
        assertEquals(85.0, metrics.getBatchEfficiencyPercent());
        assertEquals(75.0, metrics.getResourceUtilizationScore());
        assertTrue(metrics.isOptimal());
        assertEquals(BatchPerformanceMetrics.PerformanceGrade.A, metrics.getPerformanceGrade());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Should create resource utilization successfully")
    void shouldCreateResourceUtilizationSuccessfully() {
        // When
        ResourceUtilization utilization = new ResourceUtilization(
                80.0, // CPU utilization %
                true, // CPU available
                60.0, // memory utilization %
                90.0, // I/O utilization %
                5, // max concurrent operations
                1024L, // peak memory usage MB
                1000000L, // total bytes read
                5000000L // total bytes written
        );

        // Then
        assertNotNull(utilization);
        assertEquals(80.0, utilization.getCpuUtilizationPercent());
        assertTrue(utilization.isCpuAvailable());
        assertEquals(60.0, utilization.getMemoryUtilizationPercent());
        assertEquals(90.0, utilization.getIoUtilizationPercent());
        assertEquals(5, utilization.getMaxConcurrentOperations());
        assertEquals(1024L, utilization.getPeakMemoryUsageMB());
        assertEquals(1000000L, utilization.getTotalBytesRead());
        assertEquals(5000000L, utilization.getTotalBytesWritten());
    }
}