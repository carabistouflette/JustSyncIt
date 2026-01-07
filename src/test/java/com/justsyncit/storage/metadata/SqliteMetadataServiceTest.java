package com.justsyncit.storage.metadata;

import com.justsyncit.metadata.BlindIndexSearch;
import com.justsyncit.network.encryption.AesGcmEncryptionService;
import com.justsyncit.network.encryption.EncryptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class SqliteMetadataServiceTest {

    private SqliteMetadataService metadataService;
    private DatabaseConnectionManager connectionManager;
    private byte[] secretKey;

    @BeforeEach
    void setUp() throws IOException {
        connectionManager = new SqliteConnectionManager("file::memory:?cache=shared", 1);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (metadataService != null) {
            metadataService.close();
        }
    }

    private void setupService(boolean useEncryption) throws IOException {
        // SchemaMigrator is handled internally now
        if (useEncryption) {
            secretKey = new byte[32];
            new java.security.SecureRandom().nextBytes(secretKey);
            Supplier<byte[]> keySupplier = () -> secretKey;
            EncryptionService encryptionService = new AesGcmEncryptionService();
            BlindIndexSearch blindIndexSearch = new BlindIndexSearch(keySupplier);
            metadataService = new SqliteMetadataService(connectionManager, encryptionService,
                    keySupplier, blindIndexSearch, new com.fasterxml.jackson.databind.ObjectMapper());
        } else {
            metadataService = new SqliteMetadataService(connectionManager, null, null, null,
                    new com.fasterxml.jackson.databind.ObjectMapper());
        }
    }

    private void insertFiles(String snapshotId, int count, String prefix) throws IOException {
        for (int i = 0; i < count; i++) {
            // zero-padded index for alphabetical sorting (works for unencrypted)
            String path = String.format("%sfile_%03d.txt", prefix, i);
            FileMetadata file = new FileMetadata(
                    UUID.randomUUID().toString(),
                    snapshotId,
                    path,
                    0L,
                    Instant.now(),
                    "hash_" + i,
                    Collections.emptyList());
            metadataService.insertFile(file);
        }
    }

    @Test
    void testUnencryptedPagination() throws IOException {
        setupService(false);
        metadataService.createSnapshot("snap1", "Test");

        insertFiles("snap1", 20, "folder/");
        insertFiles("snap1", 5, "other/");

        // Test Count
        assertEquals(25, metadataService.countFilesInSnapshot("snap1", null));
        assertEquals(20, metadataService.countFilesInSnapshot("snap1", "folder/"));

        // Test Pagination Page 1
        List<FileMetadata> page1 = metadataService.getFilesInSnapshot("snap1", "folder/", 10, 0);
        assertEquals(10, page1.size());
        // Since unencrypted, SQL ORDER BY path ASC should match alphabetical
        assertEquals("folder/file_000.txt", page1.get(0).getPath());
        assertEquals("folder/file_009.txt", page1.get(9).getPath());

        // Test Pagination Page 2
        List<FileMetadata> page2 = metadataService.getFilesInSnapshot("snap1", "folder/", 10, 10);
        assertEquals(10, page2.size());
        assertEquals("folder/file_010.txt", page2.get(0).getPath());
        assertEquals("folder/file_019.txt", page2.get(9).getPath());

        // Test Pagination Out of Bounds
        List<FileMetadata> page3 = metadataService.getFilesInSnapshot("snap1", "folder/", 10, 20);
        assertEquals(0, page3.size());
    }

    @Test
    void testEncryptedPagination() throws IOException {
        setupService(true);
        metadataService.createSnapshot("snap2", "Encrypted Test");

        insertFiles("snap2", 20, "secret/");

        // Test Count
        assertEquals(20, metadataService.countFilesInSnapshot("snap2", "secret/"));
        assertEquals(0, metadataService.countFilesInSnapshot("snap2", "missing/"));

        // Test Pagination Page 1
        List<FileMetadata> page1 = metadataService.getFilesInSnapshot("snap2", "secret/", 10, 0);
        assertEquals(10, page1.size());

        // Test Pagination Page 2
        List<FileMetadata> page2 = metadataService.getFilesInSnapshot("snap2", "secret/", 10, 10);
        assertEquals(10, page2.size());

        // Verify disjoint sets
        for (FileMetadata f1 : page1) {
            assertTrue(f1.getPath().startsWith("secret/"));
            for (FileMetadata f2 : page2) {
                assertNotEquals(f1.getId(), f2.getId());
            }
        }
    }
}
