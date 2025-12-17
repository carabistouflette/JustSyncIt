package com.justsyncit.storage.metadata;

import com.justsyncit.metadata.BlindIndexSearch;
import com.justsyncit.network.encryption.AesGcmEncryptionService;
import com.justsyncit.network.encryption.EncryptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class EncryptedMetadataTest {

        private SqliteMetadataService metadataService;
        private DatabaseConnectionManager connectionManager;
        private byte[] secretKey;

        @BeforeEach
        void setUp() throws IOException {
                // Setup in-memory database with shared cache so we can inspect it from test
                connectionManager = new SqliteConnectionManager("file::memory:?cache=shared", 1);
                SchemaMigrator schemaMigrator = SqliteSchemaMigrator.create();

                // Setup encryption
                secretKey = new byte[32]; // 256-bit key
                new java.security.SecureRandom().nextBytes(secretKey);
                Supplier<byte[]> keySupplier = () -> secretKey;

                EncryptionService encryptionService = new AesGcmEncryptionService();
                BlindIndexSearch blindIndexSearch = new BlindIndexSearch(keySupplier);

                metadataService = new SqliteMetadataService(
                                connectionManager,
                                schemaMigrator,
                                encryptionService,
                                blindIndexSearch,
                                keySupplier);
        }

        @AfterEach
        void tearDown() throws IOException {
                if (metadataService != null) {
                        metadataService.close();
                }
        }

        @Test
        void testEncryptedFileInsertionAndRetrieval() throws Exception {
                try {
                        // Create snapshot
                        metadataService.createSnapshot("snap1", "Test Snapshot");

                        // Create file metadata
                        String originalPath = "/home/user/documents/secret.pdf";
                        FileMetadata file = new FileMetadata(
                                        UUID.randomUUID().toString(),
                                        "snap1",
                                        originalPath,
                                        1024L,
                                        Instant.now(),
                                        "hash123",
                                        Collections.singletonList("chunk1"));

                        // Insert file
                        metadataService.insertFile(file);

                        // 1. Verify storage is encrypted by querying DB directly
                        try (Connection conn = connectionManager.getConnection();
                                        Statement stmt = conn.createStatement()) {

                                ResultSet rs = stmt
                                                .executeQuery("SELECT path, encryption_mode FROM files WHERE id = '"
                                                                + file.getId() + "'");
                                assertTrue(rs.next());
                                String storedPath = rs.getString("path");
                                String encryptionMode = rs.getString("encryption_mode");

                                assertEquals("AES", encryptionMode);
                                assertNotEquals(originalPath, storedPath);
                                assertFalse(storedPath.contains("secret")); // Should look randomized/base64

                                // Verify blind index keywords
                                ResultSet rsKeywords = stmt
                                                .executeQuery("SELECT * FROM file_keywords WHERE file_id = '"
                                                                + file.getId() + "'");
                                assertTrue(rsKeywords.next(), "Keywords should be indexed");
                        }

                        // 2. Verify retrieval decrypts the path
                        Optional<FileMetadata> retrieved = metadataService.getFile(file.getId());
                        assertTrue(retrieved.isPresent());
                        assertEquals(originalPath, retrieved.get().getPath());

                        // 3. Verify list snapshot files decrypts path
                        List<FileMetadata> files = metadataService.getFilesInSnapshot("snap1");
                        assertEquals(1, files.size());
                        assertEquals(originalPath, files.get(0).getPath());
                } catch (Exception e) {
                        e.printStackTrace();
                        throw e;
                }
        }

        @Test
        void testBlindIndexSearch() throws Exception {
                try {
                        // Create snapshot
                        metadataService.createSnapshot("snap1", "Test Snapshot");

                        // Insert files
                        String path1 = "/home/user/documents/report_2023.pdf";
                        String path2 = "/home/user/photos/vacation.jpg";

                        FileMetadata file1 = new FileMetadata(UUID.randomUUID().toString(), "snap1", path1, 0L,
                                        Instant.now(), "h1", Collections.emptyList());
                        FileMetadata file2 = new FileMetadata(UUID.randomUUID().toString(), "snap1", path2, 0L,
                                        Instant.now(), "h2", Collections.emptyList());

                        metadataService.insertFile(file1);
                        metadataService.insertFile(file2);

                        // Search for "report"
                        List<FileMetadata> results1 = metadataService.searchFiles("report");
                        assertEquals(1, results1.size());
                        assertEquals(path1, results1.get(0).getPath());

                        // Search for "vacation"
                        List<FileMetadata> results2 = metadataService.searchFiles("vacation");
                        assertEquals(1, results2.size());
                        assertEquals(path2, results2.get(0).getPath());

                        // Search for "photos" (part of path)
                        List<FileMetadata> results3 = metadataService.searchFiles("photos");
                        assertEquals(1, results3.size());
                        assertEquals(path2, results3.get(0).getPath());

                        // Search for non-existent
                        List<FileMetadata> results4 = metadataService.searchFiles("missing");
                        assertTrue(results4.isEmpty());
                } catch (Exception e) {
                        e.printStackTrace();
                        throw e;
                }
        }

        @Test
        void testMixedEncryptionModes() throws Exception {
                try {
                        // This test simulates a migration scenario where some files are plain text

                        // Allow creating a service WITHOUT encryption to simulate legacy insert
                        SqliteMetadataService legacyService = new SqliteMetadataService(
                                        connectionManager,
                                        SqliteSchemaMigrator.create());

                        legacyService.createSnapshot("snap1", "Legacy");

                        FileMetadata plainFile = new FileMetadata(
                                        UUID.randomUUID().toString(),
                                        "snap1",
                                        "/legacy/plain.txt",
                                        0L,
                                        Instant.now(),
                                        "h_old",
                                        Collections.emptyList());
                        legacyService.insertFile(plainFile);

                        // Now switch to encrypted service

                        // Insert encrypted file
                        FileMetadata encFile = new FileMetadata(
                                        UUID.randomUUID().toString(),
                                        "snap1",
                                        "/secure/data.txt",
                                        0L,
                                        Instant.now(),
                                        "h_new",
                                        Collections.emptyList());
                        metadataService.insertFile(encFile);

                        // Verify we can retrieve BOTH correctly
                        Optional<FileMetadata> retPlain = metadataService.getFile(plainFile.getId());
                        assertTrue(retPlain.isPresent());
                        assertEquals("/legacy/plain.txt", retPlain.get().getPath()); // Should remain plain

                        Optional<FileMetadata> retEnc = metadataService.getFile(encFile.getId());
                        assertTrue(retEnc.isPresent());
                        assertEquals("/secure/data.txt", retEnc.get().getPath()); // Should be decrypted
                } catch (Exception e) {
                        e.printStackTrace();
                        throw e;
                }
        }
}
