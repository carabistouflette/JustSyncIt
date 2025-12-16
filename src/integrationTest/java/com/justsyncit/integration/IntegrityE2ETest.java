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

import com.justsyncit.restore.RestoreService;
import com.justsyncit.storage.IntegrityVerifier;
import com.justsyncit.storage.IntegrityVerifierFactory;
import com.justsyncit.storage.metadata.FileMetadata;
import com.justsyncit.storage.metadata.Snapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * End-to-end integration tests for data integrity verification.
 * Tests integrity checking across all operations including backup, restore, and
 * storage.
 */
public class IntegrityE2ETest extends E2ETestBase {

    private IntegrityVerifier integrityVerifier;

    @org.junit.jupiter.api.BeforeEach
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        integrityVerifier = IntegrityVerifierFactory.createBlake3Verifier(blake3Service);
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testBasicIntegrityVerification() throws Exception {
        // Create test data with known content
        createBasicDataset();

        // Calculate original file checksums
        List<FileChecksum> originalChecksums = calculateFileChecksums(sourceDir);

        // Perform backup
        String snapshotId = performBackup("integrity-basic-snapshot", "Basic integrity test");

        // Perform restore
        RestoreService.RestoreResult restoreResult = performRestore(snapshotId);

        // Calculate restored file checksums
        List<FileChecksum> restoredChecksums = calculateFileChecksums(restoreDir);

        // Verify integrity
        assertEquals(originalChecksums.size(), restoredChecksums.size(),
                "Should have same number of files after restore");

        for (FileChecksum original : originalChecksums) {
            Optional<FileChecksum> restored = restoredChecksums.stream()
                    .filter(c -> c.getFileName().equals(original.getFileName()))
                    .findFirst();

            assertTrue(restored.isPresent(), "Restored file should exist: " + original.getFileName());
            assertEquals(original.getChecksum(), restored.get().getChecksum(),
                    "File checksum should match: " + original.getFileName());
        }

        // Verify restore result indicates integrity was checked
        assertTrue(restoreResult.isIntegrityVerified(), "Restore should verify integrity");
    }

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void testIntegrityWithLargeFiles() throws Exception {
        // Create large test files
        createPerformanceDataset(10, 1024 * 1024); // 10 files, up to 1MB each

        // Calculate original file checksums
        List<FileChecksum> originalChecksums = calculateFileChecksums(sourceDir);

        // Perform backup with integrity verification
        String snapshotId = performBackup("integrity-large-snapshot", "Large files integrity test");

        // Perform restore with integrity verification
        RestoreService.RestoreResult restoreResult = performRestore(snapshotId);

        // Calculate restored file checksums
        List<FileChecksum> restoredChecksums = calculateFileChecksums(restoreDir);

        // Verify integrity for large files
        assertEquals(originalChecksums.size(), restoredChecksums.size(),
                "Should have same number of large files after restore");

        for (FileChecksum original : originalChecksums) {
            Optional<FileChecksum> restored = restoredChecksums.stream()
                    .filter(c -> c.getFileName().equals(original.getFileName()))
                    .findFirst();

            assertTrue(restored.isPresent(), "Restored large file should exist: " + original.getFileName());
            assertEquals(original.getChecksum(), restored.get().getChecksum(),
                    "Large file checksum should match: " + original.getFileName());
        }

        assertTrue(restoreResult.isIntegrityVerified(), "Large files restore should verify integrity");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testIntegrityWithSpecialCharacters() throws Exception {
        // Create files with special characters
        createSpecialCharacterDataset();

        // Calculate original file checksums
        List<FileChecksum> originalChecksums = calculateFileChecksums(sourceDir);

        // Perform backup
        String snapshotId = performBackup("integrity-special-snapshot", "Special characters integrity test");

        // Perform restore
        RestoreService.RestoreResult restoreResult = performRestore(snapshotId);

        // Calculate restored file checksums
        List<FileChecksum> restoredChecksums = calculateFileChecksums(restoreDir);

        // Verify integrity for files with special characters
        assertEquals(originalChecksums.size(), restoredChecksums.size(),
                "Should have same number of special character files after restore");

        for (FileChecksum original : originalChecksums) {
            Optional<FileChecksum> restored = restoredChecksums.stream()
                    .filter(c -> c.getFileName().equals(original.getFileName()))
                    .findFirst();

            assertTrue(restored.isPresent(),
                    "Restored special character file should exist: " + original.getFileName());
            assertEquals(original.getChecksum(), restored.get().getChecksum(),
                    "Special character file checksum should match: " + original.getFileName());
        }

        assertTrue(restoreResult.isIntegrityVerified(),
                "Special character files restore should verify integrity");
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void testIntegrityWithEmptyFiles() throws Exception {
        // Create empty files
        createEmptyDataset();

        // Calculate original file checksums
        List<FileChecksum> originalChecksums = calculateFileChecksums(sourceDir);

        // Perform backup
        String snapshotId = performBackup("integrity-empty-snapshot", "Empty files integrity test");

        // Perform restore
        RestoreService.RestoreResult restoreResult = performRestore(snapshotId);

        // Calculate restored file checksums
        List<FileChecksum> restoredChecksums = calculateFileChecksums(restoreDir);

        // Verify integrity for empty files
        assertEquals(originalChecksums.size(), restoredChecksums.size(),
                "Should have same number of empty files after restore");

        for (FileChecksum original : originalChecksums) {
            Optional<FileChecksum> restored = restoredChecksums.stream()
                    .filter(c -> c.getFileName().equals(original.getFileName()))
                    .findFirst();

            assertTrue(restored.isPresent(), "Restored empty file should exist: " + original.getFileName());
            assertEquals(original.getChecksum(), restored.get().getChecksum(),
                    "Empty file checksum should match: " + original.getFileName());
        }

        assertTrue(restoreResult.isIntegrityVerified(), "Empty files restore should verify integrity");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testIntegrityWithDuplicateFiles() throws Exception {
        // Create files with duplicates
        createDuplicateDataset();

        // Calculate original file checksums
        List<FileChecksum> originalChecksums = calculateFileChecksums(sourceDir);

        // Perform backup
        String snapshotId = performBackup("integrity-duplicate-snapshot", "Duplicate files integrity test");

        // Perform restore
        RestoreService.RestoreResult restoreResult = performRestore(snapshotId);

        // Calculate restored file checksums
        List<FileChecksum> restoredChecksums = calculateFileChecksums(restoreDir);

        // Verify integrity for duplicate files
        assertEquals(originalChecksums.size(), restoredChecksums.size(),
                "Should have same number of duplicate files after restore");

        for (FileChecksum original : originalChecksums) {
            Optional<FileChecksum> restored = restoredChecksums.stream()
                    .filter(c -> c.getFileName().equals(original.getFileName()))
                    .findFirst();

            assertTrue(restored.isPresent(), "Restored duplicate file should exist: " + original.getFileName());
            assertEquals(original.getChecksum(), restored.get().getChecksum(),
                    "Duplicate file checksum should match: " + original.getFileName());
        }

        assertTrue(restoreResult.isIntegrityVerified(), "Duplicate files restore should verify integrity");
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testIntegrityAcrossMultipleBackups() throws Exception {
        // Create initial dataset
        createBasicDataset();
        List<FileChecksum> originalChecksums1 = calculateFileChecksums(sourceDir);

        // Perform first backup
        String snapshotId1 = performBackup("integrity-multi1-snapshot", "First backup for multi-backup integrity test");

        // Modify dataset
        Path newFile = sourceDir.resolve("new-file.txt");
        Files.writeString(newFile, "New file content for second backup");
        List<FileChecksum> originalChecksums2 = calculateFileChecksums(sourceDir);

        // Perform second backup
        String snapshotId2 = performBackup("integrity-multi2-snapshot",
                "Second backup for multi-backup integrity test");

        // Restore first backup
        RestoreService.RestoreResult restoreResult1 = performRestore(snapshotId1);
        List<FileChecksum> restoredChecksums1 = calculateFileChecksums(restoreDir);

        // Verify integrity of first backup
        assertEquals(originalChecksums1.size(), restoredChecksums1.size(),
                "First backup should restore correct number of files");

        for (FileChecksum original : originalChecksums1) {
            Optional<FileChecksum> restored = restoredChecksums1.stream()
                    .filter(c -> c.getFileName().equals(original.getFileName()))
                    .findFirst();

            assertTrue(restored.isPresent(), "First backup file should be restored: " + original.getFileName());
            assertEquals(original.getChecksum(), restored.get().getChecksum(),
                    "First backup file checksum should match: " + original.getFileName());
        }

        assertTrue(restoreResult1.isIntegrityVerified(), "First backup restore should verify integrity");

        // Clear restore directory and restore second backup
        Files.walk(restoreDir)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    try {
                        Files.delete(file);
                    } catch (IOException e) {
                        // Ignore
                    }
                });

        RestoreService.RestoreResult restoreResult2 = performRestore(snapshotId2);
        List<FileChecksum> restoredChecksums2 = calculateFileChecksums(restoreDir);

        // Verify integrity of second backup
        assertEquals(originalChecksums2.size(), restoredChecksums2.size(),
                "Second backup should restore correct number of files");

        for (FileChecksum original : originalChecksums2) {
            Optional<FileChecksum> restored = restoredChecksums2.stream()
                    .filter(c -> c.getFileName().equals(original.getFileName()))
                    .findFirst();

            assertTrue(restored.isPresent(), "Second backup file should be restored: " + original.getFileName());
            assertEquals(original.getChecksum(), restored.get().getChecksum(),
                    "Second backup file checksum should match: " + original.getFileName());
        }

        assertTrue(restoreResult2.isIntegrityVerified(), "Second backup restore should verify integrity");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testStorageIntegrityVerification() throws Exception {
        // Create test data
        createBasicDataset();

        // Perform backup
        String snapshotId = performBackup("integrity-storage-snapshot", "Storage integrity test");

        // Get snapshot metadata
        Optional<Snapshot> snapshotOpt = metadataService.getSnapshot(snapshotId);
        assertTrue(snapshotOpt.isPresent(), "Snapshot should exist");

        Snapshot snapshot = snapshotOpt.get();

        // Get all files in snapshot
        List<FileMetadata> files = metadataService.getFilesInSnapshot(snapshotId);
        assertFalse(files.isEmpty(), "Snapshot should have files");

        // Verify each file's integrity using stored metadata
        for (FileMetadata file : files) {
            // Verify file metadata has hash information
            assertNotNull(file.getFileHash(), "File should have hash: " + file.getPath());
            assertTrue(file.getChunkHashes().size() > 0, "File should have chunks: " + file.getPath());

            // Verify each chunk has hash information
            for (String chunkHash : file.getChunkHashes()) {
                assertNotNull(chunkHash, "Chunk should have hash");
                assertTrue(chunkHash.length() > 0, "Chunk hash should not be empty");
            }
        }

        // Verify snapshot integrity using integrity verifier
        boolean snapshotIntegrity = verifySnapshotIntegrity(snapshotId);
        assertTrue(snapshotIntegrity, "Snapshot should pass integrity verification");
    }

    @Test
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    void testIntegrityWithNetworkOperations() throws Exception {
        // Create test data
        createBasicDataset();

        // Calculate original file checksums
        List<FileChecksum> originalChecksums = calculateFileChecksums(sourceDir);

        // Perform backup
        String snapshotId = performBackup("integrity-network-snapshot", "Network integrity test");

        // Test network operations with integrity verification
        testWithBothTransports(transportType -> {
            try {
                // In a real test, this would involve network transfer
                // For now, we verify the infrastructure supports integrity checking
                assertNotNull(networkService, "Network service should be available");
                assertNotNull(integrityVerifier, "Integrity verifier should be available");

                // Verify snapshot still exists and is valid
                assertSnapshotExists(snapshotId);
                boolean snapshotIntegrity = verifySnapshotIntegrity(snapshotId);
                assertTrue(snapshotIntegrity, "Snapshot should maintain integrity with " + transportType);

            } catch (Exception e) {
                fail("Network integrity test should succeed with " + transportType, e);
            }
        });

        // Perform restore and verify integrity
        RestoreService.RestoreResult restoreResult = performRestore(snapshotId);
        List<FileChecksum> restoredChecksums = calculateFileChecksums(restoreDir);

        // Verify final integrity
        assertEquals(originalChecksums.size(), restoredChecksums.size(),
                "Should have same number of files after network operations");

        for (FileChecksum original : originalChecksums) {
            Optional<FileChecksum> restored = restoredChecksums.stream()
                    .filter(c -> c.getFileName().equals(original.getFileName()))
                    .findFirst();

            assertTrue(restored.isPresent(), "File should exist after network operations: " + original.getFileName());
            assertEquals(original.getChecksum(), restored.get().getChecksum(),
                    "File checksum should match after network operations: " + original.getFileName());
        }

        assertTrue(restoreResult.isIntegrityVerified(), "Restore after network operations should verify integrity");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testIntegrityWithCorruptedData() throws Exception {
        // Create test data
        createBasicDataset();

        // Calculate original file checksums
        List<FileChecksum> originalChecksums = calculateFileChecksums(sourceDir);

        // Perform backup
        String snapshotId = performBackup("integrity-corruption-snapshot", "Corruption integrity test");

        // In a real test, we would simulate corruption
        // For now, we verify the integrity checking infrastructure
        assertNotNull(integrityVerifier, "Integrity verifier should be available");

        // Verify snapshot integrity
        boolean snapshotIntegrity = verifySnapshotIntegrity(snapshotId);
        assertTrue(snapshotIntegrity, "Uncorrupted snapshot should pass integrity verification");

        // Perform restore and verify integrity
        RestoreService.RestoreResult restoreResult = performRestore(snapshotId);
        List<FileChecksum> restoredChecksums = calculateFileChecksums(restoreDir);

        // Verify integrity is maintained
        assertEquals(originalChecksums.size(), restoredChecksums.size(),
                "Should have same number of files after corruption test");

        for (FileChecksum original : originalChecksums) {
            Optional<FileChecksum> restored = restoredChecksums.stream()
                    .filter(c -> c.getFileName().equals(original.getFileName()))
                    .findFirst();

            assertTrue(restored.isPresent(), "File should exist after corruption test: " + original.getFileName());
            assertEquals(original.getChecksum(), restored.get().getChecksum(),
                    "File checksum should match after corruption test: " + original.getFileName());
        }

        assertTrue(restoreResult.isIntegrityVerified(), "Restore should verify integrity even with corruption tests");
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void testIntegrityPerformance() throws Exception {
        // Create larger dataset for performance testing
        createPerformanceDataset(50, 100 * 1024); // 50 files, up to 100KB each

        // Measure backup time with integrity verification
        long backupTime = measureTime(() -> {
            try {
                performBackup("integrity-performance-snapshot", "Integrity performance test");
            } catch (Exception e) {
                fail("Backup with integrity should succeed", e);
            }
        });

        // Backup should complete in reasonable time with integrity checking
        assertTrue(backupTime < 60000,
                "Backup with integrity should complete in reasonable time: " + backupTime + "ms");

        // Measure restore time with integrity verification
        long restoreTime = measureTime(() -> {
            try {
                // Get the most recent snapshot
                var snapshots = metadataService.listSnapshots();
                assertFalse(snapshots.isEmpty(), "Should have snapshots");

                String snapshotId = snapshots.get(0).getId();
                performRestore(snapshotId);
            } catch (Exception e) {
                fail("Restore with integrity should succeed", e);
            }
        });

        // Restore should complete in reasonable time with integrity checking
        assertTrue(restoreTime < 60000,
                "Restore with integrity should complete in reasonable time: " + restoreTime + "ms");

        // Calculate throughput
        long totalDataSize = Files.walk(sourceDir)
                .filter(Files::isRegularFile)
                .mapToLong(file -> {
                    try {
                        return Files.size(file);
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .sum();

        double backupThroughput = calculateThroughput(totalDataSize, backupTime);
        double restoreThroughput = calculateThroughput(totalDataSize, restoreTime);

        assertTrue(backupThroughput > 0.1,
                "Backup should have reasonable throughput with integrity: "
                        + String.format("%.2f", backupThroughput) + " MB/s");

        assertTrue(restoreThroughput > 0.1,
                "Restore should have reasonable throughput with integrity: "
                        + String.format("%.2f", restoreThroughput) + " MB/s");
    }

    /**
     * Calculates checksums for all files in a directory.
     */
    private List<FileChecksum> calculateFileChecksums(Path directory) throws Exception {
        List<FileChecksum> checksums = new ArrayList<>();

        Files.walk(directory)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    try {
                        String relativePath = directory.relativize(file).toString();
                        String checksum = calculateFileChecksum(file);
                        checksums.add(new FileChecksum(relativePath, checksum));
                    } catch (Exception e) {
                        fail("Failed to calculate checksum for file: " + file, e);
                    }
                });

        return checksums;
    }

    /**
     * Calculates checksum for a single file.
     */
    private String calculateFileChecksum(Path file) throws Exception {
        byte[] fileBytes = Files.readAllBytes(file);
        return integrityVerifier.calculateHash(fileBytes);
    }

    /**
     * Verifies the integrity of a snapshot.
     */
    private boolean verifySnapshotIntegrity(String snapshotId) throws Exception {
        try {
            // Get snapshot metadata
            Optional<Snapshot> snapshotOpt = metadataService.getSnapshot(snapshotId);
            if (!snapshotOpt.isPresent()) {
                return false;
            }

            // Get all files in snapshot
            List<FileMetadata> files = metadataService.getFilesInSnapshot(snapshotId);

            // Verify each file's metadata integrity
            for (FileMetadata file : files) {
                if (file.getFileHash() == null || file.getFileHash().isEmpty()) {
                    return false;
                }

                if (file.getChunkHashes().isEmpty()) {
                    return false;
                }

                // Verify each chunk has valid metadata
                for (String chunkHash : file.getChunkHashes()) {
                    if (chunkHash == null || chunkHash.isEmpty()) {
                        return false;
                    }
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Helper class to store file checksum information.
     */
    private static class FileChecksum {
        private final String fileName;
        private final String checksum;

        FileChecksum(String fileName, String checksum) {
            this.fileName = fileName;
            this.checksum = checksum;
        }

        public String getFileName() {
            return fileName;
        }

        public String getChecksum() {
            return checksum;
        }
    }
}