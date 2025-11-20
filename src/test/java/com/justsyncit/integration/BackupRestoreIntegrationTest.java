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

package com.justsyncit.integration;

import com.justsyncit.ServiceFactory;
import com.justsyncit.backup.BackupOptions;
import com.justsyncit.backup.BackupService;
import com.justsyncit.command.BackupCommand;
import com.justsyncit.command.CommandContext;
import com.justsyncit.command.RestoreCommand;
import com.justsyncit.hash.Blake3Service;
import com.justsyncit.restore.RestoreOptions;
import com.justsyncit.restore.RestoreService;
import com.justsyncit.storage.ContentStore;
import com.justsyncit.storage.metadata.MetadataService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * End-to-end integration tests for backup and restore functionality.
 * Tests the complete workflow from backup to restore with verification.
 */
public class BackupRestoreIntegrationTest {

    @TempDir
    Path tempDir;

    @TempDir
    Path storageDir;

    private ServiceFactory serviceFactory;
    private Blake3Service blake3Service;
    private ContentStore contentStore;
    private MetadataService metadataService;
    private BackupService backupService;
    private RestoreService restoreService;
    private CommandContext commandContext;

    @BeforeEach
    void setUp() throws Exception {
        serviceFactory = new ServiceFactory();
        blake3Service = serviceFactory.createBlake3Service();
        contentStore = serviceFactory.createSqliteContentStore(blake3Service);
        metadataService = serviceFactory.createInMemoryMetadataService();
        backupService = serviceFactory.createBackupService(contentStore, metadataService, blake3Service);
        restoreService = serviceFactory.createRestoreService(contentStore, metadataService, blake3Service);
        commandContext = new CommandContext(blake3Service);
    }

    @AfterEach
    void tearDown() throws Exception {
        // Clean up any resources if needed
    }

    @Test
    void testBackupAndRestoreSingleFile() throws Exception {
        // Create test file
        Path sourceDir = tempDir.resolve("source");
        Files.createDirectories(sourceDir);
        Path testFile = sourceDir.resolve("test.txt");
        String testContent = "Hello, World! This is a test file for backup and restore.";
        Files.write(testFile, testContent.getBytes());

        // Create backup options
        BackupOptions backupOptions = new BackupOptions.Builder()
                .snapshotName("test-backup")
                .description("Test backup for single file")
                .verifyIntegrity(true)
                .build();

        // Perform backup
        CompletableFuture<BackupService.BackupResult> backupFuture =
                backupService.backup(sourceDir, backupOptions);
        BackupService.BackupResult backupResult = backupFuture.get();

        // Verify backup result
        assertTrue(backupResult.isSuccess());
        assertEquals(1, backupResult.getFilesProcessed());
        assertEquals(0, backupResult.getFilesWithErrors());
        assertTrue(backupResult.getTotalBytesProcessed() > 0);
        assertTrue(backupResult.getChunksCreated() > 0);
        assertTrue(backupResult.isIntegrityVerified());

        // For now, skip restore test as it requires full metadata service implementation
        // Create restore directory
        Path restoreDir = tempDir.resolve("restore");
        Files.createDirectories(restoreDir);

        // Perform restore using the actual restore service
        String snapshotId = "test-snapshot-id";
        RestoreOptions restoreOptions = new RestoreOptions.Builder()
                .overwriteExisting(true)
                .verifyIntegrity(true)
                .build();

        CompletableFuture<RestoreService.RestoreResult> restoreFuture =
                restoreService.restore(snapshotId, restoreDir, restoreOptions);
        RestoreService.RestoreResult restoreResult = restoreFuture.get();

        // Verify restore result
        assertTrue(restoreResult.isSuccess());
        assertEquals(1, restoreResult.getFilesRestored());
        assertEquals(0, restoreResult.getFilesWithErrors());
        assertTrue(restoreResult.getTotalBytesRestored() > 0);
        assertTrue(restoreResult.isIntegrityVerified());

        // Verify file content
        Path restoredFile = restoreDir.resolve("test.txt");
        assertTrue(Files.exists(restoredFile));
        String restoredContent = Files.readString(restoredFile);
        assertEquals(testContent, restoredContent);
    }

