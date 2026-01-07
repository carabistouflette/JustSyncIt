package com.justsyncit.scanner;

import com.justsyncit.hash.Blake3Service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AbsurdityReproductionTest {

    @TempDir
    Path tempDir;

    @Test
    public void reproduceAsyncHang() throws Exception {
        // Create a file large enough to trigger async IO (>= 1MB)
        Path testFile = tempDir.resolve("large_file.dat");
        byte[] data = new byte[1024 * 1024 + 100]; // 1MB + 100 bytes
        Arrays.fill(data, (byte) 1);
        Files.write(testFile, data);

        Blake3Service blake3Service = new com.justsyncit.ServiceFactory().createBlake3Service();
        FixedSizeFileChunker chunker = FixedSizeFileChunker.create(blake3Service);

        ChunkingOptions options = new ChunkingOptions()
                .withUseAsyncIO(true)
                .withChunkSize(64 * 1024);

        System.out.println("Starting chunking...");
        CompletableFuture<FileChunker.ChunkingResult> future = chunker.chunkFile(testFile, options);

        // This should complete quickly. If it hangs, we found the bug.
        // We set a timeout of 5 seconds.
        FileChunker.ChunkingResult result = future.get(5, TimeUnit.SECONDS);

        System.out.println("Chunking completed!");
        assertNotNull(result);
        System.out.println("File hash: " + result.getFileHash());
    }
}
