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

import com.justsyncit.hash.Blake3Service;
import com.justsyncit.ServiceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class FastCDCFileChunkerTest {

    private Blake3Service blake3Service;
    private FileChunker chunker;
    private ServiceFactory serviceFactory;

    @BeforeEach
    public void setup() throws Exception {
        serviceFactory = new ServiceFactory();
        blake3Service = serviceFactory.createBlake3Service();
        chunker = serviceFactory.createFastCDCFileChunker(blake3Service);
    }

    @Test
    public void testChunkFile(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("test.bin");
        byte[] data = new byte[1024 * 1024]; // 1MB
        new Random(1).nextBytes(data);
        Files.write(file, data);

        ChunkingOptions options = new ChunkingOptions()
                .withAlgorithm(ChunkingOptions.ChunkingAlgorithm.CDC)
                .withMinChunkSize(4 * 1024)
                .withChunkSize(16 * 1024)
                .withMaxChunkSize(64 * 1024);

        FileChunker.ChunkingResult result = chunker.chunkFile(file, options).get();

        if (!result.isSuccess()) {
            fail("Chunking failed: " + result.getError().getMessage(), result.getError());
        }
        assertEquals(file, result.getFile());
        assertEquals(data.length, result.getTotalSize());

        // Verify hashes
        assertNotNull(result.getFileHash());
        assertFalse(result.getChunkHashes().isEmpty());

        // Verify average chunk size is roughly respected (within reason for random
        // data)
        double avgSize = (double) data.length / result.getChunkCount();
        System.out.println("Average chunk size: " + avgSize);
        assertTrue(avgSize >= 4096);
    }

    @Test
    public void testSmallFile(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("small.bin");
        byte[] data = new byte[1000];
        new Random(2).nextBytes(data);
        Files.write(file, data);

        ChunkingOptions options = new ChunkingOptions()
                .withAlgorithm(ChunkingOptions.ChunkingAlgorithm.CDC)
                .withMinChunkSize(2048); // Min size larger than file

        FileChunker.ChunkingResult result = chunker.chunkFile(file, options).get();

        if (!result.isSuccess()) {
            fail("Chunking failed: " + result.getError().getMessage(), result.getError());
        }
        assertTrue(result.isSuccess());
        assertEquals(1, result.getChunkCount()); // Should be 1 chunk
    }
}
