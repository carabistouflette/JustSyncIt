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

import com.justsyncit.command.SnapshotsCommandGroup;
import com.justsyncit.command.SnapshotsDeleteCommand;
import com.justsyncit.command.SnapshotsInfoCommand;
import com.justsyncit.command.SnapshotsListCommand;
import com.justsyncit.command.SnapshotsVerifyCommand;
import com.justsyncit.storage.metadata.MetadataService;
import com.justsyncit.storage.metadata.Snapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for snapshot management functionality.
 * Tests all snapshot commands: list, info, delete, verify.
 */
public class SnapshotManagementE2ETest extends E2ETestBase {

    private SnapshotsCommandGroup snapshotsCommand;
    private SnapshotsListCommand listCommand;
    private SnapshotsInfoCommand infoCommand;
    private SnapshotsDeleteCommand deleteCommand;
    private SnapshotsVerifyCommand verifyCommand;
    private com.justsyncit.command.CommandContext commandContext;

    @BeforeEach
    void setUpSnapshotCommands() throws Exception {
        super.setUp();
        commandContext = new com.justsyncit.command.CommandContext(blake3Service);
        snapshotsCommand = new SnapshotsCommandGroup();
        listCommand = new SnapshotsListCommand(metadataService);
        infoCommand = new SnapshotsInfoCommand(metadataService);
        deleteCommand = new SnapshotsDeleteCommand(metadataService);
        verifyCommand = new SnapshotsVerifyCommand(metadataService, blake3Service);
    }

    @Test
    void testListSnapshotsEmpty() throws Exception {
        // Test listing snapshots when none exist
        String[] args = {};
        
        boolean result = snapshotsCommand.execute(new String[]{"list"}, commandContext);
        
        assertTrue(result, "List command should succeed");
        
        // Verify no snapshots are listed
        List<Snapshot> snapshots = metadataService.listSnapshots();
        assertTrue(snapshots.isEmpty(), "Should have no snapshots initially");
    }

    @Test
    void testListSnapshotsWithData() throws Exception {
        // Create multiple snapshots
        String snapshot1 = performBackup("test-snapshot-1", "First test snapshot");
        String snapshot2 = performBackup("test-snapshot-2", "Second test snapshot");
        String snapshot3 = performBackup("test-snapshot-3", "Third test snapshot");

        // Test listing snapshots
        boolean result = snapshotsCommand.execute(new String[]{"list"}, commandContext);
        
        assertTrue(result, "List command should succeed");
        
        // Verify snapshots are listed
        List<Snapshot> snapshots = metadataService.listSnapshots();
        assertEquals(3, snapshots.size(), "Should have 3 snapshots");
        
        // Verify snapshots are in correct order (newest first)
        assertTrue(snapshots.get(0).getCreatedAt().isAfter(snapshots.get(1).getCreatedAt()),
                "Snapshots should be ordered by creation time (newest first)");
        assertTrue(snapshots.get(1).getCreatedAt().isAfter(snapshots.get(2).getCreatedAt()),
                "Snapshots should be ordered by creation time (newest first)");
    }

    @Test
    void testSnapshotInfo() throws Exception {
        // Create a snapshot
        String snapshotId = performBackup("info-test-snapshot", "Snapshot for info testing");

        // Test getting snapshot info
        boolean result = snapshotsCommand.execute(new String[]{"info", snapshotId}, commandContext);
        
        assertTrue(result, "Info command should succeed");
        
        // Verify snapshot info is displayed
        Optional<Snapshot> snapshotOpt = metadataService.getSnapshot(snapshotId);
        assertTrue(snapshotOpt.isPresent(), "Snapshot should exist");
        
        Snapshot snapshot = snapshotOpt.get();
        assertEquals("info-test-snapshot", snapshot.getName(), "Snapshot name should match");
        assertEquals("Snapshot for info testing", snapshot.getDescription(), "Snapshot description should match");
        assertTrue(snapshot.getTotalFiles() > 0, "Snapshot should have files");
        assertTrue(snapshot.getTotalSize() > 0, "Snapshot should have size");
    }

    @Test
    void testSnapshotInfoNonExistent() throws Exception {
        // Test getting info for non-existent snapshot
        String nonExistentId = "non-existent-snapshot";
        
        boolean result = snapshotsCommand.execute(new String[]{"info", nonExistentId}, commandContext);
        
        // Command should fail gracefully
        assertFalse(result, "Info command should fail for non-existent snapshot");
        
        // Verify snapshot doesn't exist
        assertSnapshotDoesNotExist(nonExistentId);
    }

    @Test
    void testDeleteSnapshot() throws Exception {
        // Create a snapshot
        String snapshotId = performBackup("delete-test-snapshot", "Snapshot for delete testing");
        assertSnapshotExists(snapshotId);

        // Test deleting snapshot
        boolean result = snapshotsCommand.execute(new String[]{"delete", snapshotId}, commandContext);
        
        assertTrue(result, "Delete command should succeed");
        
        // Verify snapshot is deleted
        assertSnapshotDoesNotExist(snapshotId);
    }

