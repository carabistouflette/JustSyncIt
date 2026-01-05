/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.justsyncit.integrity;

import com.justsyncit.ServiceFactory;
import com.justsyncit.hash.Blake3Service;
import com.justsyncit.integrity.service.IntegrityCheckService;
import com.justsyncit.integrity.service.ReedSolomonService;
import com.justsyncit.storage.ContentStore;
import com.justsyncit.storage.FilesystemContentStore;
import com.justsyncit.storage.HealingContentStore;
import com.justsyncit.storage.metadata.MetadataService;
import com.justsyncit.storage.metadata.MetadataServiceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class IntegrityE2ETest {

    @TempDir
    Path tempDir;

    private MetadataService metadataService;
    private Blake3Service blake3Service;
    private ContentStore rawStore;
    private HealingContentStore healingStore;
    private ReedSolomonService rsService;
    private IntegrityCheckService integrityCheckService;
    private ServiceFactory serviceFactory;

    @BeforeEach
    void setUp() throws Exception {
        serviceFactory = new ServiceFactory();
        blake3Service = serviceFactory.createBlake3Service();

        // Use file-based DB for persistence across service resets if needed, but
        // in-memory is faster
        // We'll use file-based to be closer to real scenario
        Path dbPath = tempDir.resolve("metadata.db");
        metadataService = MetadataServiceFactory.createFileBasedService(dbPath.toString());

        // Setup raw store in temp dir
        Path storageDir = tempDir.resolve("storage");
        Files.createDirectories(storageDir);

        // We need to bypass ServiceFactory for raw store creation to control path
        // But ServiceFactory.createDefaultSqliteStore uses default path...
        // Let's manually reconstruct the stack using internal classes for test
        // Or use ServiceFactory but change user.dir? No.

        // Manually create stack:
        Path chunksDir = storageDir.resolve("chunks");
        Files.createDirectories(chunksDir);
        Path indexFile = storageDir.resolve("index.txt");

        com.justsyncit.storage.ChunkIndex chunkIndex = com.justsyncit.storage.FilesystemChunkIndex.create(storageDir,
                indexFile);
        rawStore = com.justsyncit.storage.FilesystemContentStore.create(storageDir, chunkIndex, blake3Service);

        rsService = new ReedSolomonService(metadataService, rawStore, blake3Service, 4, 2);
        healingStore = new HealingContentStore(rawStore, rsService);
        integrityCheckService = new IntegrityCheckService(healingStore, metadataService, rsService);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (healingStore != null)
            healingStore.close();
        if (metadataService != null)
            metadataService.close();
    }

    @Test
    void testEndToEndSelfHealing() throws Exception {
        // 1. Create enough chunks to form a parity group (need 4 data chunks)
        byte[][] dataChunks = new byte[4][];
        String[] hashes = new String[4];

        Random random = new Random();
        for (int i = 0; i < 4; i++) {
            dataChunks[i] = new byte[1024]; // 1KB chunks
            random.nextBytes(dataChunks[i]);
            hashes[i] = healingStore.storeChunk(dataChunks[i]);

            // Also need to register chunks in metadata service (usually done by
            // BackupService)
            // But here we manually insert
            com.justsyncit.storage.metadata.ChunkMetadata meta = new com.justsyncit.storage.metadata.ChunkMetadata(
                    hashes[i], dataChunks[i].length, java.time.Instant.now(), 1, java.time.Instant.now());
            metadataService.upsertChunk(meta);
        }

        // 2. Trigger parity creation
        // Run integrity check to detect missing parity
        IntegrityCheckService.IntegrityCheckResult result = integrityCheckService.runIntegrityCheck();

        assertEquals(4, result.checkedChunks);
        assertEquals(1, result.parityGroupsCreated);
        assertEquals(0, result.failedChunks);

        // Verify parity chunks exist on disk
        // We can't easily know their hashes without querying DB
        // But we assume they are there.

        // 3. Corrupt one data chunk
        String victimHash = hashes[0];
        Path victimPath = ((FilesystemContentStore) rawStore).getChunkPath(victimHash);

        assertTrue(Files.exists(victimPath));
        // Overwrite with garbage
        Files.write(victimPath, new byte[1024]); // Zeros
        System.out.println("Corrupted chunk: " + victimHash);

        // 4. Verify Corruption (using RAW store to avoid auto-repair)
        assertThrows(Exception.class, () -> rawStore.retrieveChunk(victimHash));

        // 5. Run Integrity Check (should detect and REPAIR)
        result = integrityCheckService.runIntegrityCheck();

        // Note: failedChunks might be 0 if repair was successful and transparent?
        // IntegrityCheckService logic:
        // try { contentStore.retrieveChunk(hash); } catch ...
        // HealingContentStore catches StorageIntegrityException, repairs, and returns
        // data.
        // So `retrieveChunk` SUCCEEDS.
        // So failedChunks should be 0!
        // But we should see logs (not asserting logs here).

        assertEquals(6, result.checkedChunks); // It checked all 6 (4 data + 2 parity)
        // If repair works, no exception is thrown to IntegrityCheckService
        assertEquals(0, result.failedChunks, "Should verify successfully after repair");

        // 6. Verify data is correct on disk (or at least retrievable)
        byte[] retrieved = rawStore.retrieveChunk(victimHash);
        assertArrayEquals(dataChunks[0], retrieved, "Restored data should match original");

        System.out.println("Self-healing successful!");
    }
}
