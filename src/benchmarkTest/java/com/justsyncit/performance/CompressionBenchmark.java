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
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class CompressionBenchmark {

    private ZstdCompressionService compressionService;
    private byte[] compressibleData;
    private byte[] randomData;
    private byte[] compressedCompressibleData;
    private byte[] compressedRandomData;

    @Setup
    public void setup() throws IOException {
        compressionService = new ZstdCompressionService(3); // Default level

        // Prepare compressible data (text + repetition)
        StringBuilder sb = new StringBuilder();
        String base = "JustSyncIt is a modern backup solution. ";
        for (int i = 0; i < 2000; i++) {
            sb.append(base).append(i).append(" ");
        }
        compressibleData = sb.toString().getBytes(StandardCharsets.UTF_8);

        // Prepare random data (hard to compress)
        randomData = new byte[compressibleData.length];
        new Random(42).nextBytes(randomData);

        // Pre-compress for decompression tests
        compressedCompressibleData = compressionService.compress(compressibleData);
        compressedRandomData = compressionService.compress(randomData);
    }

    @Benchmark
    public byte[] compressCompressible() throws IOException {
        return compressionService.compress(compressibleData);
    }

    @Benchmark
    public byte[] compressRandom() throws IOException {
        return compressionService.compress(randomData);
    }

    @Benchmark
    public byte[] decompressCompressible() throws IOException {
        return compressionService.decompress(compressedCompressibleData);
    }

    @Benchmark
    public byte[] decompressRandom() throws IOException {
        return compressionService.decompress(compressedRandomData);
    }
}
