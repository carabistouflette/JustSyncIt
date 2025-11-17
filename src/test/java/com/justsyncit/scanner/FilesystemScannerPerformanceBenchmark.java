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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance benchmarks for filesystem scanner and chunking system.
 * Tests the >500 MB/s disk read requirement.
 */
class FilesystemScannerPerformanceBenchmark {

    @TempDir
    Path tempDir;

    @Test
    @Disabled("Performance benchmark - run manually")
    void benchmarkFileChunkingPerformance() throws Exception {
        ServiceFactory serviceFactory = new ServiceFactory();
        Blake3Service blake3Service = serviceFactory.createBlake3Service();
        FileChunker chunker = new FixedSizeFileChunker(blake3Service);

        // Create test files of various sizes
        int[] fileSizes = {
            1024 * 1024,        // 1MB
            10 * 1024 * 1024,   // 10MB
            100 * 1024 * 1024,  // 100MB
            500 * 1024 * 1024   // 500MB
        };

        for (int fileSize : fileSizes) {
            Path testFile = createTestFile(fileSize);
            
            long startTime = System.nanoTime();
            FileChunker.ChunkingResult result = chunker.chunkFile(testFile, 
                new FileChunker.ChunkingOptions().withUseAsyncIO(true)).get();
            long endTime = System.nanoTime();
            
            double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
            double throughputMBps = (fileSize / (1024.0 * 1024.0)) / durationSeconds;
            
            System.out.printf("File size: %d MB, Duration: %.2f s, Throughput: %.2f MB/s, Chunks: %d%n",
                fileSize / (1024 * 1024), durationSeconds, throughputMBps, result.getChunkCount());
            
            assertTrue(result.isSuccess(), "Chunking should succeed");
            assertTrue(throughputMBps >= 500.0, 
                String.format("Throughput %.2f MB/s should meet >= 500 MB/s requirement", throughputMBps));
            
            // Clean up
            Files.deleteIfExists(testFile);
        }
    }

    @Test
    @Disabled("Performance benchmark - run manually")
    void benchmarkScanningPerformance() throws Exception {
        // Create directory with many files
        int fileCount = 1000;
        int fileSizeKB = 100; // 100KB per file
        long totalSizeBytes = (long) fileCount * fileSizeKB * 1024;
        
        for (int i = 0; i < fileCount; i++) {
            Path file = tempDir.resolve(String.format("file_%04d.dat", i));
            createTestFileOfSize(file, fileSizeKB * 1024);
        }
        
        FilesystemScanner scanner = new NioFilesystemScanner();
        
        long startTime = System.nanoTime();
        ScanResult result = scanner.scanDirectory(tempDir, new ScanOptions()).get();
        long endTime = System.nanoTime();
        
        double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
        double throughputMBps = (totalSizeBytes / (1024.0 * 1024.0)) / durationSeconds;
        
        System.out.printf("Scanned %d files (%.1f MB), Duration: %.2f s, Throughput: %.2f MB/s%n",
            fileCount, totalSizeBytes / (1024.0 * 1024.0), durationSeconds, throughputMBps);
        
        assertEquals(fileCount, result.getScannedFileCount(), "All files should be scanned");
        assertTrue(throughputMBps >= 500.0, 
            String.format("Scanning throughput %.2f MB/s should meet >= 500 MB/s requirement", throughputMBps));
    }

    @Test
    @Disabled("Performance benchmark - run manually")
    void benchmarkAsyncVsSyncChunking() throws Exception {
        ServiceFactory serviceFactory = new ServiceFactory();
        Blake3Service blake3Service = serviceFactory.createBlake3Service();
        FileChunker asyncChunker = new FixedSizeFileChunker(blake3Service);
        FileChunker syncChunker = new FixedSizeFileChunker(blake3Service);
        
        int fileSize = 50 * 1024 * 1024; // 50MB
        Path testFile = createTestFile(fileSize);
        
        // Benchmark async chunking
        long asyncStart = System.nanoTime();
        FileChunker.ChunkingResult asyncResult = asyncChunker.chunkFile(testFile,
            new FileChunker.ChunkingOptions().withUseAsyncIO(true)).get();
        long asyncEnd = System.nanoTime();
        
        // Benchmark sync chunking
        long syncStart = System.nanoTime();
        FileChunker.ChunkingResult syncResult = syncChunker.chunkFile(testFile,
            new FileChunker.ChunkingOptions().withUseAsyncIO(false)).get();
        long syncEnd = System.nanoTime();
        
        double asyncDuration = (asyncEnd - asyncStart) / 1_000_000_000.0;
        double syncDuration = (syncEnd - syncStart) / 1_000_000_000.0;
        
        System.out.printf("Async chunking: %.2f s, Sync chunking: %.2f s, Speedup: %.2fx%n",
            asyncDuration, syncDuration, syncDuration / asyncDuration);
        
        assertTrue(asyncResult.isSuccess(), "Async chunking should succeed");
        assertTrue(syncResult.isSuccess(), "Sync chunking should succeed");
        assertEquals(asyncResult.getChunkCount(), syncResult.getChunkCount(), "Should produce same number of chunks");
        
        // Clean up
        Files.deleteIfExists(testFile);
    }

    @Test
    @Disabled("Memory stress test - run manually")
    void benchmarkMemoryUsage() throws Exception {
        ServiceFactory serviceFactory = new ServiceFactory();
        Blake3Service blake3Service = serviceFactory.createBlake3Service();
        
        // Test with different buffer pool sizes
        int[] bufferSizes = {16, 32, 64, 128, 256}; // KB
        int fileSize = 100 * 1024 * 1024; // 100MB
        
        for (int bufferSizeKB : bufferSizes) {
            BufferPool bufferPool = new ByteBufferPool(bufferSizeKB * 1024, 8);
            FileChunker chunker = new FixedSizeFileChunker(blake3Service, bufferPool, 64 * 1024);
            
            Path testFile = createTestFile(fileSize);
            
            Runtime runtime = Runtime.getRuntime();
            long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
            
            FileChunker.ChunkingResult result = chunker.chunkFile(testFile,
                new FileChunker.ChunkingOptions()).get();
            
            long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
            long memoryUsed = memoryAfter - memoryBefore;
            
            System.out.printf("Buffer size: %d KB, Memory used: %.1f MB, Chunks: %d%n",
                bufferSizeKB, memoryUsed / (1024.0 * 1024.0), result.getChunkCount());
            
            assertTrue(result.isSuccess(), "Chunking should succeed");
            
            // Clean up
            Files.deleteIfExists(testFile);
            bufferPool.clear();
            
            // Force garbage collection
            System.gc();
            Thread.sleep(100);
        }
    }

    private Path createTestFile(int sizeBytes) throws IOException {
        Path file = tempDir.resolve("test_file_" + sizeBytes + ".dat");
        return createTestFileOfSize(file, sizeBytes);
    }

    private Path createTestFileOfSize(Path file, int sizeBytes) throws IOException {
        byte[] data = new byte[sizeBytes];
        new Random().nextBytes(data);
        Files.write(file, data);
        return file;
    }
}