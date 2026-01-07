package com.justsyncit.storage.metadata;

import com.justsyncit.metadata.BlindIndexSearch;
import com.justsyncit.network.encryption.AesGcmEncryptionService;
import com.justsyncit.network.encryption.EncryptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
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
        private Connection keepAliveConnection;

        @BeforeEach
        void setUp() throws IOException, java.sql.SQLException {
                // Setup in-memory database with shared cache so we can inspect it from test
                // Use named memory DB to ensure sharing works across different connection
                // managers
                connectionManager = new SqliteConnectionManager("file:memdb1?mode=memory&cache=shared", 1);
                // Keep one connection open to persistence of shared in-memory DB
                keepAliveConnection = connectionManager.getConnection();

                // SqliteSchemaMigrator.create() ... (removed)

                // Setup encryption
                secretKey = new byte[32]; // 256-bit key
                new java.security.SecureRandom().nextBytes(secretKey);
                Supplier<byte[]> keySupplier = () -> secretKey;

                EncryptionService encryptionService = new AesGcmEncryptionService();
                BlindIndexSearch blindIndexSearch = new BlindIndexSearch(() -> new byte[32]);

                metadataService = new SqliteMetadataService(connectionManager, encryptionService,
                                keySupplier, blindIndexSearch, new com.fasterxml.jackson.databind.ObjectMapper());
        }

        @AfterEach
        void tearDown() throws IOException, java.sql.SQLException {
                if (metadataService != null) {
                        metadataService.close();
                }
                if (keepAliveConnection != null && !keepAliveConnection.isClosed()) {
                        keepAliveConnection.close();
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
                        System.out.println("TEST FAILURE DETAILS: " + e.getMessage());
                        if (e.getCause() != null) {
                                System.out.println("TEST FAILURE CAUSE: " + e.getCause().getMessage());
                                e.getCause().printStackTrace();
                        } else {
                                e.printStackTrace();
                        }
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

                        FileMetadata plainFile;
                        // Use a separate connection manager for legacy service so closing it doesn't
                        // close the main one
                        // but points to same shared memory DB
                        try (DatabaseConnectionManager legacyConnManager = new SqliteConnectionManager(
                                        "file:memdb1?mode=memory&cache=shared", 1);
                                        SqliteMetadataService legacyService = new SqliteMetadataService(
                                                        legacyConnManager, null, null, null,
                                                        new com.fasterxml.jackson.databind.ObjectMapper())) {

                                legacyService.createSnapshot("snap1", "Legacy");

                                plainFile = new FileMetadata(
                                                UUID.randomUUID().toString(),
                                                "snap1",
                                                "/legacy/plain.txt",
                                                0L,
                                                Instant.now(),
                                                "h_old",
                                                Collections.emptyList());
                                legacyService.insertFile(plainFile);
                        }

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

        @Test
        void testDecryptionFailure() throws Exception {
                // Manually insert a file with corrupt path data/invalid Base64
                // to trigger the exception handling in decryptPath
                metadataService.createSnapshot("snap_fail", "Snapshot with corrupt file");

                String corruptPath = "NotValidBase64!!!";
                String fileId = UUID.randomUUID().toString();

                try (Connection conn = connectionManager.getConnection();
                                Statement stmt = conn.createStatement()) {

                        // Direct SQL insert to bypass service validation/encryption logic
                        String sql = String.format(
                                        "INSERT INTO files (id, snapshot_id, path, size, modified_time, file_hash, encryption_mode) "
                                                        +
                                                        "VALUES ('%s', 'snap_fail', '%s', 100, %d, 'hash', 'AES')",
                                        fileId, corruptPath, System.currentTimeMillis());
                        stmt.execute(sql);
                }

                // Verify retrieval catches exception and returns placeholder
                Optional<FileMetadata> retrieved = metadataService.getFile(fileId);
                assertTrue(retrieved.isPresent());
                // Expecting the placeholder defined in FileRepository
                assertEquals("<decryption_failed>", retrieved.get().getPath());
        }
}
