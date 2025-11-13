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

import com.justsyncit.TestServiceFactory;
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
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Performance benchmarks for BLAKE3 hashing implementation.
 * Tests various data sizes and hashing patterns to measure throughput.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class Blake3PerformanceBenchmark {

    /** BLAKE3 service instance. */
    private Blake3Service blake3Service;
    /** Small test data (64 bytes). */
    private byte[] smallData;
    /** Medium test data (1 KB). */
    private byte[] mediumData;
    /** Large test data (1 MB). */
    private byte[] largeData;
    /** Huge test data (10 MB). */
    private byte[] hugeData;
    /** Random number generator. */
    private static final Random random = new Random(12345); // Fixed seed for reproducible results

    @Setup
    public void benchmarkSetup() {
        blake3Service = TestServiceFactory.createBlake3Service();

        // Prepare test data of various sizes
        smallData = new byte[64]; // 64 bytes
        mediumData = new byte[1024]; // 1 KB
        largeData = new byte[1024 * 1024]; // 1 MB
        hugeData = new byte[10 * 1024 * 1024]; // 10 MB

        random.nextBytes(smallData);
        random.nextBytes(mediumData);
        random.nextBytes(largeData);
        random.nextBytes(hugeData);
    }

    @Benchmark
    public String hashSmallData() {
        return blake3Service.hashBuffer(smallData);
    }

    @Benchmark
    public String hashMediumData() {
        return blake3Service.hashBuffer(mediumData);
    }

    @Benchmark
    public String hashLargeData() {
        return blake3Service.hashBuffer(largeData);
    }

    @Benchmark
    public String hashHugeData() {
        return blake3Service.hashBuffer(hugeData);
    }

    @Benchmark
    public String hashIncrementalSmall() {
        Blake3Service.Blake3IncrementalHasher hasher = blake3Service.createIncrementalHasher();
        hasher.update(smallData);
        return hasher.digest();
    }

    @Benchmark
    public String hashIncrementalMedium() {
        Blake3Service.Blake3IncrementalHasher hasher = blake3Service.createIncrementalHasher();
        hasher.update(mediumData);
        return hasher.digest();
    }

    @Benchmark
    public String hashIncrementalLarge() {
        Blake3Service.Blake3IncrementalHasher hasher = blake3Service.createIncrementalHasher();
        hasher.update(largeData);
        return hasher.digest();
    }

    @Benchmark
    public String hashIncrementalHuge() {
        Blake3Service.Blake3IncrementalHasher hasher = blake3Service.createIncrementalHasher();
        hasher.update(hugeData);
        return hasher.digest();
    }

    @Benchmark
    public String hashIncrementalInChunks() {
        Blake3Service.Blake3IncrementalHasher hasher = blake3Service.createIncrementalHasher();

        // Update in 1KB chunks
        int chunkSize = 1024;
        for (int i = 0; i < hugeData.length; i += chunkSize) {
            int length = Math.min(chunkSize, hugeData.length - i);
            hasher.update(hugeData, i, length);
        }

        return hasher.digest();
    }

    @Benchmark
    public String hashZerosSmall() {
        byte[] zeros = new byte[64];
        return blake3Service.hashBuffer(zeros);
    }

    @Benchmark
    public String hashZerosMedium() {
        byte[] zeros = new byte[1024];
        return blake3Service.hashBuffer(zeros);
    }

    @Benchmark
    public String hashZerosLarge() {
        byte[] zeros = new byte[1024 * 1024];
        return blake3Service.hashBuffer(zeros);
    }

    @Benchmark
    public String hashSequentialSmall() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("test data ");
            sb.append(i);
        }
        return blake3Service.hashBuffer(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Benchmark
    public String hashSequentialMedium() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("test data ");
            sb.append(i);
        }
        return blake3Service.hashBuffer(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Main method to run the benchmarks.
     */
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(Blake3PerformanceBenchmark.class.getSimpleName())
                .forks(1)
                .warmupIterations(3)
                .measurementIterations(5)
                .shouldDoGC(true)
                .jvmArgs("-Xmx2g")
                .build();
        new Runner(opt).run();
    }
}