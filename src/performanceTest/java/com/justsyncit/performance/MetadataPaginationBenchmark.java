package com.justsyncit.performance;

import com.justsyncit.ServiceFactory;
import com.justsyncit.network.encryption.AesGcmEncryptionService;
import com.justsyncit.network.encryption.EncryptionService;
import com.justsyncit.performance.util.PerformanceMetrics;
import com.justsyncit.storage.metadata.FileMetadata;
import com.justsyncit.storage.metadata.MetadataService;
import com.justsyncit.storage.metadata.Snapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Benchmark for testing MetadataService pagination performance,
 * specifically optimizing streaming/skipping with encryption.
 */
@Tag("performance")
public class MetadataPaginationBenchmark {

    @TempDir
    Path tempDir;

    private MetadataService metadataService;
    private EncryptionService encryptionService;
    private Supplier<byte[]> keySupplier;
    private final List<PerformanceMetrics> benchmarkResults = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        ServiceFactory serviceFactory = new ServiceFactory();
        encryptionService = new AesGcmEncryptionService();
        keySupplier = () -> "test-key-12345678901234567890123".getBytes(StandardCharsets.UTF_8); // 32 bytes

        // Use a persistent DB file for the benchmark to be realistic
        String dbPath = tempDir.resolve("benchmark-metadata.db").toString();
        // Use the factory method to create the encrypted service
        metadataService = serviceFactory.createEncryptedMetadataService(dbPath, encryptionService, keySupplier);
    }

    @AfterEach
    void tearDown() {
        // Print results
        System.out.println("\n=== PAGINATION PERFORMANCE REPORT ===\n");
        for (PerformanceMetrics metrics : benchmarkResults) {
            System.out.println(metrics.generateSummary());
            System.out.println("  Avg time per item (ms): " + metrics.getMetrics().get("avg_time_per_item_ms"));
            System.out.println();
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void benchmarkPaginationWithEncryption() throws IOException {
        int totalFiles = 20000;
        int pageSize = 100;

        PerformanceMetrics setupMetrics = new PerformanceMetrics("Setup - Insert " + totalFiles + " Files");

        // 1. Create Snapshot
        Snapshot snapshot = metadataService.createSnapshot("test-snapshot", "Benchmark Snapshot");
        String snapshotId = snapshot.getId();

        // 2. Insert Files
        Instant now = Instant.now();
        List<String> chunkHashes = Collections.emptyList(); // No chunks needed for this metric

        for (int i = 0; i < totalFiles; i++) {
            String path = "/benchmark/file/path/is/somewhat/long/to/simulate/real/paths/" + i + ".dat";
            FileMetadata file = new FileMetadata(
                    java.util.UUID.randomUUID().toString(), // Must generate ID
                    snapshotId,
                    path,
                    0, // Size 0 to avoid chunk requirement
                    now,
                    "hash" + i, // File hash
                    chunkHashes);
            metadataService.insertFile(file); // Encrypt filenames automatically via service config
        }
        setupMetrics.finalizeMetrics();
        benchmarkResults.add(setupMetrics);
        System.out.println("Inserted " + totalFiles + " files.");

        // 3. Benchmark First Page (Offset 0)
        measurePagination("Page 1 (Offset 0)", snapshotId, pageSize, 0);

        // 4. Benchmark Middle Page
        measurePagination("Middle Page (Offset " + (totalFiles / 2) + ")", snapshotId, pageSize, totalFiles / 2);

        // 5. Benchmark End Page
        measurePagination("End Page (Offset " + (totalFiles - pageSize) + ")", snapshotId, pageSize,
                totalFiles - pageSize);
    }

    private void measurePagination(String label, String snapshotId, int limit, int offset) throws IOException {
        PerformanceMetrics metrics = new PerformanceMetrics(label);

        List<FileMetadata> files = metadataService.getFilesInSnapshot(snapshotId, null, limit, offset);

        metrics.finalizeMetrics();

        assertEquals(limit, files.size(), "Should verify page size");

        metrics.recordMetric("limit", limit);
        metrics.recordMetric("offset", offset);
        metrics.recordMetric("items_returned", files.size());
        metrics.recordMetric("avg_time_per_item_ms", (double) metrics.getDurationMs() / files.size());

        benchmarkResults.add(metrics);
    }
}
