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
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;

class RestoreServiceTest {

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
    void testRestoreWithCustomTracker() throws Exception {
        // Setup mocks
        String snapshotId = "snap-1";
        FileMetadata fileMeta = new FileMetadata(
                "file-id-1",
                snapshotId,
                "file1.txt",
                100L,
                Instant.now(),
                "hash1",
                Collections.singletonList("chunk1"));

        when(metadataService.getSnapshot(snapshotId)).thenReturn(Optional.of(new Snapshot(
                snapshotId, "name", "desc", Instant.now(), 1, 100)));
        when(metadataService.getFilesInSnapshot(snapshotId)).thenReturn(Collections.singletonList(fileMeta));
        // Mock content store to return dummy data so restore succeeds
        when(contentStore.retrieveChunk("chunk1")).thenReturn(new byte[100]);
        // Mock integrity check
        when(blake3Service.hashBuffer(any(byte[].class))).thenReturn("chunkHash"); // Simplified
        when(blake3Service.hashFile(any())).thenReturn("hash1");

        // We skip actual integrity verification inside RestoreService for now by
        // mocking or using options
        // But RestoreService calls verifyChunkIntegrity which calls
        // blake3Service.hashBuffer
        // and finalize verification calls blake3Service.hashFile
        // Let's use a simpler approach: use the "test-snapshot-id" logic in
        // RestoreService if possible, or just standard mocks.
        // The test logic above uses standard mocks.
        // RestoreService.verifyChunkIntegrity: expectedHash vs actualHash.
        // We need expectedHash (from chunk list) to match hashBuffer result.
        // FileMetadata constructor takes chunkHashes list. We put "chunk1".
        // contentStore.retrieveChunk returns data.
        // verifyChunkIntegrity calls blake3Service.hashBuffer(chunkData).
        // It expects result to equal header hash? No, chunkHash.
        // FileMetadata has chunkHashes. "chunk1" is the hash.
        // So we need: when(blake3Service.hashBuffer(any())).thenReturn("chunk1");

        when(contentStore.retrieveChunk("chunk1")).thenReturn(new byte[10]);
        when(blake3Service.hashBuffer(any(byte[].class))).thenReturn("chunk1");
        when(blake3Service.hashFile(any())).thenReturn("hash1");

        RestoreProgressTracker tracker = org.mockito.Mockito.mock(RestoreProgressTracker.class);
        RestoreOptions options = new RestoreOptions.Builder().build();

        // Execute
        restoreService.restore(snapshotId, tempDir, options, tracker).get();

        // Verify tracker calls
        verify(tracker).startRestore(any(Snapshot.class), eq(tempDir));
        // updateProgress is called for start (0/N) and then for each file
        verify(tracker, org.mockito.Mockito.atLeastOnce()).updateProgress(anyLong(), anyLong(), anyLong(), anyLong(),
                any());
        verify(tracker).completeRestore(any(RestoreService.RestoreResult.class));
    }

    @Test
    void testRestoreExistingFileSkip() throws Exception {
        // Setup existing file
        Path targetFile = tempDir.resolve("existing.txt");
        Files.write(targetFile, "Old content".getBytes(StandardCharsets.UTF_8));

        // Setup mock data
        String snapshotId = "snap-1";
        FileMetadata fileMeta = new FileMetadata(
                "file-id-1",
                snapshotId,
                "existing.txt",
                100L,
                Instant.now(),
                "hash1",
                Collections.singletonList("chunk1"));

        when(metadataService.getSnapshot(snapshotId)).thenReturn(Optional.of(new Snapshot(
                snapshotId, "name", "desc", Instant.now(), 1, 100)));
        when(metadataService.getFilesInSnapshot(snapshotId)).thenReturn(Collections.singletonList(fileMeta));

        // Options: overwrite=false, skipExisting=true
        RestoreOptions options = new RestoreOptions.Builder()
                .overwriteExisting(false)
                .skipExisting(true)
                .build();

        // Execute
        RestoreService.RestoreResult result = restoreService.restore(snapshotId, tempDir, options).get();

        // Verify
        assertEquals(0, result.getFilesRestored());
        assertEquals(1, result.getFilesSkipped());
        assertEquals(0, result.getFilesWithErrors());

        // Ensure we didn't try to fetch chunks
        verify(contentStore, never()).retrieveChunk(anyString());
    }

    @Test
    void testRestoreExistingFileError() throws Exception {
        // Setup existing file
        Path targetFile = tempDir.resolve("existing.txt");
        Files.write(targetFile, "Old content".getBytes(StandardCharsets.UTF_8));

        // Setup mock data
        String snapshotId = "snap-1";
        FileMetadata fileMeta = new FileMetadata(
                "file-id-1",
                snapshotId,
                "existing.txt",
                100L,
                Instant.now(),
                "hash1",
                Collections.singletonList("chunk1"));

        when(metadataService.getSnapshot(snapshotId)).thenReturn(Optional.of(new Snapshot(
                snapshotId, "name", "desc", Instant.now(), 1, 100)));
        when(metadataService.getFilesInSnapshot(snapshotId)).thenReturn(Collections.singletonList(fileMeta));

        // Options: overwrite=false, skipExisting=false (default)
        RestoreOptions options = new RestoreOptions.Builder()
                .overwriteExisting(false)
                .skipExisting(false)
                .build();

        // Execute
        RestoreService.RestoreResult result = restoreService.restore(snapshotId, tempDir, options).get();

        // Verify
        assertEquals(0, result.getFilesRestored());
        assertEquals(0, result.getFilesSkipped());
        assertEquals(1, result.getFilesWithErrors()); // Should be an error
    }

    @Test
    void testRestoreWithAbsolutePath() throws Exception {
        // Setup mocks
        String snapshotId = "snap-abs";
        // Simulate absolute path in metadata: /home/user/data/file1.txt
        String absolutePath = "/home/user/data/file1.txt";
        String sourceRoot = "/home/user/data";

        FileMetadata fileMeta = new FileMetadata(
                "file-id-1",
                snapshotId,
                absolutePath,
                100L,
                Instant.now(),
                "hash1",
                Collections.singletonList("chunk1"));

        // IMPORTANT: Description must contain the source root for relativization to
        // work
        when(metadataService.getSnapshot(snapshotId)).thenReturn(Optional.of(new Snapshot(
                snapshotId, "name", "Processing session for directory: " + sourceRoot, Instant.now(), 1, 100)));
        when(metadataService.getFilesInSnapshot(snapshotId)).thenReturn(Collections.singletonList(fileMeta));

        when(contentStore.retrieveChunk("chunk1")).thenReturn(new byte[100]);
        when(blake3Service.hashBuffer(any(byte[].class))).thenReturn("chunk1");
        when(blake3Service.hashFile(any())).thenReturn("hash1");

        RestoreProgressTracker tracker = org.mockito.Mockito.mock(RestoreProgressTracker.class);
        RestoreOptions options = new RestoreOptions.Builder().build();

        // Execute
        RestoreService.RestoreResult result = restoreService.restore(snapshotId, tempDir, options, tracker).get();

        // Verify that file was restored to tempDir/file1.txt (path relativized)
        // file1.txt is relative to /home/user/data
        Path expectedFile = tempDir.resolve("file1.txt");

        assertEquals(1, result.getFilesRestored());
        // Verify we actually tried to hash the file at the expected location
        verify(blake3Service).hashFile(eq(expectedFile));
    }
}
