package com.justsyncit.storage.metadata;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class IntegrityMetadataTest {

    @TempDir
    Path tempDir;

    private SqliteMetadataService metadataService;
    private DatabaseConnectionManager connectionManager;

    @BeforeEach
    public void setUp() throws Exception {
        Path dbPath = tempDir.resolve("test-metadata.db");
        connectionManager = new SqliteConnectionManager(dbPath.toString(), 10);
        metadataService = new SqliteMetadataService(connectionManager, SqliteSchemaMigrator.create());

        // We need chunks to refer to foreign keys
        // So let's insert some dummy chunks
        ChunkMetadata c1 = new ChunkMetadata("hash1", 100, Instant.now(), 1, Instant.now());
        ChunkMetadata c2 = new ChunkMetadata("hash2", 100, Instant.now(), 1, Instant.now());
        ChunkMetadata c3 = new ChunkMetadata("hash3-parity", 100, Instant.now(), 1, Instant.now());

        metadataService.upsertChunk(c1);
        metadataService.upsertChunk(c2);
        metadataService.upsertChunk(c3);
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (metadataService != null) {
            metadataService.close();
        }
    }

    @Test
    public void testParityGroupLifecycle() throws IOException {
        // 1. Create Parity Group
        long groupId = metadataService.createParityGroup("RS-2-1");
        assertTrue(groupId > 0, "Group ID should be positive");

        // 2. Verify Group Exists
        Optional<ParityGroupMetadata> groupOpt = metadataService.getParityGroup(groupId);
        assertTrue(groupOpt.isPresent(), "Group should exist");
        assertEquals("RS-2-1", groupOpt.get().getAlgorithm());

        // 3. Add chunks to group
        metadataService.addChunkToParityGroup(groupId, "hash1", 0, false);
        metadataService.addChunkToParityGroup(groupId, "hash2", 1, false);
        metadataService.addChunkToParityGroup(groupId, "hash3-parity", 2, true);

        // 4. Verify chunks in group
        List<ChunkParityEntry> entries = metadataService.getChunksInParityGroup(groupId);
        assertEquals(3, entries.size(), "Should have 3 chunks in group");

        assertEquals("hash1", entries.get(0).getChunkHash());
        assertEquals(0, entries.get(0).getChunkIndex());
        assertFalse(entries.get(0).isParity());

        assertEquals("hash2", entries.get(1).getChunkHash());
        assertEquals(1, entries.get(1).getChunkIndex());
        assertFalse(entries.get(1).isParity());

        assertEquals("hash3-parity", entries.get(2).getChunkHash());
        assertEquals(2, entries.get(2).getChunkIndex());
        assertTrue(entries.get(2).isParity());
    }
}
