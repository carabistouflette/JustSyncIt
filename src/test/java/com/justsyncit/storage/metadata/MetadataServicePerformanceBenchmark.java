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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.justsyncit.storage.metadata;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * JMH performance benchmarks for the SQLite metadata service.
 * Tests performance of CRUD operations with various data sizes.
 */
@BenchmarkMode(org.openjdk.jmh.annotations.Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class MetadataServicePerformanceBenchmark {

    /** Metadata service instance for benchmarking. */
    private MetadataService metadataService;
    /** Connection manager for the metadata service. */
    private DatabaseConnectionManager connectionManager;
    /** Temporary directory for test database. */
    private Path tempDir;
    /** Test data for snapshots. */
    private List<Snapshot> testSnapshots;
    /** Test data for files. */
    private List<FileMetadata> testFiles;
    /** Test data for chunks. */
    private List<ChunkMetadata> testChunks;

    /**
     * Set up the benchmark environment.
     * Creates temporary database and populates test data.
     */
    @Setup(Level.Trial)
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("metadata-benchmark");
        String dbPath = tempDir.resolve("benchmark.db").toString();
        connectionManager = new SqliteConnectionManager(dbPath, 10);
        SchemaMigrator migrator = SqliteSchemaMigrator.create();
        try (Connection connection = connectionManager.getConnection()) {
            migrator.createInitialSchema(connection);
        }
        metadataService = new SqliteMetadataService(connectionManager, migrator);
        // Create test data
        createTestData();
    }

    /**
     * Clean up the benchmark environment.
     * Closes connections and removes temporary files.
     */
    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
        if (metadataService != null) {
            metadataService.close();
        }
        if (connectionManager != null && !connectionManager.isClosed()) {
            connectionManager.close();
        }
        // Clean up temp directory
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (Exception e) {
                            // Ignore cleanup errors
                        }
                    });
        }
    }

    /**
     * Creates test data for benchmarks.
     * Generates snapshots, files, and chunks with various sizes.
     */
    private void createTestData() {
        // Create test snapshots
        testSnapshots = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            testSnapshots.add(new Snapshot(
                    UUID.randomUUID().toString(),
                    "snapshot-" + i,
                    "Test snapshot " + i,
                    Instant.now().minusSeconds(i * 1000),
                    i % 10,
                    1024 * (i % 100 + 1)
            ));
        }
        // Create test files
        testFiles = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            List<String> chunkHashes = new ArrayList<>();
            for (int j = 0; j < (i % 5 + 1); j++) {
                chunkHashes.add("chunk-hash-" + (i * 10 + j));
            }
            testFiles.add(new FileMetadata(
                    UUID.randomUUID().toString(),
                    "snapshot-" + (i % 100),
                    "/path/to/file-" + i + ".txt",
                    1024 * (i % 100 + 1),
                    Instant.now().minusSeconds(i * 100),
                    "hash-" + i,
                    chunkHashes
            ));
        }
        // Create test chunks
        testChunks = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            testChunks.add(new ChunkMetadata(
                    "chunk-hash-" + i,
                    4096 * (i % 10 + 1),
                    Instant.now().minusSeconds(i * 50),
                    i % 100 + 1,
                    Instant.now().minusSeconds(i * 25)
            ));
        }
    }

    /**
     * Benchmark creating a snapshot.
     * Tests the performance of snapshot creation.
     */
    @Benchmark
    public void createSnapshot(Blackhole bh) throws Exception {
        Snapshot snapshot = testSnapshots.get(
                (int) (Math.random() * testSnapshots.size())
        );
        try {
            metadataService.createSnapshot(snapshot.getName(), snapshot.getDescription());
            bh.consume(true);
        } catch (Exception e) {
            bh.consume(false);
        }
    }

    /**
     * Benchmark retrieving a snapshot.
     * Tests the performance of snapshot lookup by name.
     */
    @Benchmark
    public void getSnapshot(Blackhole bh) throws Exception {
        String snapshotId = testSnapshots.get(
                (int) (Math.random() * testSnapshots.size())
        ).getId();
        Optional<Snapshot> result = metadataService.getSnapshot(snapshotId);
        bh.consume(result.orElse(null));
    }

    /**
     * Benchmark listing all snapshots.
     * Tests the performance of snapshot enumeration.
     */
    @Benchmark
    public void listSnapshots(Blackhole bh) throws Exception {
        List<Snapshot> result = metadataService.listSnapshots();
        bh.consume(result);
    }

    /**
     * Benchmark inserting a file.
     * Tests the performance of file metadata insertion.
     */
    @Benchmark
    public void insertFile(Blackhole bh) throws Exception {
        FileMetadata file = testFiles.get(
                (int) (Math.random() * testFiles.size())
        );
        try {
            metadataService.insertFile(file);
            bh.consume(true);
        } catch (Exception e) {
            bh.consume(false);
        }
    }

    /**
     * Benchmark retrieving a file.
     * Tests the performance of file lookup by ID.
     */
    @Benchmark
    public void getFile(Blackhole bh) throws Exception {
        String fileId = testFiles.get(
                (int) (Math.random() * testFiles.size())
        ).getId();
        Optional<FileMetadata> result = metadataService.getFile(fileId);
        bh.consume(result.orElse(null));
    }

    /**
     * Benchmark listing files in a snapshot.
     * Tests the performance of file enumeration by snapshot.
     */
    @Benchmark
    public void listFilesInSnapshot(Blackhole bh) throws Exception {
        String snapshotId = testSnapshots.get(
                (int) (Math.random() * testSnapshots.size())
        ).getId();
        List<FileMetadata> result = metadataService.getFilesInSnapshot(snapshotId);
        bh.consume(result);
    }

    /**
     * Benchmark upserting a chunk.
     * Tests the performance of chunk metadata insertion/update.
     */
    @Benchmark
    public void upsertChunk(Blackhole bh) throws Exception {
        ChunkMetadata chunk = testChunks.get(
                (int) (Math.random() * testChunks.size())
        );
        metadataService.upsertChunk(chunk);
        bh.consume(chunk);
    }

    /**
     * Benchmark recording chunk access.
     * Tests the performance of chunk access tracking.
     */
    @Benchmark
    public void recordChunkAccess(Blackhole bh) throws Exception {
        String chunkHash = testChunks.get(
                (int) (Math.random() * testChunks.size())
        ).getHash();
        metadataService.recordChunkAccess(chunkHash);
        bh.consume(chunkHash);
    }

    /**
     * Benchmark getting metadata statistics.
     * Tests the performance of statistics aggregation.
     */
    @Benchmark
    public void getMetadataStatistics(Blackhole bh) throws Exception {
        MetadataStats result = metadataService.getStats();
        bh.consume(result);
    }

    /**
     * Benchmark transaction operations.
     * Tests the performance of batch operations within a transaction.
     */
    @Benchmark
    public void transactionBatchInsert(Blackhole bh) throws Exception {
        Transaction transaction = metadataService.beginTransaction();
        try {
            // Insert 10 files in a single transaction
            for (int i = 0; i < 10; i++) {
                FileMetadata file = testFiles.get(
                        (int) (Math.random() * testFiles.size())
                );
                metadataService.insertFile(file);
            }
            transaction.commit();
            bh.consume(true);
        } catch (Exception e) {
            try {
                transaction.rollback();
            } catch (Exception rollbackException) {
                // Ignore rollback errors
            }
            bh.consume(false);
        } finally {
            try {
                transaction.close();
            } catch (Exception closeException) {
                // Ignore close errors
            }
        }
    }
}