    @Test
    void testDeleteSnapshotNonExistent() throws Exception {
        // Test deleting non-existent snapshot
        String nonExistentId = "non-existent-snapshot";
        
        boolean result = snapshotsCommand.execute(new String[]{"delete", nonExistentId}, commandContext);
        
        // Command should fail gracefully
        assertFalse(result, "Delete command should fail for non-existent snapshot");
    }

    @Test
    void testVerifySnapshotValid() throws Exception {
        // Create a snapshot
        String snapshotId = performBackup("verify-test-snapshot", "Snapshot for verify testing");
        assertSnapshotExists(snapshotId);

        // Test verifying snapshot
        boolean result = snapshotsCommand.execute(new String[]{"verify", snapshotId}, commandContext);
        
        assertTrue(result, "Verify command should succeed for valid snapshot");
    }

    @Test
    void testVerifySnapshotNonExistent() throws Exception {
        // Test verifying non-existent snapshot
        String nonExistentId = "non-existent-snapshot";
        
        boolean result = snapshotsCommand.execute(new String[]{"verify", nonExistentId}, commandContext);
        
        // Command should fail gracefully
        assertFalse(result, "Verify command should fail for non-existent snapshot");
    }

    @Test
    void testSnapshotManagementWorkflow() throws Exception {
        // Complete workflow: create -> list -> info -> verify -> delete
        
        // 1. Create multiple snapshots
        String snapshot1 = performBackup("workflow-snapshot-1", "Workflow test snapshot 1");
        String snapshot2 = performBackup("workflow-snapshot-2", "Workflow test snapshot 2");
        
        // 2. List snapshots
        boolean listResult = snapshotsCommand.execute(new String[]{"list"}, commandContext);
        assertTrue(listResult, "List command should succeed");
        
        List<Snapshot> snapshots = metadataService.listSnapshots();
        assertEquals(2, snapshots.size(), "Should have 2 snapshots");
        
        // 3. Get info for first snapshot
        boolean infoResult = snapshotsCommand.execute(new String[]{"info", snapshot1}, commandContext);
        assertTrue(infoResult, "Info command should succeed");
        
        // 4. Verify both snapshots
        boolean verifyResult1 = snapshotsCommand.execute(new String[]{"verify", snapshot1}, commandContext);
        assertTrue(verifyResult1, "Verify command should succeed for first snapshot");
        
        boolean verifyResult2 = snapshotsCommand.execute(new String[]{"verify", snapshot2}, commandContext);
        assertTrue(verifyResult2, "Verify command should succeed for second snapshot");
        
        // 5. Delete first snapshot
        boolean deleteResult = snapshotsCommand.execute(new String[]{"delete", snapshot1}, commandContext);
        assertTrue(deleteResult, "Delete command should succeed");
        
        // 6. Verify only second snapshot remains
        assertSnapshotDoesNotExist(snapshot1);
        assertSnapshotExists(snapshot2);
        
        List<Snapshot> remainingSnapshots = metadataService.listSnapshots();
        assertEquals(1, remainingSnapshots.size(), "Should have 1 remaining snapshot");
        assertEquals(snapshot2, remainingSnapshots.get(0).getId(), "Remaining snapshot should be the second one");
    }

    @Test
    void testSnapshotManagementWithSpecialCharacters() throws Exception {
        // Test snapshot management with special characters in names and data
        createSpecialCharacterDataset();
        
        String snapshotName = "special-chars-测试-файл-éàç";
        String snapshotId = performBackup(snapshotName, "Snapshot with special characters: 测试 русский français");
        
        assertSnapshotExists(snapshotId);
        
        // Test listing snapshots with special characters
        boolean listResult = snapshotsCommand.execute(new String[]{"list"}, commandContext);
        assertTrue(listResult, "List command should succeed with special characters");
        
        // Test getting info for snapshot with special characters
        boolean infoResult = snapshotsCommand.execute(new String[]{"info", snapshotId}, commandContext);
        assertTrue(infoResult, "Info command should succeed with special characters");
        
        // Test verifying snapshot with special characters
        boolean verifyResult = snapshotsCommand.execute(new String[]{"verify", snapshotId}, commandContext);
        assertTrue(verifyResult, "Verify command should succeed with special characters");
        
        // Clean up
        boolean deleteResult = snapshotsCommand.execute(new String[]{"delete", snapshotId}, commandContext);
        assertTrue(deleteResult, "Delete command should succeed with special characters");
    }

