/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.justsyncit.integrity.service;

import com.justsyncit.storage.ChunkStorage;
import com.justsyncit.storage.StorageIntegrityException;
import com.justsyncit.storage.metadata.*;
import com.justsyncit.storage.snapshot.MerkleNode;
import com.justsyncit.storage.snapshot.MerkleTreeDiffer.DiffEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class ReedSolomonServiceTest {

    private StubMetadataService metadataService;
    private StubChunkStorage chunkStorage;
    private StubBlake3Service blake3Service;
    private ReedSolomonService service;

    @BeforeEach
    public void setUp() {
        metadataService = new StubMetadataService();
        chunkStorage = new StubChunkStorage();
        blake3Service = new StubBlake3Service();
        service = new ReedSolomonService(metadataService, chunkStorage, blake3Service, 2, 1);
    }

    @Test
    public void testCreateParityGroup() throws IOException {
        // Prepare data
        byte[] d1 = new byte[10];
        Arrays.fill(d1, (byte) 1);
        byte[] d2 = new byte[10];
        Arrays.fill(d2, (byte) 2);

        String h1 = chunkStorage.storeChunk(d1);
        String h2 = chunkStorage.storeChunk(d2);

        metadataService.upsertChunk(new ChunkMetadata(h1, 10, Instant.now(), 1, Instant.now()));
        metadataService.upsertChunk(new ChunkMetadata(h2, 10, Instant.now(), 1, Instant.now()));

        // Execute
        long groupId = service.createParityGroup(Arrays.asList(h1, h2));

        // Verify
        assertTrue(groupId > 0);
        List<ChunkParityEntry> entries = metadataService.getChunksInParityGroup(groupId);
        assertEquals(3, entries.size(), "Should have 2 data + 1 parity");

        // Check parity chunk exists in storage
        String parityHash = entries.get(2).getChunkHash();
        assertTrue(chunkStorage.existsChunk(parityHash), "Parity chunk should be stored");
        assertTrue(entries.get(2).isParity());
    }

    @Test
    public void testRepairChunk() throws IOException {
        // Setup
        byte[] d1 = new byte[10];
        Arrays.fill(d1, (byte) 1);
        byte[] d2 = new byte[10];
        Arrays.fill(d2, (byte) 2);
        String h1 = chunkStorage.storeChunk(d1);
        String h2 = chunkStorage.storeChunk(d2);

        metadataService.upsertChunk(new ChunkMetadata(h1, 10, Instant.now(), 1, Instant.now()));
        metadataService.upsertChunk(new ChunkMetadata(h2, 10, Instant.now(), 1, Instant.now()));

        service.createParityGroup(Arrays.asList(h1, h2));

        // Simulate loss of h1
        chunkStorage.deleteChunk(h1);
        assertFalse(chunkStorage.existsChunk(h1));

        // Repair
        boolean success = service.repairChunk(h1);
        assertTrue(success, "Repair should succeed");

        // Verify data restored
        assertTrue(chunkStorage.existsChunk(h1));
        assertArrayEquals(d1, chunkStorage.getData(h1));
    }

    // --- Stubs ---

    static class StubChunkStorage implements ChunkStorage {
        final Map<String, byte[]> store = new HashMap<>();

        @Override
        public String storeChunk(byte[] data) {
            String hash = "hash-" + Arrays.hashCode(data); // Simple hash for test
            store.put(hash, data);
            return hash;
        }

        public byte[] getData(String hash) {
            return store.get(hash);
        }

        public void deleteChunk(String hash) {
            store.remove(hash);
        }

        @Override
        public byte[] retrieveChunk(String hash) throws IOException, StorageIntegrityException {
            if (!store.containsKey(hash))
                return null;
            return store.get(hash);
        }

        @Override
        public boolean existsChunk(String hash) {
            return store.containsKey(hash);
        }

    }

    static class StubBlake3Service implements com.justsyncit.hash.Blake3Service {
        @Override
        public String hashFile(java.nio.file.Path filePath) {
            return "mock";
        }

        @Override
        public String hashBuffer(byte[] data) {
            return "hash-" + Arrays.hashCode(data);
        }

        @Override
        public String hashBuffer(byte[] data, int offset, int length) {
            return "mock";
        }

        @Override
        public String hashBuffer(java.nio.ByteBuffer buffer) {
            return "mock";
        }

        @Override
        public String hashStream(java.io.InputStream inputStream) {
            return "mock";
        }

        @Override
        public Blake3IncrementalHasher createIncrementalHasher() {
            return null;
        }

        @Override
        public Blake3IncrementalHasher createKeyedIncrementalHasher(byte[] key) {
            return null;
        }

        @Override
        public java.util.concurrent.CompletableFuture<List<String>> hashFilesParallel(
                List<java.nio.file.Path> filePaths) {
            return null;
        }

        @Override
        public Blake3Info getInfo() {
            return null;
        }

        @Override
        public boolean verify(byte[] data, String expectedHash) {
            return expectedHash.equals(hashBuffer(data));
        }
    }

    static class StubMetadataService implements MetadataService {
        long nextGroupId = 1;
        final Map<Long, ParityGroupMetadata> groups = new HashMap<>();
        final Map<Long, List<ChunkParityEntry>> groupEntries = new HashMap<>();
        final Map<String, ChunkMetadata> chunks = new HashMap<>();
        final Map<String, ChunkParityEntry> chunkToEntry = new HashMap<>();

        @Override
        public void close() {
        }

        @Override
        public Transaction beginTransaction() {
            return null;
        }

        @Override
        public Optional<ChunkMetadata> getChunkMetadata(String hash) {
            return Optional.ofNullable(chunks.get(hash));
        }

        @Override
        public void upsertChunk(ChunkMetadata chunk) {
            chunks.put(chunk.getHash(), chunk);
        }

        @Override
        public long createParityGroup(String algorithm) {
            long id = nextGroupId++;
            groups.put(id, new ParityGroupMetadata(id, algorithm, Instant.now()));
            return id;
        }

        @Override
        public void addChunkToParityGroup(long groupId, String chunkHash, int index, boolean isParity) {
            ChunkParityEntry entry = new ChunkParityEntry(groupId, chunkHash, index, isParity);
            groupEntries.computeIfAbsent(groupId, k -> new ArrayList<>()).add(entry);
            chunkToEntry.put(chunkHash, entry);
        }

        @Override
        public List<ChunkParityEntry> getChunksInParityGroup(long groupId) {
            return groupEntries.getOrDefault(groupId, Collections.emptyList());
        }

        @Override
        public Optional<ChunkParityEntry> getChunkParityEntry(String chunkHash) {
            return Optional.ofNullable(chunkToEntry.get(chunkHash));
        }

        @Override
        public Optional<ParityGroupMetadata> getParityGroup(long groupId) {
            return Optional.ofNullable(groups.get(groupId));
        }

        // Unused methods
        @Override
        public List<FileMetadata> searchFiles(String q) {
            return null;
        }

        @Override
        public Snapshot createSnapshot(String n, String d) {
            return null;
        }

        @Override
        public Optional<Snapshot> getSnapshot(String id) {
            return Optional.empty();
        }

        @Override
        public void updateSnapshot(Snapshot s) {
        }

        @Override
        public List<Snapshot> listSnapshots() {
            return null;
        }

        @Override
        public void deleteSnapshot(String id) {
        }

        @Override
        public String insertFile(FileMetadata f) {
            return null;
        }

        @Override
        public List<String> insertFiles(List<FileMetadata> f) {
            return null;
        }

        @Override
        public Optional<FileMetadata> getFile(String id) {
            return Optional.empty();
        }

        @Override
        public List<FileMetadata> getFilesInSnapshot(String id) {
            return null;
        }

        @Override
        public void updateFile(FileMetadata f) {
        }

        @Override
        public void deleteFile(String id) {
        }

        @Override
        public java.util.stream.Stream<ChunkMetadata> streamAllChunks() {
            return chunks.values().stream();
        }

        @Override
        public void recordChunkAccess(String h) {
        }

        @Override
        public boolean deleteChunk(String h) {
            return false;
        }

        @Override
        public MetadataStats getStats() {
            return null;
        }

        @Override
        public void upsertMerkleNode(MerkleNode n) {
        }

        @Override
        public Optional<MerkleNode> getMerkleNode(String h) {
            return Optional.empty();
        }

        @Override
        public void setSnapshotRoot(String s, String r) {
        }

        @Override
        public Optional<String> getSnapshotRoot(String s) {
            return Optional.empty();
        }

        @Override
        public void copyUnchangedFiles(String s, String t, List<String> c) {
        }

        @Override
        public List<DiffEntry> compareSnapshots(String s1, String s2) {
            return null;
        }

        @Override
        public boolean validateSnapshotChain(String s) {
            return false;
        }
    }
}
