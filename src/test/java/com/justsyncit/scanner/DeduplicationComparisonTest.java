package com.justsyncit.scanner;

import com.justsyncit.hash.Blake3Service;
import com.justsyncit.ServiceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comparison test to verify that CDC provides better deduplication than
 * Fixed-Size chunking
 * when data is shifted (insertion/deletion).
 */
public class DeduplicationComparisonTest {

    private Blake3Service blake3Service;
    private FileChunker fixedChunker;
    private FileChunker cdcChunker;
    private ServiceFactory serviceFactory;

    @BeforeEach
    public void setup() throws Exception {
        serviceFactory = new ServiceFactory();
        blake3Service = serviceFactory.createBlake3Service();
        fixedChunker = serviceFactory.createFileChunker(blake3Service, ChunkingOptions.ChunkingAlgorithm.FIXED);
        cdcChunker = serviceFactory.createFastCDCFileChunker(blake3Service);
    }

    @Test
    public void testShiftResistance(@TempDir Path tempDir) throws Exception {
        // 1. Generate base file
        int size = 1024 * 1024; // 1MB
        byte[] baseData = new byte[size];
        new Random(12345).nextBytes(baseData);
        Path fileA = tempDir.resolve("fileA.bin");
        Files.write(fileA, baseData);

        // 2. Generate shifted file (insert 100 bytes at the beginning)
        byte[] shiftedData = new byte[size + 100];
        byte[] shift = new byte[100];
        new Random(67890).nextBytes(shift);

        System.arraycopy(shift, 0, shiftedData, 0, 100);
        System.arraycopy(baseData, 0, shiftedData, 100, size);

        Path fileB = tempDir.resolve("fileB.bin");
        Files.write(fileB, shiftedData);

        // 3. Chunk with Fixed Size
        int chunkSize = 4096;
        ChunkingOptions fixedOps = new ChunkingOptions()
                .withAlgorithm(ChunkingOptions.ChunkingAlgorithm.FIXED)
                .withChunkSize(chunkSize);

        FileChunker.ChunkingResult fixedA = fixedChunker.chunkFile(fileA, fixedOps).get();
        FileChunker.ChunkingResult fixedB = fixedChunker.chunkFile(fileB, fixedOps).get();

        long fixedCommonBytes = countCommonBytes(fixedA, fixedB, chunkSize);
        double fixedRatio = (double) fixedCommonBytes / size;

        System.out.println("Fixed Size Common Bytes: " + fixedCommonBytes + " Ratio: " + fixedRatio);

        // 4. Chunk with CDC
        // Use standard settings for CDC: min=2KB, avg=4KB, max=16KB
        ChunkingOptions cdcOps = new ChunkingOptions()
                .withAlgorithm(ChunkingOptions.ChunkingAlgorithm.CDC)
                .withMinChunkSize(2048)
                .withChunkSize(4096)
                .withMaxChunkSize(16384);

        FileChunker.ChunkingResult cdcA = cdcChunker.chunkFile(fileA, cdcOps).get();
        FileChunker.ChunkingResult cdcB = cdcChunker.chunkFile(fileB, cdcOps).get();

        // Approximate bytes for CDC (sum of common hashes * avg chunk size is rough,
        // precise is harder without size map)
        // Ideally ChunkingResult should allow retrieving chunk sizes or we
        // re-calculate.
        // For verify, we can just look at number of common hashes vs total hashes.

        long cdcCommonChunks = countCommonChunks(cdcA, cdcB);
        long totalChunksA = cdcA.getChunkCount();
        double cdcRatio = (double) cdcCommonChunks / totalChunksA;

        System.out.println("CDC Common Chunks: " + cdcCommonChunks + "/" + totalChunksA + " Ratio: " + cdcRatio);

        // 5. Assert CDC is better
        // Fixed size should fail almost completely (ratio near 0 or < 5% due to
        // alignment luck or end)
        // CDC should recover most chunks (ratio > 80% ideally)

        assertTrue(cdcRatio > fixedRatio + 0.20, "CDC should be at least 20% better than Fixed Size");
        assertTrue(cdcRatio > 0.80, "CDC should detect > 80% common content");
    }

    private long countCommonBytes(FileChunker.ChunkingResult res1, FileChunker.ChunkingResult res2, int chunkSize) {
        Set<String> set1 = new HashSet<>(res1.getChunkHashes());
        List<String> list2 = res2.getChunkHashes();

        long common = 0;
        for (String h : list2) {
            if (set1.contains(h)) {
                common += chunkSize;
            }
        }
        return common;
    }

    private long countCommonChunks(FileChunker.ChunkingResult res1, FileChunker.ChunkingResult res2) {
        Set<String> set1 = new HashSet<>(res1.getChunkHashes());
        List<String> list2 = res2.getChunkHashes();

        long common = 0;
        for (String h : list2) {
            if (set1.contains(h)) {
                common++;
            }
        }
        return common;
    }
}