    @Test
    void testSnapshotManagementWithLargeDataset() throws Exception {
        // Test with large dataset to ensure performance
        createPerformanceDataset(100, 1024 * 10); // 100 files, up to 10KB each
        
        String snapshotId = performBackup("large-dataset-snapshot", "Large dataset for performance testing");
        
        assertSnapshotExists(snapshotId);
        
        // Test operations on large snapshot
        boolean listResult = snapshotsCommand.execute(new String[]{"list"}, commandContext);
        assertTrue(listResult, "List command should succeed with large dataset");
        
        boolean infoResult = snapshotsCommand.execute(new String[]{"info", snapshotId}, commandContext);
        assertTrue(infoResult, "Info command should succeed with large dataset");
        
        boolean verifyResult = snapshotsCommand.execute(new String[]{"verify", snapshotId}, commandContext);
        assertTrue(verifyResult, "Verify command should succeed with large dataset");
        
        // Verify snapshot has expected size
        Optional<Snapshot> snapshotOpt = metadataService.getSnapshot(snapshotId);
        assertTrue(snapshotOpt.isPresent(), "Snapshot should exist");
        
        Snapshot snapshot = snapshotOpt.get();
        assertEquals(100, snapshot.getTotalFiles(), "Should have 100 files");
        assertTrue(snapshot.getTotalSize() > 100 * 1024, "Should have significant size");
    }

    @Test
    void testSnapshotManagementConcurrentOperations() throws Exception {
        // Test concurrent snapshot operations
        createBasicDataset();
        
        // Create multiple snapshots concurrently
        String snapshot1 = performBackup("concurrent-snapshot-1", "Concurrent test 1");
        String snapshot2 = performBackup("concurrent-snapshot-2", "Concurrent test 2");
        
        // Test concurrent listing
        Thread listThread = new Thread(() -> {
            boolean result = snapshotsCommand.execute(new String[]{"list"}, commandContext);
            assertTrue(result, "List command should succeed in concurrent context");
        });
        
        Thread infoThread = new Thread(() -> {
            boolean result = snapshotsCommand.execute(new String[]{"info", snapshot1}, commandContext);
            assertTrue(result, "Info command should succeed in concurrent context");
        });
        
        // Start threads
        listThread.start();
        infoThread.start();
        
        // Wait for completion
        listThread.join(5000);
        infoThread.join(5000);
        
        // Verify both snapshots still exist
        assertSnapshotExists(snapshot1);
        assertSnapshotExists(snapshot2);
    }

    @Test
    void testSnapshotManagementErrorHandling() throws Exception {
        // Test error handling in snapshot commands
        
        // Test with null/empty snapshot ID
        boolean result1 = snapshotsCommand.execute(new String[]{"info", ""}, commandContext);
        assertFalse(result1, "Info command should fail with empty snapshot ID");
        
        boolean result2 = snapshotsCommand.execute(new String[]{"delete", null}, commandContext);
        assertFalse(result2, "Delete command should fail with null snapshot ID");
        
        boolean result3 = snapshotsCommand.execute(new String[]{"verify", "   "}, commandContext); // whitespace only
        assertFalse(result3, "Verify command should fail with whitespace-only snapshot ID");
        
        // Test with invalid subcommand
        boolean result4 = snapshotsCommand.execute(new String[]{"invalid"}, commandContext);
        assertFalse(result4, "Should fail with invalid subcommand");
        
        // Test help command
        boolean result5 = snapshotsCommand.execute(new String[]{"--help"}, commandContext);
        assertTrue(result5, "Help command should succeed");
    }

    @Test
    void testSnapshotManagementIntegrity() throws Exception {
        // Test that snapshot management maintains data integrity
        createBasicDataset();
        
        String snapshotId = performBackup("integrity-test-snapshot", "Integrity test snapshot");
        
        // Verify snapshot multiple times
        for (int i = 0; i < 5; i++) {
            boolean verifyResult = snapshotsCommand.execute(new String[]{"verify", snapshotId}, commandContext);
            assertTrue(verifyResult, "Verify command should succeed consistently: " + i);
            
            Optional<Snapshot> snapshotOpt = metadataService.getSnapshot(snapshotId);
            assertTrue(snapshotOpt.isPresent(), "Snapshot should persist: " + i);
            
            Snapshot snapshot = snapshotOpt.get();
            assertEquals("integrity-test-snapshot", snapshot.getName(), "Snapshot name should persist: " + i);
            assertEquals("Integrity test snapshot", snapshot.getDescription(), "Snapshot description should persist: " + i);
        }
    }

    @Test
    void testSnapshotManagementHelpAndUsage() throws Exception {
        // Test help functionality
        boolean helpResult = snapshotsCommand.execute(new String[]{"--help"}, commandContext);
        assertTrue(helpResult, "Help command should succeed");
        
        boolean helpResult2 = snapshotsCommand.execute(new String[]{"help"}, commandContext);
        assertTrue(helpResult2, "Help command should succeed");
        
        // Test usage display
        boolean usageResult = snapshotsCommand.execute(new String[]{}, commandContext);
        assertFalse(usageResult, "Should fail with no arguments and show usage");
    }
}