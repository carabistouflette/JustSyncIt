package com.justsyncit.storage;

import com.justsyncit.hash.Blake3Service;
import com.justsyncit.metadata.BlindIndexSearch;
import com.justsyncit.network.encryption.AesGcmEncryptionService;
import com.justsyncit.network.encryption.EncryptionService;
import com.justsyncit.storage.metadata.FileMetadata;
import com.justsyncit.storage.metadata.MetadataService;
import com.justsyncit.storage.metadata.SqliteConnectionManager;
import com.justsyncit.storage.metadata.SqliteMetadataService;
import com.justsyncit.storage.metadata.SqliteSchemaMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class EncryptedBackupE2ETest {

    @TempDir
    Path tempDir;

    private SqliteConnectionManager connectionManager;
    private MetadataService metadataService;
    private ContentStore contentStore;
    private com.justsyncit.storage.FilesystemChunkIndex chunkIndex;
    private EncryptionService encryptionService;
    private byte[] masterKey;

    @BeforeEach
    void setUp() throws Exception {
        // Setup encryption
        masterKey = new byte[32];
        // simple key
        for (int i = 0; i < 32; i++)
            masterKey[i] = (byte) i;
        Supplier<byte[]> keySupplier = () -> masterKey;
        encryptionService = new AesGcmEncryptionService();

        // Use ServiceFactory to create complex dependencies
        com.justsyncit.ServiceFactory factory = new com.justsyncit.ServiceFactory();
        Blake3Service blake3Service = factory.createBlake3Service();

        // Setup Metadata Service (Encrypted)
        connectionManager = new SqliteConnectionManager("file::memory:?cache=shared", 1);
        BlindIndexSearch blindIndexSearch = new BlindIndexSearch(keySupplier);
        metadataService = new SqliteMetadataService(
                connectionManager,
                SqliteSchemaMigrator.create(),
                encryptionService,
                blindIndexSearch,
                keySupplier);

        // Setup Content Store (Encrypted wrapper around File System)
        Path storagePath = tempDir.resolve("storage/chunks");
        Path indexPath = tempDir.resolve("storage/index.txt");
        Files.createDirectories(storagePath);

        chunkIndex = com.justsyncit.storage.FilesystemChunkIndex
                .create(storagePath, indexPath);
        ContentStore fsStore = FilesystemContentStore.create(storagePath, chunkIndex, blake3Service);

        contentStore = new EncryptedContentStore(fsStore, encryptionService, keySupplier, blake3Service);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (contentStore != null)
            contentStore.close();
        if (metadataService != null)
            ((SqliteMetadataService) metadataService).close();
    }

    @Test
    void testEndToEndEncryption() throws Exception {
        // 1. Prepare data
        String content = "This is a secret message that should be encrypted.";
        byte[] data = content.getBytes(StandardCharsets.UTF_8);

        // Use ServiceFactory to get Blake3Service for hashing
        com.justsyncit.ServiceFactory factory = new com.justsyncit.ServiceFactory();
        Blake3Service blake3Service = factory.createBlake3Service();
        String fileHash = blake3Service.hashBuffer(data); // Hash of PLAINTEXT

        // 2. Store chunk (Simulate BackupService)
        String chunkHash = contentStore.storeChunk(data);

        assertNotEquals(fileHash, chunkHash, "Stored chunk hash should differ from plaintext hash due to encryption");

        // 3. Create metadata
        metadataService.createSnapshot("snap1", "Backup 1");
        FileMetadata fileMeta = new FileMetadata(
                UUID.randomUUID().toString(),
                "snap1",
                "/secure/doc.txt",
                (long) data.length,
                Instant.now(),
                fileHash,
                Collections.singletonList(chunkHash));
        metadataService.insertFile(fileMeta);

        // 4. Verification: Check file on disk
        // FSStore stores at storagePath/xx/xx/hash
        Path chunkPath = getChunkPath(chunkHash);
        assertTrue(Files.exists(chunkPath), "Chunk file should exist on disk: " + chunkPath);

        String diskContent = Files.readString(chunkPath, StandardCharsets.ISO_8859_1);
        assertFalse(diskContent.contains("secret message"), "Disk content should NOT contain plaintext");
        assertFalse(diskContent.contains("encrypted"), "Disk content should NOT contain plaintext");

        // 5. Restore
        // Retrieve chunk using the chunkHash (which is Hash(Blob))
        byte[] restoredData = contentStore.retrieveChunk(chunkHash);
        assertNotNull(restoredData, "Restored data should not be null");
        String restoredString = new String(restoredData, StandardCharsets.UTF_8);

        assertEquals(content, restoredString, "Restored content should match original");

        // 6. Verify Search
        List<FileMetadata> searchResults = metadataService.searchFiles("doc");
        assertEquals(1, searchResults.size());
        assertEquals("/secure/doc.txt", searchResults.get(0).getPath());
    }

    @Test
    void testDeduplicationOfEncryptedChunks() throws IOException {
        String content = "Deduplicate me!";
        byte[] data = content.getBytes(StandardCharsets.UTF_8);

        // Store first time
        String hash1 = contentStore.storeChunk(data);

        // Store second time
        String hash2 = contentStore.storeChunk(data);

        // Hashes should be identical because of Deterministic Encryption
        assertEquals(hash1, hash2, "Deterministic encryption should produce same ciphertext/hash for same plaintext");

        // Check only one file on disk
        Path chunkPath = getChunkPath(hash1);
        assertTrue(Files.exists(chunkPath));

        // Verify stats
        assertEquals(1, contentStore.getChunkCount());
    }

    private Path getChunkPath(String hash) throws IOException {
        return chunkIndex.getChunkPath(hash);
    }
}
