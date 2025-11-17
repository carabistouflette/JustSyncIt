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

package com.justsyncit.scanner;

import com.justsyncit.ServiceFactory;
import com.justsyncit.hash.Blake3Service;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance benchmarks for the filesystem scanner and chunking system.
 * Verifies the >500 MB/s disk read requirement.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class FilesystemScannerBenchmark {

    private FilesystemScanner scanner;
    private FixedSizeFileChunker chunker;
    private Blake3Service blake3Service;
    private Path testDir;
    private Path largeFile;
    private static final long TARGET_FILE_SIZE = 100 * 1024 * 1024; // 100MB
    private static final long MIN_THROUGHPUT_MBPS = 500; // 500 MB/s requirement

    @Setup
    public void setup() throws Exception {
        // Initialize services
        ServiceFactory serviceFactory = new ServiceFactory();
        blake3Service = serviceFactory.createBlake3Service();
        scanner = new NioFilesystemScanner();
        chunker = new FixedSizeFileChunker(blake3Service);
        
        // Create test directory and large file
        testDir = Files.createTempDirectory("benchmark-test");
        largeFile = testDir.resolve("large-test-file.dat");
        
        // Create a large file with random-like content
        byte[] data = new byte[(int) TARGET_FILE_SIZE];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 256);
        }
        Files.write(largeFile, data);
        
        System.out.println("Created test file of size: " + TARGET_FILE_SIZE / (1024 * 1024) + " MB");
    }

    @TearDown
    public void tearDown() throws IOException {
        if (chunker != null) {
            chunker.close();
        }
        // Clean up test files
        if (testDir != null && Files.exists(testDir)) {
            java.nio.file.Files.walk(testDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore cleanup errors
                        }
                    });
        }
    }

    @Benchmark
    public void benchmarkScanningOnly() throws Exception {
        ScanOptions options = new ScanOptions()
                .withIncludePattern(Paths.get("*.dat").getFileSystem().getPathMatcher("glob:*.dat"));
        
        scanner.scanDirectory(testDir, options).get();
    }

    @Benchmark
    public void benchmarkChunkingOnly() throws Exception {
        FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions()
                .withChunkSize(64 * 1024) // 64KB chunks
                .withUseAsyncIO(true)
                .withDetectSparseFiles(true);
        
        chunker.chunkFile(largeFile, options).get();
    }

    @Benchmark
    public void benchmarkScanningAndChunking() throws Exception {
        ScanOptions scanOptions = new ScanOptions()
                .withIncludePattern(Paths.get("*.dat").getFileSystem().getPathMatcher("glob:*.dat"));
        
        FileChunker.ChunkingOptions chunkOptions = new FileChunker.ChunkingOptions()
                .withChunkSize(64 * 1024) // 64KB chunks
                .withUseAsyncIO(true)
                .withDetectSparseFiles(true);
        
        // Perform scanning
        ScanResult scanResult = scanner.scanDirectory(testDir, scanOptions).get();
        
        // Perform chunking on all files found
        for (ScanResult.ScannedFile file : scanResult.getScannedFiles()) {
            if (!file.isSymbolicLink()) {
                chunker.chunkFile(file.getPath(), chunkOptions).get();
            }
        }
    }

    @Benchmark
    public void benchmarkAsyncVsSyncChunking() throws Exception {
        FileChunker.ChunkingOptions asyncOptions = new FileChunker.ChunkingOptions()
                .withChunkSize(64 * 1024)
                .withUseAsyncIO(true)
                .withDetectSparseFiles(true);
        
        FileChunker.ChunkingOptions syncOptions = new FileChunker.ChunkingOptions()
                .withChunkSize(64 * 1024)
                .withUseAsyncIO(false)
                .withDetectSparseFiles(true);
        
        // Test async chunking
        chunker.chunkFile(largeFile, asyncOptions).get();
        
        // Test sync chunking
        chunker.chunkFile(largeFile, syncOptions).get();
    }

    @Benchmark
    public void benchmarkDifferentChunkSizes() throws Exception {
        // Test different chunk sizes to find optimal performance
        int[] chunkSizes = {32 * 1024, 64 * 1024, 128 * 1024, 256 * 1024}; // 32KB, 64KB, 128KB, 256KB
        
        for (int chunkSize : chunkSizes) {
            FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions()
                    .withChunkSize(chunkSize)
                    .withUseAsyncIO(true)
                    .withDetectSparseFiles(true);
            
            chunker.chunkFile(largeFile, options).get();
        }
    }

    /**
     * Simple test to verify performance requirements are met.
     * This is not a JMH benchmark but a verification test.
     */
    public static void verifyPerformanceRequirements() throws Exception {
        System.out.println("Running performance verification test...");
        
        // Setup
        ServiceFactory serviceFactory = new ServiceFactory();
        Blake3Service blake3Service = serviceFactory.createBlake3Service();
        FixedSizeFileChunker chunker = new FixedSizeFileChunker(blake3Service);
        
        Path testDir = Files.createTempDirectory("perf-test");
        Path testFile = testDir.resolve("perf-test-file.dat");
        
        // Create 100MB test file
        byte[] data = new byte[100 * 1024 * 1024];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 256);
        }
        Files.write(testFile, data);
        
        try {
            // Benchmark chunking
            FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions()
                    .withChunkSize(64 * 1024)
                    .withUseAsyncIO(true)
                    .withDetectSparseFiles(true);
            
            long startTime = System.nanoTime();
            FileChunker.ChunkingResult result = chunker.chunkFile(testFile, options).get();
            long endTime = System.nanoTime();
            
            // Calculate throughput
            double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
            double fileSizeMB = result.getTotalSize() / (1024.0 * 1024.0);
            double throughputMBps = fileSizeMB / durationSeconds;
            
            System.out.printf("File size: %.2f MB\n", fileSizeMB);
            System.out.printf("Duration: %.3f seconds\n", durationSeconds);
            System.out.printf("Throughput: %.2f MB/s\n", throughputMBps);
            System.out.printf("Chunks: %d\n", result.getChunkCount());
            
            // Verify requirement
            if (throughputMBps >= MIN_THROUGHPUT_MBPS) {
                System.out.printf("✓ PERFORMANCE REQUIREMENT MET: %.2f MB/s >= %d MB/s\n", 
                        throughputMBps, MIN_THROUGHPUT_MBPS);
            } else {
                System.out.printf("✗ PERFORMANCE REQUIREMENT NOT MET: %.2f MB/s < %d MB/s\n", 
                        throughputMBps, MIN_THROUGHPUT_MBPS);
            }
            
            assertTrue(throughputMBps >= MIN_THROUGHPUT_MBPS, 
                    "Performance requirement not met: " + throughputMBps + " MB/s < " + MIN_THROUGHPUT_MBPS + " MB/s");
            
        } finally {
            chunker.close();
            // Cleanup
            Files.walk(testDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore cleanup errors
                        }
                    });
        }
    }

    /**
     * Main method to run benchmarks.
     */
    public static void main(String[] args) throws RunnerException {
        if (args.length > 0 && args[0].equals("verify")) {
            try {
                verifyPerformanceRequirements();
            } catch (Exception e) {
                System.err.println("Performance verification failed: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            Options opt = new OptionsBuilder()
                    .include(FilesystemScannerBenchmark.class.getSimpleName())
                    .build();
            new Runner(opt).run();
        }
    }
}