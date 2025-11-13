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

package com.justsyncit.hash;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance tests for BLAKE3 hashing implementation.
 * Measures throughput for various data sizes and patterns.
 */
class Blake3PerformanceTest {

    private static final int WARMUP_ITERATIONS = 1000;
    private static final int MEASUREMENT_ITERATIONS = 10000;

    @Test
    @Disabled("Performance test - run manually when needed")
    void testSmallDataPerformance() {
        Blake3Service service = new Blake3ServiceImpl();
        byte[] data = new byte[64]; // 64 bytes
        new Random(12345).nextBytes(data);

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            service.hashBuffer(data);
        }

        // Measurement
        long startTime = System.nanoTime();
        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            service.hashBuffer(data);
        }
        long endTime = System.nanoTime();

        double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
        double throughputMBps = (data.length * MEASUREMENT_ITERATIONS) / (1024.0 * 1024.0) / durationSeconds;
        double throughputGbps = throughputMBps / 1024.0;

        System.out.printf("Small data (64B): %.2f MB/s (%.3f GB/s)%n", throughputMBps, throughputGbps);
        assertTrue(throughputGbps > 0.1, "Small data throughput should be reasonable");
    }

    @Test
    @Disabled("Performance test - run manually when needed")
    void testMediumDataPerformance() {
        Blake3Service service = new Blake3ServiceImpl();
        byte[] data = new byte[1024]; // 1 KB
        new Random(12345).nextBytes(data);

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            service.hashBuffer(data);
        }

        // Measurement
        long startTime = System.nanoTime();
        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            service.hashBuffer(data);
        }
        long endTime = System.nanoTime();

        double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
        double throughputMBps = (data.length * MEASUREMENT_ITERATIONS) / (1024.0 * 1024.0) / durationSeconds;
        double throughputGbps = throughputMBps / 1024.0;

        System.out.printf("Medium data (1KB): %.2f MB/s (%.3f GB/s)%n", throughputMBps, throughputGbps);
        assertTrue(throughputGbps > 0.1, "Medium data throughput should be reasonable");
    }

    @Test
    @Disabled("Performance test - run manually when needed")
    void testLargeDataPerformance() {
        Blake3Service service = new Blake3ServiceImpl();
        byte[] data = new byte[1024 * 1024]; // 1 MB
        new Random(12345).nextBytes(data);

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS / 10; i++) {
            service.hashBuffer(data);
        }

        // Measurement
        long startTime = System.nanoTime();
        for (int i = 0; i < MEASUREMENT_ITERATIONS / 100; i++) {
            service.hashBuffer(data);
        }
        long endTime = System.nanoTime();

        double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
        double throughputMBps = (data.length * (MEASUREMENT_ITERATIONS / 100)) / (1024.0 * 1024.0) / durationSeconds;
        double throughputGbps = throughputMBps / 1024.0;

        System.out.printf("Large data (1MB): %.2f MB/s (%.3f GB/s)%n", throughputMBps, throughputGbps);
        assertTrue(throughputGbps > 0.01, "Large data throughput should be reasonable");
    }

    @Test
    @Disabled("Performance test - run manually when needed")
    void testIncrementalHashingPerformance() {
        Blake3Service service = new Blake3ServiceImpl();
        byte[] data = new byte[1024 * 1024]; // 1 MB
        new Random(12345).nextBytes(data);

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS / 10; i++) {
            Blake3Service.Blake3IncrementalHasher hasher = service.createIncrementalHasher();
            hasher.update(data);
            hasher.digest();
        }

        // Measurement
        long startTime = System.nanoTime();
        for (int i = 0; i < MEASUREMENT_ITERATIONS / 100; i++) {
            Blake3Service.Blake3IncrementalHasher hasher = service.createIncrementalHasher();
            hasher.update(data);
            hasher.digest();
        }
        long endTime = System.nanoTime();

        double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
        double throughputMBps = (data.length * (MEASUREMENT_ITERATIONS / 100)) / (1024.0 * 1024.0) / durationSeconds;
        double throughputGbps = throughputMBps / 1024.0;

        System.out.printf("Incremental (1MB): %.2f MB/s (%.3f GB/s)%n", throughputMBps, throughputGbps);
        assertTrue(throughputGbps > 0.01, "Incremental hashing throughput should be reasonable");
    }

    @Test
    @Disabled("Performance test - run manually when needed")
    void testChunkedHashingPerformance() {
        Blake3Service service = new Blake3ServiceImpl();
        byte[] data = new byte[10 * 1024 * 1024]; // 10 MB
        new Random(12345).nextBytes(data);

        // Warmup
        for (int i = 0; i < 10; i++) {
            Blake3Service.Blake3IncrementalHasher hasher = service.createIncrementalHasher();
            
            // Update in 1KB chunks
            int chunkSize = 1024;
            for (int j = 0; j < data.length; j += chunkSize) {
                int length = Math.min(chunkSize, data.length - j);
                hasher.update(data, j, length);
            }
            hasher.digest();
        }

        // Measurement
        long startTime = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            Blake3Service.Blake3IncrementalHasher hasher = service.createIncrementalHasher();
            
            // Update in 1KB chunks
            int chunkSize = 1024;
            for (int j = 0; j < data.length; j += chunkSize) {
                int length = Math.min(chunkSize, data.length - j);
                hasher.update(data, j, length);
            }
            hasher.digest();
        }
        long endTime = System.nanoTime();

        double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
        double throughputMBps = (data.length * 100) / (1024.0 * 1024.0) / durationSeconds;
        double throughputGbps = throughputMBps / 1024.0;

        System.out.printf("Chunked (10MB): %.2f MB/s (%.3f GB/s)%n", throughputMBps, throughputGbps);
        assertTrue(throughputGbps > 0.01, "Chunked hashing throughput should be reasonable");
    }

    @Test
    @Disabled("Performance test - run manually when needed")
    void compareDirectVsIncremental() {
        Blake3Service service = new Blake3ServiceImpl();
        byte[] data = new byte[1024]; // 1 KB
        new Random(12345).nextBytes(data);

        // Direct hashing
        long startTime = System.nanoTime();
        String directHash = null;
        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            directHash = service.hashBuffer(data);
        }
        long directTime = System.nanoTime() - startTime;

        // Incremental hashing
        startTime = System.nanoTime();
        String incrementalHash = null;
        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            Blake3Service.Blake3IncrementalHasher hasher = service.createIncrementalHasher();
            hasher.update(data);
            incrementalHash = hasher.digest();
        }
        long incrementalTime = System.nanoTime() - startTime;

        // Verify hashes are identical
        assertEquals(directHash, incrementalHash, "Direct and incremental hashes should be identical");

        double directThroughputMBps = (data.length * MEASUREMENT_ITERATIONS) / (1024.0 * 1024.0) / (directTime / 1_000_000_000.0);
        double incrementalThroughputMBps = (data.length * MEASUREMENT_ITERATIONS) / (1024.0 * 1024.0) / (incrementalTime / 1_000_000_000.0);

        System.out.printf("Direct (1KB): %.2f MB/s%n", directThroughputMBps);
        System.out.printf("Incremental (1KB): %.2f MB/s%n", incrementalThroughputMBps);
        System.out.printf("Performance ratio: %.2fx%n", directThroughputMBps / incrementalThroughputMBps);

        // Both should have reasonable performance
        assertTrue(directThroughputMBps > 0.1, "Direct hashing should be reasonable");
        assertTrue(incrementalThroughputMBps > 0.1, "Incremental hashing should be reasonable");
    }

    /**
     * Manual performance test that can be run to get current performance metrics.
     * This method provides a quick performance check without being disabled.
     */
    @Test
    void quickPerformanceCheck() {
        Blake3Service service = new Blake3ServiceImpl();
        Blake3Service.Blake3Info info = service.getInfo();
        
        System.out.println("BLAKE3 Implementation Info:");
        System.out.println("  Version: " + info.getVersion());
        System.out.println("  SIMD Support: " + info.hasSimdSupport());
        System.out.println("  SIMD Instruction Set: " + info.getSimdInstructionSet());
        System.out.println("  JNI Implementation: " + info.isJniImplementation());
        
        // Test with various data sizes
        int[] sizes = {64, 1024, 1024 * 1024, 10 * 1024 * 1024};
        String[] sizeNames = {"64B", "1KB", "1MB", "10MB"};
        
        for (int i = 0; i < sizes.length; i++) {
            byte[] data = new byte[sizes[i]];
            Arrays.fill(data, (byte) 0x42);
            
            // Warmup
            for (int j = 0; j < 100; j++) {
                service.hashBuffer(data);
            }
            
            // Measure
            int iterations = Math.max(1, 1000000 / sizes[i]); // Scale iterations based on size
            long startTime = System.nanoTime();
            for (int j = 0; j < iterations; j++) {
                service.hashBuffer(data);
            }
            long endTime = System.nanoTime();
            
            double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
            double throughputMBps = (sizes[i] * iterations) / (1024.0 * 1024.0) / durationSeconds;
            double throughputGbps = throughputMBps / 1024.0;
            
            System.out.printf("%s: %.2f MB/s (%.3f GB/s) - %d iterations%n", 
                sizeNames[i], throughputMBps, throughputGbps, iterations);
        }
        
        // Basic performance assertion - should handle at least 1MB/s for small data
        byte[] smallData = new byte[1024];
        Arrays.fill(smallData, (byte) 0x42);
        
        long startTime = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            service.hashBuffer(smallData);
        }
        long endTime = System.nanoTime();
        
        double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
        double throughputMBps = (1024.0 * 1000) / (1024.0 * 1024.0) / durationSeconds;
        
        System.out.printf("Basic performance check: %.2f MB/s%n", throughputMBps);
        assertTrue(throughputMBps > 1.0, "Should achieve at least 1MB/s for 1KB data");
    }
}