/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 */

package com.justsyncit.storage.metadata;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for FTS5 full-text search.
 */
@DisplayName("FTS Search Tests")
class FTSMetadataServiceTest {

    private MetadataService metadataService;
    private DatabaseConnectionManager connectionManager;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("fts-test");
        connectionManager = new SqliteConnectionManager(
                tempDir.resolve("test.db").toString(), 5);
        SchemaMigrator schemaMigrator = SqliteSchemaMigrator.create();
        try {
            metadataService = new SqliteMetadataService(connectionManager, schemaMigrator);
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        if (metadataService != null) {
            metadataService.close();
        }
        // Clean up temp directory... auto-handled by OS usually but good practice to
        // clean
        // omitting for brevity in this snippet as previous tests handle it
    }

    @Test
    @DisplayName("Should search files by partial path")
    void shouldSearchFilesByPartialPath() throws IOException {
        // Given
        Snapshot snapshot = metadataService.createSnapshot("fts-snap", "desc");
        createDummyFile(snapshot.getId(), "/home/user/documents/report.pdf");
        createDummyFile(snapshot.getId(), "/home/user/pictures/vacation.jpg");
        createDummyFile(snapshot.getId(), "/var/log/syslog");

        // When
        List<FileMetadata> results = metadataService.searchFiles("documents");

        // Then
        assertEquals(1, results.size());
        assertEquals("/home/user/documents/report.pdf", results.get(0).getPath());
    }

    @Test
    @DisplayName("Should update search index on file insert")
    void shouldUpdateIndexOnFileInsert() throws IOException {
        // Given
        Snapshot snapshot = metadataService.createSnapshot("fts-snap-2", "desc");

        // When
        createDummyFile(snapshot.getId(), "/new/file/test.txt");

        // Then
        List<FileMetadata> results = metadataService.searchFiles("test");
        assertEquals(1, results.size());
    }

    @Test
    @DisplayName("Should update search index on file delete")
    void shouldUpdateIndexOnFileDelete() throws IOException {
        // Given
        Snapshot snapshot = metadataService.createSnapshot("fts-snap-3", "desc");
        FileMetadata file = createDummyFile(snapshot.getId(), "/delete/me.txt");

        // When
        metadataService.deleteFile(file.getId());

        // Then
        List<FileMetadata> results = metadataService.searchFiles("delete");
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Should support boolean operators implicitly")
    void shouldSupportBooleanOperators() throws IOException {
        // Given
        Snapshot snapshot = metadataService.createSnapshot("fts-snap-4", "desc");
        createDummyFile(snapshot.getId(), "/project/java/src/Main.java");

        // When
        // In FTS5, space is implicit AND
        List<FileMetadata> results = metadataService.searchFiles("project Main");

        // Then
        assertEquals(1, results.size());
    }

    @Test
    @DisplayName("Should support explicit boolean operators")
    void shouldSupportExplicitBooleanOperators() throws IOException {
        // Given
        Snapshot snapshot = metadataService.createSnapshot("fts-snap-5", "desc");
        createDummyFile(snapshot.getId(), "/docs/report.pdf");
        createDummyFile(snapshot.getId(), "/docs/image.jpg");
        createDummyFile(snapshot.getId(), "/docs/readme.txt");

        // When: OR
        List<FileMetadata> resultsOr = metadataService.searchFiles("report OR image");
        assertEquals(2, resultsOr.size());

        // When: NOT
        // Note: FTS5 standard syntax is "term1 NOT term2"
        List<FileMetadata> resultsNot = metadataService.searchFiles("docs NOT pdf");
        // Should find image.jpg and readme.txt
        assertEquals(2, resultsNot.size());
        assertTrue(resultsNot.stream().noneMatch(f -> f.getPath().endsWith(".pdf")));
    }

    private FileMetadata createDummyFile(String snapshotId, String path) throws IOException {
        // We need to create chunks first due to FK constraints
        ChunkMetadata chunk = new ChunkMetadata("hash-" + path.hashCode(), 100, Instant.now(), 1, Instant.now());
        metadataService.upsertChunk(chunk);

        FileMetadata file = new FileMetadata(
                "id-" + path.hashCode(),
                snapshotId,
                path,
                100,
                Instant.now(),
                "file-hash",
                Arrays.asList(chunk.getHash()));
        metadataService.insertFile(file);
        return file;
    }
}
