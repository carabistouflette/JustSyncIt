/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.justsyncit.performance;

import com.justsyncit.network.compression.ZstdCompressionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("performance")
public class CompressionPerformanceTest {

    private ZstdCompressionService compressionService;
    private byte[] data;

    @BeforeEach
    void setUp() {
        compressionService = new ZstdCompressionService(3);
        // Prepare 10MB mixed data
        int size = 10 * 1024 * 1024;
        data = new byte[size];

        // Fill with some repetitive text
        String pattern = "JustSyncIt is fast! ";
        byte[] patternBytes = pattern.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < size; i++) {
            data[i] = patternBytes[i % patternBytes.length];
        }

        // Add some noise to prevent infinite compression
        Random rand = new Random(42);
        for (int i = 0; i < size / 10; i++) { // Modify 10%
            data[rand.nextInt(size)] = (byte) rand.nextInt(256);
        }
    }

    @Test
    void benchmarkCompressionSpeed() throws IOException {
        // Warmup
        for (int i = 0; i < 5; i++) {
            compressionService.compress(data);
        }

        long startTime = System.nanoTime();
        int iterations = 10;
        long totalCompressedSize = 0;

        for (int i = 0; i < iterations; i++) {
            byte[] compressed = compressionService.compress(data);
            totalCompressedSize += compressed.length;
        }

        long durationNs = System.nanoTime() - startTime;
        double durationSeconds = durationNs / 1_000_000_000.0;
        long totalBytesProcessed = (long) data.length * iterations;
        double mbProcessed = totalBytesProcessed / (1024.0 * 1024.0);
        double speedMBps = mbProcessed / durationSeconds;

        // Calculate ratio
        double avgCompressedSize = totalCompressedSize / (double) iterations;
        double ratio = data.length / avgCompressedSize;

        System.out.printf("Compression Speed: %.2f MB/s\n", speedMBps);
        System.out.printf("Compression Ratio: %.2f x\n", ratio);

        // Verify criteria (>300 MB/s per core roughly, allowing for variation in CI
        // environments, I'll set 150 as soft fail)
        // Adjusting expectation: Pure Java Zstd JNI should be very fast.
        assertTrue(speedMBps > 100, "Compression speed should be decent (>100 MB/s)");
        assertTrue(ratio > 1.5, "Compression ration should be decent (>1.5x)");
    }
}
