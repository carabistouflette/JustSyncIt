package com.justsyncit.backup.cbt;

import com.justsyncit.ServiceFactory;
import com.justsyncit.backup.BackupOptions;
import com.justsyncit.backup.BackupService;
import com.justsyncit.hash.Blake3Service;
import com.justsyncit.scanner.AsyncByteBufferPool;
import com.justsyncit.scanner.FileChunker;
import com.justsyncit.scanner.FilesystemScanner;
import com.justsyncit.scanner.NioFilesystemScanner;
import com.justsyncit.scanner.FixedSizeFileChunker;
import com.justsyncit.scanner.ThreadPoolManager;
import com.justsyncit.storage.ContentStore;
import com.justsyncit.storage.FilesystemChunkIndex;
import com.justsyncit.storage.FilesystemContentStore;
import com.justsyncit.storage.metadata.MetadataService;
import com.justsyncit.storage.metadata.Snapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CBTIntegrationTest {

    @TempDir
    Path tempDir;

    private Path sourceDir;
    private Path storageDir;
    private Path journalDir;

    private ServiceFactory serviceFactory;
    private BackupService backupService;
    private ChangedBlockTrackingService cbtService;
    private MetadataService metadataService;

    @BeforeEach
    void setUp() throws Exception {
        sourceDir = tempDir.resolve("source");
        storageDir = tempDir.resolve("storage");
        journalDir = tempDir.resolve("journal");
        Files.createDirectories(sourceDir);
        Files.createDirectories(storageDir);
        Files.createDirectories(journalDir);

        serviceFactory = new ServiceFactory();
        Blake3Service blake3Service = serviceFactory.createBlake3Service();

        // Use file-based metadata service to ensure test isolation and avoid static
        // shared state issues
        // from InMemoryMetadataService factory
        metadataService = serviceFactory.createMetadataService(tempDir.resolve("metadata.db").toString());

        // Manual ContentStore setup for temp dir
        Path chunksDir = storageDir.resolve("chunks");
        Path indexFile = storageDir.resolve("index.txt");
        FilesystemChunkIndex chunkIndex = FilesystemChunkIndex.create(chunksDir, indexFile);
        ContentStore contentStore = FilesystemContentStore.create(chunksDir, chunkIndex, blake3Service);

        FilesystemScanner scanner = new NioFilesystemScanner();
        FileChunker chunker = FixedSizeFileChunker.create(blake3Service);

        // Setup CBT
        ThreadPoolManager threadPoolManager = serviceFactory.createThreadPoolManager();
        AsyncByteBufferPool bufferPool = serviceFactory.createAsyncByteBufferPool();
        cbtService = new ChangedBlockTrackingService(threadPoolManager, bufferPool, journalDir);
        cbtService.start();

        // BackupService with CBT
        backupService = new BackupService(contentStore, metadataService, scanner, chunker, cbtService);
    }

    @AfterEach
    void tearDown() {
        if (cbtService != null) {
            cbtService.stop();
        }
    }

    @Test
    void testIncrementalBackupWithCBT() throws Exception {
        // 1. Setup initial files
        Path file1 = sourceDir.resolve("file1.txt");
        Files.writeString(file1, "Hello World");

        // 2. Enable tracking
        cbtService.enableTracking(sourceDir);

        // Wait a bit for watch service to register (it's async)
        Thread.sleep(1000);

        // 3. Perform Initial Full Backup
        BackupOptions options = new BackupOptions.Builder().build();
        BackupService.BackupResult fullResult = backupService.backup(sourceDir, options).get();
        String fullSnapshotId = fullResult.getSnapshotId();

        assertNotNull(fullSnapshotId);
        assertEquals(1, fullResult.getFilesProcessed());

        // 4. Modify file
        Files.writeString(file1, "Hello World Modified");

        // Wait for WatchService to pick up change (debounce is 500ms)
        Thread.sleep(2000);

        // Verify CBT has it marked
        Snapshot fullSnapshot = metadataService.getSnapshot(fullSnapshotId).get();
        // Just verify tracking happens in backupIncremental call logic

        // 5. Perform Incremental Backup
        BackupService.BackupResult incResult = backupService.backupIncremental(sourceDir, options, fullSnapshotId)
                .get();

        assertEquals(1, incResult.getFilesProcessed(), "Should process the 1 changed file");

        // 6. No changes
        Thread.sleep(1000); // ensure time passes for new snapshot timestamp diff
        Snapshot incSnapshot = metadataService.getSnapshot(incResult.getSnapshotId()).get();

        BackupService.BackupResult noChangeResult = backupService
                .backupIncremental(sourceDir, options, incResult.getSnapshotId()).get();
        // Since no changes occurred since incSnapshot, it should process 0 files.
        assertEquals(0, noChangeResult.getFilesProcessed(), "Should process 0 files when no changes");
    }
}