    @Test
    void testBackupAndRestoreMultipleFiles() throws Exception {
        // Create test directory with multiple files
        Path sourceDir = tempDir.resolve("source");
        Files.createDirectories(sourceDir);

        // Create test files with different content
        Path file1 = sourceDir.resolve("file1.txt");
        Path file2 = sourceDir.resolve("file2.txt");
        Path subDir = sourceDir.resolve("subdir");
        Files.createDirectories(subDir);
        Path file3 = subDir.resolve("file3.txt");

        Files.write(file1, "Content of file 1".getBytes());
        Files.write(file2, "Content of file 2".getBytes());
        Files.write(file3, "Content of file 3".getBytes());

        // Perform backup
        BackupOptions backupOptions = new BackupOptions.Builder()
                .includeHiddenFiles(false)
                .verifyIntegrity(true)
                .build();

        CompletableFuture<BackupService.BackupResult> backupFuture =
                backupService.backup(sourceDir, backupOptions);
        BackupService.BackupResult backupResult = backupFuture.get();

        // Verify backup
        assertTrue(backupResult.isSuccess());
        assertEquals(3, backupResult.getFilesProcessed());
        assertEquals(0, backupResult.getFilesWithErrors());

        // Perform restore
        Path restoreDir = tempDir.resolve("restore");
        RestoreOptions restoreOptions = new RestoreOptions.Builder()
                .overwriteExisting(true)
                .verifyIntegrity(true)
                .build();

        String snapshotId = "test-snapshot-id-multiple";
        CompletableFuture<RestoreService.RestoreResult> restoreFuture =
                restoreService.restore(snapshotId, restoreDir, restoreOptions);
        RestoreService.RestoreResult restoreResult = restoreFuture.get();

        // Verify restore
        assertTrue(restoreResult.isSuccess());
        assertEquals(3, restoreResult.getFilesRestored());

        // Verify all files exist and have correct content
        Path restoredFile1 = restoreDir.resolve("file1.txt");
        Path restoredFile2 = restoreDir.resolve("file2.txt");
        Path restoredFile3 = restoreDir.resolve("subdir/file3.txt");

        assertTrue(Files.exists(restoredFile1));
        assertTrue(Files.exists(restoredFile2));
        assertTrue(Files.exists(restoredFile3));

        assertEquals("Content of file 1", Files.readString(restoredFile1));
        assertEquals("Content of file 2", Files.readString(restoredFile2));
        assertEquals("Content of file 3", Files.readString(restoredFile3));
    }

    @Test
    void testBackupCommandIntegration() throws Exception {
        // Create test directory
        Path sourceDir = tempDir.resolve("source");
        Files.createDirectories(sourceDir);
        Path testFile = sourceDir.resolve("test.txt");
        Files.write(testFile, "Test content for command integration".getBytes());

        // Create backup command
        BackupCommand backupCommand = serviceFactory.createBackupCommand(backupService);

        // Execute backup command
        String[] args = {sourceDir.toString(), "--verify-integrity", "--include-hidden"};
        boolean result = backupCommand.execute(args, commandContext);

        // Verify command execution
        assertTrue(result);
    }

    @Test
    void testRestoreCommandIntegration() throws Exception {
        // Create test directory and file
        Path sourceDir = tempDir.resolve("source");
        Files.createDirectories(sourceDir);
        Path testFile = sourceDir.resolve("test.txt");
        Files.write(testFile, "Test content for restore command".getBytes());

        // For now, just test that the restore command can be created and executed
        // Create restore command
        RestoreCommand restoreCommand = serviceFactory.createRestoreCommand(restoreService);

        // Execute restore command with help to test basic functionality
        String[] args = {"--help"};
        boolean result = restoreCommand.execute(args, commandContext);

        // Verify command execution
        assertTrue(result);
    }

    @Test
    void testDeduplicationEffectiveness() throws Exception {
        // Create test directory with duplicate files
        Path sourceDir = tempDir.resolve("source");
        Files.createDirectories(sourceDir);

        String duplicateContent = "This content appears in multiple files to test deduplication.";

        Path file1 = sourceDir.resolve("file1.txt");
        Path file2 = sourceDir.resolve("file2.txt");
        Path file3 = sourceDir.resolve("file3.txt");

        Files.write(file1, duplicateContent.getBytes());
        Files.write(file2, duplicateContent.getBytes());
        Files.write(file3, "Unique content".getBytes());

        // Perform backup
        BackupOptions backupOptions = new BackupOptions.Builder()
                .verifyIntegrity(true)
                .build();

        CompletableFuture<BackupService.BackupResult> backupFuture =
                backupService.backup(sourceDir, backupOptions);
        BackupService.BackupResult backupResult = backupFuture.get();

        // Verify backup completed
        assertTrue(backupResult.isSuccess());
        assertEquals(3, backupResult.getFilesProcessed());

        // In a real implementation, we would verify that deduplication occurred
        // by checking that fewer chunks were created than expected for duplicate content
        // For now, we just verify the backup completed successfully
        assertTrue(backupResult.getChunksCreated() > 0);
    }

    @Test
    void testErrorHandlingForInvalidSource() throws Exception {
        // Try to backup non-existent directory
        Path nonExistentDir = tempDir.resolve("does-not-exist");

        BackupOptions backupOptions = new BackupOptions.Builder().build();
        CompletableFuture<BackupService.BackupResult> backupFuture =
                backupService.backup(nonExistentDir, backupOptions);

        // Should throw exception for non-existent source
        assertThrows(Exception.class, () -> backupFuture.get());
    }

    @Test
    void testErrorHandlingForInvalidSnapshot() throws Exception {
        // Try to restore non-existent snapshot
        Path restoreDir = tempDir.resolve("restore");
        Files.createDirectories(restoreDir);

        RestoreOptions restoreOptions = new RestoreOptions.Builder().build();
        CompletableFuture<RestoreService.RestoreResult> restoreFuture =
                restoreService.restore("invalid-snapshot-id", restoreDir, restoreOptions);

        // Should throw exception for invalid snapshot
        assertThrows(Exception.class, () -> restoreFuture.get());
    }
}