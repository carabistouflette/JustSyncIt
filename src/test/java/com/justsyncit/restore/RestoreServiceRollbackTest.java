package com.justsyncit.restore;

import com.justsyncit.hash.Blake3Service;
import com.justsyncit.storage.ContentStore;
import com.justsyncit.storage.metadata.FileMetadata;
import com.justsyncit.storage.metadata.MetadataService;
import com.justsyncit.storage.metadata.Snapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class RestoreServiceRollbackTest {

        @Mock
        private ContentStore contentStore;
        @Mock
        private MetadataService metadataService;
        @Mock
        private Blake3Service blake3Service;

        private RestoreService restoreService;

        @TempDir
        Path tempDir;

        @BeforeEach
        void setUp() {
                MockitoAnnotations.openMocks(this);
                restoreService = new RestoreService(contentStore, metadataService, blake3Service);
        }

        @Test
        void shouldRollbackToSnapshotState() throws Exception {
                // Setup
                String snapshotId = "snap-123";
                Path targetDir = tempDir.resolve("target");
                Files.createDirectories(targetDir);

                // Files in snapshot
                // File A: "contentA"
                // File A: "contentA"
                FileMetadata fileA = new FileMetadata(
                                "file-id-A",
                                snapshotId,
                                "fileA.txt",
                                8,
                                java.time.Instant.now(),
                                "hashA",
                                Collections.singletonList("chunkA"));

                // Mock snapshot metadata
                Snapshot snapshot = new Snapshot(snapshotId, "Desc",
                                "Processing session for directory: /source | extra",
                                java.time.Instant.now(), 1, 100);

                when(metadataService.getSnapshot(snapshotId)).thenReturn(Optional.of(snapshot));
                when(metadataService.getFilesInSnapshot(snapshotId)).thenReturn(Collections.singletonList(fileA));

                when(contentStore.retrieveChunk("chunkA")).thenReturn("contentA".getBytes(StandardCharsets.UTF_8));
                when(blake3Service.hashBuffer(any(byte[].class))).thenReturn("chunkA"); // Simplified mock
                when(blake3Service.hashFile(any(Path.class))).thenReturn("hashA");

                // Current state on disk:
                // - fileA.txt (modified content)
                // - fileB.txt (extraneous file)
                Files.write(targetDir.resolve("fileA.txt"), "modified".getBytes(StandardCharsets.UTF_8));
                Files.write(targetDir.resolve("fileB.txt"), "extra".getBytes(StandardCharsets.UTF_8));

                // Action
                RestoreOptions options = new RestoreOptions.Builder().build();
                // Note: rollback should force overwriteExisting=true internally, but setDryRun
                // defaults false

                CompletableFuture<RestoreService.RestoreResult> future = restoreService.rollback(snapshotId, targetDir,
                                options);
                RestoreService.RestoreResult result = future.join();

                // Assert
                assertTrue(result.isSuccess());

                // 1. fileB.txt should be deleted (extraneous)
                assertFalse(Files.exists(targetDir.resolve("fileB.txt")), "Extraneous file should be deleted");

                // 2. fileA.txt should be restored to "contentA"
                assertTrue(Files.exists(targetDir.resolve("fileA.txt")));
                String content = Files.readString(targetDir.resolve("fileA.txt"));
                assertEquals("contentA", content, "Modified file should be reverted");
        }

        @Test
        void shouldSupportDryRun() throws Exception {
                // Setup similar to above but with dryRun
                String snapshotId = "snap-dry";
                Path targetDir = tempDir.resolve("target-dry");
                Files.createDirectories(targetDir);

                Snapshot snapshot = new Snapshot(snapshotId, "Desc", "Processing session for directory: /source",
                                java.time.Instant.now(), 1, 100);

                when(metadataService.getSnapshot(snapshotId)).thenReturn(Optional.of(snapshot));
                when(metadataService.getFilesInSnapshot(snapshotId)).thenReturn(Collections.emptyList()); // Empty
                                                                                                          // snapshot

                // Add extra file
                Files.write(targetDir.resolve("extra.txt"), "data".getBytes());

                RestoreOptions options = new RestoreOptions.Builder().build();
                options.setDryRun(true);

                // Action
                CompletableFuture<RestoreService.RestoreResult> future = restoreService.rollback(snapshotId, targetDir,
                                options);
                future.join();

                // Assert
                assertTrue(Files.exists(targetDir.resolve("extra.txt")),
                                "Extraneous file should NOT be deleted in dry run");
        }
}
