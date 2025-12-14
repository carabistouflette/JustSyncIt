/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.justsyncit.network.compression;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZstdCompressionServiceTest {

    @Test
    void testCompressAndDecompress() throws IOException {
        ZstdCompressionService service = new ZstdCompressionService();
        String originalText = "Hello, World! This is a test string for Zstd compression.";
        byte[] originalData = originalText.getBytes(StandardCharsets.UTF_8);

        byte[] compressedData = service.compress(originalData);
        assertNotNull(compressedData);

        byte[] decompressedData = service.decompress(compressedData);
        assertArrayEquals(originalData, decompressedData);
    }

    @Test
    void testCompressionRatio() throws IOException {
        ZstdCompressionService service = new ZstdCompressionService(3);

        // Generate a repetitive string that should compress well
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("RepeatMe ");
        }
        byte[] originalData = sb.toString().getBytes(StandardCharsets.UTF_8);

        byte[] compressedData = service.compress(originalData);

        assertTrue(compressedData.length < originalData.length,
                "Compressed data should be smaller than original data");

        System.out.println("Original size: " + originalData.length);
        System.out.println("Compressed size: " + compressedData.length);
    }

    @Test
    void testBinaryData() throws IOException {
        ZstdCompressionService service = new ZstdCompressionService();
        byte[] originalData = new byte[1024 * 64]; // 64KB
        new Random().nextBytes(originalData);

        byte[] compressedData = service.compress(originalData);
        byte[] decompressedData = service.decompress(compressedData);

        assertArrayEquals(originalData, decompressedData);
    }

    @Test
    void testAlgorithmName() {
        ZstdCompressionService service = new ZstdCompressionService();
        assertEquals("ZSTD", service.getAlgorithmName());
    }
}
