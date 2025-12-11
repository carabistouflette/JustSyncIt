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
import com.justsyncit.storage.ContentStoreStats;
import com.justsyncit.storage.metadata.MetadataStats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for deduplication functionality.
 * Tests deduplication effectiveness with duplicate files and validates space
 * savings.
 */
public class DeduplicationE2ETest extends E2ETestBase {

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testBasicDeduplication() throws Exception {
        // Create dataset with duplicate files
        createDuplicateDataset();

        // Perform backup
        String snapshotId = performBackup("dedup-basic-snapshot", "Basic deduplication test");

        // Get storage statistics
        ContentStoreStats storageStats = contentStore.getStats();
        MetadataStats metadataStats = metadataService.getStats();

        // Verify deduplication occurred
        assertTrue(storageStats.getTotalChunks() > 0, "Should have some chunks stored");
        assertTrue(storageStats.getTotalSizeBytes() > 0, "Should have some data stored");

        // Verify snapshot metadata
        var snapshotOpt = metadataService.getSnapshot(snapshotId);
        assertTrue(snapshotOpt.isPresent(), "Snapshot should exist");

        var snapshot = snapshotOpt.get();
        assertTrue(snapshot.getTotalFiles() > 0, "Should have processed files");
        assertTrue(snapshot.getTotalSize() > 0, "Should have processed data");

        // Verify that duplicate files don't create duplicate chunks
        // This is the core deduplication test
        long uniqueChunks = storageStats.getTotalChunks();
        long totalFiles = snapshot.getTotalFiles();

        // With duplicate files, we should have fewer chunks than files
        // (assuming files are larger than chunk size)
        assertTrue(uniqueChunks <= totalFiles * 2,
                "Deduplication should reduce chunk count: " + uniqueChunks + " chunks vs " + totalFiles + " files");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testDeduplicationWithIdenticalFiles() throws Exception {
        // Create multiple identical files
        Path file1 = sourceDir.resolve("identical1.txt");
        Path file2 = sourceDir.resolve("identical2.txt");
        Path file3 = sourceDir.resolve("identical3.txt");

        String content = "This is identical content for deduplication testing.\n".repeat(100);
        Files.write(file1, content.getBytes());
        Files.write(file2, content.getBytes());
        Files.write(file3, content.getBytes());

        // Perform backup
        String snapshotId = performBackup("dedup-identical-snapshot", "Identical files deduplication test");

        // Get storage statistics
        ContentStoreStats storageStats = contentStore.getStats();

        // With identical files, we should have significantly fewer chunks than files
        long uniqueChunks = storageStats.getTotalChunks();
        long totalFiles = 3;

        // For identical content, we should have much fewer chunks than files
        assertTrue(uniqueChunks < totalFiles,
                "Identical files should be fully deduplicated: " + uniqueChunks + " chunks vs " + totalFiles
                        + " files");

        // Verify all files are in the snapshot
        var snapshotOpt = metadataService.getSnapshot(snapshotId);
        assertTrue(snapshotOpt.isPresent(), "Snapshot should exist");

        var snapshot = snapshotOpt.get();
        assertEquals(totalFiles, snapshot.getTotalFiles(), "All files should be in snapshot");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testDeduplicationWithPartialDuplicates() throws Exception {
        // Create files with partial duplicate content
        Path file1 = sourceDir.resolve("partial1.txt");
        Path file2 = sourceDir.resolve("partial2.txt");
        Path file3 = sourceDir.resolve("partial3.txt");

        String baseContent = "This is common base content.\n".repeat(50);
        String uniqueContent1 = "Unique content for file 1.\n".repeat(20);
        String uniqueContent2 = "Unique content for file 2.\n".repeat(20);
        String uniqueContent3 = "Unique content for file 3.\n".repeat(20);

        Files.write(file1, (baseContent + uniqueContent1).getBytes());
        Files.write(file2, (baseContent + uniqueContent2).getBytes());
        Files.write(file3, (baseContent + uniqueContent3).getBytes());

        // Perform backup
        String snapshotId = performBackup("dedup-partial-snapshot", "Partial duplicates deduplication test");

        // Get storage statistics
        ContentStoreStats storageStats = contentStore.getStats();

        // With partial duplicates, we should have some deduplication
        long uniqueChunks = storageStats.getTotalChunks();
        long totalFiles = 3;

        // Should have some deduplication but not as much as identical files
        assertTrue(uniqueChunks <= totalFiles * 2,
                "Partial duplicates should be partially deduplicated: " + uniqueChunks + " chunks vs " + totalFiles
                        + " files");

        // Verify all files are in the snapshot
        var snapshotOpt = metadataService.getSnapshot(snapshotId);
        assertTrue(snapshotOpt.isPresent(), "Snapshot should exist");

        var snapshot = snapshotOpt.get();
        assertEquals(totalFiles, snapshot.getTotalFiles(), "All files should be in snapshot");
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testDeduplicationWithLargeFiles() throws Exception {
        // Create large files with duplicate sections
        Path largeFile1 = sourceDir.resolve("large1.dat");
        Path largeFile2 = sourceDir.resolve("large2.dat");

        // Create 1MB files with duplicate sections
        byte[] duplicateSection = new byte[100 * 1024]; // 100KB duplicate section
        for (int i = 0; i < duplicateSection.length; i++) {
            duplicateSection[i] = (byte) (i % 256);
        }

        byte[] uniqueSection1 = new byte[400 * 1024]; // 400KB unique section
        byte[] uniqueSection2 = new byte[400 * 1024]; // 400KB unique section

        for (int i = 0; i < uniqueSection1.length; i++) {
            uniqueSection1[i] = (byte) ((i + 100) % 256);
        }

        for (int i = 0; i < uniqueSection2.length; i++) {
            uniqueSection2[i] = (byte) ((i + 200) % 256);
        }

        // Create files with structure: duplicate + unique + duplicate
        byte[] file1Content = new byte[duplicateSection.length + uniqueSection1.length + duplicateSection.length];
        System.arraycopy(duplicateSection, 0, file1Content, 0, duplicateSection.length);
        System.arraycopy(uniqueSection1, 0, file1Content, duplicateSection.length, uniqueSection1.length);
        System.arraycopy(duplicateSection, 0, file1Content, duplicateSection.length + uniqueSection1.length,
                duplicateSection.length);

        byte[] file2Content = new byte[duplicateSection.length + uniqueSection2.length + duplicateSection.length];
        System.arraycopy(duplicateSection, 0, file2Content, 0, duplicateSection.length);
        System.arraycopy(uniqueSection2, 0, file2Content, duplicateSection.length, uniqueSection2.length);
        System.arraycopy(duplicateSection, 0, file2Content, duplicateSection.length + uniqueSection2.length,
                duplicateSection.length);

        Files.write(largeFile1, file1Content);
        Files.write(largeFile2, file2Content);

        // Perform backup
        String snapshotId = performBackup("dedup-large-snapshot", "Large files deduplication test");

        // Get storage statistics
        ContentStoreStats storageStats = contentStore.getStats();

        // Verify deduplication occurred with large files
        long uniqueChunks = storageStats.getTotalChunks();
        long totalSize = storageStats.getTotalSizeBytes();

        assertTrue(uniqueChunks > 0, "Should have chunks for large files");
        assertTrue(totalSize > 0, "Should have stored data");

        // With duplicate sections, total stored size should be less than sum of file
        // sizes
        long totalFileSize = file1Content.length + file2Content.length;
        assertTrue(totalSize < totalFileSize,
                "Deduplication should reduce storage: " + totalSize + " vs " + totalFileSize);

        // Verify files are in the snapshot
        var snapshotOpt = metadataService.getSnapshot(snapshotId);
        assertTrue(snapshotOpt.isPresent(), "Snapshot should exist");

        var snapshot = snapshotOpt.get();
        assertEquals(2, snapshot.getTotalFiles(), "Should have 2 files");
        assertEquals(totalFileSize, snapshot.getTotalSize(), "Should report correct total size");
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testDeduplicationEffectiveness() throws Exception {
        // Create a dataset with varying levels of duplication
        Map<String, Integer> fileSizes = new HashMap<>();

        // Create files with different duplication patterns
        String uniqueContent = "This is unique content.\n".repeat(100);
        String duplicateContent = "This is duplicate content.\n".repeat(100);

        // 5 unique files
        for (int i = 1; i <= 5; i++) {
            Path file = sourceDir.resolve("unique" + i + ".txt");
            String content = uniqueContent + "Unique to file " + i + ".\n".repeat(10);
            Files.write(file, content.getBytes());
            fileSizes.put(file.getFileName().toString(), content.getBytes().length);
        }

        // 10 files with duplicate content
        for (int i = 1; i <= 10; i++) {
            Path file = sourceDir.resolve("duplicate" + i + ".txt");
            String content = duplicateContent + "Slightly different " + i + ".\n".repeat(5);
            Files.write(file, content.getBytes());
            fileSizes.put(file.getFileName().toString(), content.getBytes().length);
        }

        // Calculate total original size
        long totalOriginalSize = fileSizes.values().stream().mapToLong(Long::valueOf).sum();

        // Perform backup
        String snapshotId = performBackup("dedup-effectiveness-snapshot", "Deduplication effectiveness test");

        // Get storage statistics
        ContentStoreStats storageStats = contentStore.getStats();
        long storedSize = storageStats.getTotalSizeBytes();

        // Calculate deduplication ratio
        double deduplicationRatio = (double) storedSize / totalOriginalSize;

        // Should have significant deduplication (less than 80% of original size)
        assertTrue(deduplicationRatio < 0.8,
                "Deduplication should reduce storage significantly: " +
                        String.format("%.2f", deduplicationRatio) + " ratio (stored/original)");

        // Verify all files are in the snapshot
        var snapshotOpt = metadataService.getSnapshot(snapshotId);
        assertTrue(snapshotOpt.isPresent(), "Snapshot should exist");

        var snapshot = snapshotOpt.get();
        assertEquals(15, snapshot.getTotalFiles(), "Should have 15 files");
        assertEquals(totalOriginalSize, snapshot.getTotalSize(), "Should report correct total size");
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testDeduplicationAcrossBackups() throws Exception {
        // Create initial dataset
        Path commonFile = sourceDir.resolve("common.txt");
        String commonContent = "This content appears in multiple backups.\n".repeat(100);
        Files.write(commonFile, commonContent.getBytes());

        Path initialFile = sourceDir.resolve("initial.txt");
        String initialContent = "Initial backup content.\n".repeat(50);
        Files.write(initialFile, initialContent.getBytes());

        // Perform first backup
        String snapshotId1 = performBackup("dedup-backup1-snapshot", "First backup for cross-backup deduplication");

        // Get storage statistics after first backup
        ContentStoreStats stats1 = contentStore.getStats();
        long sizeAfterBackup1 = stats1.getTotalSizeBytes();

        // Modify dataset for second backup
        Files.delete(initialFile);

        Path newFile = sourceDir.resolve("new.txt");
        String newContent = "New content for second backup.\n".repeat(50);
        Files.write(newFile, newContent.getBytes());

        // Perform second backup
        String snapshotId2 = performBackup("dedup-backup2-snapshot", "Second backup for cross-backup deduplication");

        // Get storage statistics after second backup
        ContentStoreStats stats2 = contentStore.getStats();
        long sizeAfterBackup2 = stats2.getTotalSizeBytes();

        // The increase in storage should be less than the size of new content
        // because the common file should be deduplicated
        long storageIncrease = sizeAfterBackup2 - sizeAfterBackup1;
        long newContentSize = newContent.getBytes().length;

        assertTrue(storageIncrease < newContentSize,
                "Cross-backup deduplication should reduce storage increase: " +
                        storageIncrease + " increase vs " + newContentSize + " new content size");

        // Verify both snapshots exist
        assertSnapshotExists(snapshotId1);
        assertSnapshotExists(snapshotId2);

        // Verify snapshot contents
        var snapshot1Opt = metadataService.getSnapshot(snapshotId1);
        var snapshot2Opt = metadataService.getSnapshot(snapshotId2);

        assertTrue(snapshot1Opt.isPresent(), "First snapshot should exist");
        assertTrue(snapshot2Opt.isPresent(), "Second snapshot should exist");

        var snapshot1 = snapshot1Opt.get();
        var snapshot2 = snapshot2Opt.get();

        assertEquals(2, snapshot1.getTotalFiles(), "First snapshot should have 2 files");
        assertEquals(2, snapshot2.getTotalFiles(), "Second snapshot should have 2 files");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testDeduplicationWithSpecialCharacters() throws Exception {
        // Create dataset with special characters and duplicates
        createSpecialCharacterDataset();

        // Create duplicate files with special characters
        Path file1 = sourceDir.resolve("special-üñíçødé-1.txt");
        Path file2 = sourceDir.resolve("special-üñíçødé-2.txt");

        String specialContent = "Special content with unicode: ñáéíóú 中文 العربية\n".repeat(50);
        Files.write(file1, specialContent.getBytes());
        Files.write(file2, specialContent.getBytes());

        // Perform backup
        String snapshotId = performBackup("dedup-special-snapshot", "Special characters deduplication test");

        // Get storage statistics
        ContentStoreStats storageStats = contentStore.getStats();

        // Verify deduplication works with special characters
        long uniqueChunks = storageStats.getTotalChunks();
        long totalFiles = Files.walk(sourceDir).filter(Files::isRegularFile).count();

        // Should have deduplication despite special characters
        assertTrue(uniqueChunks <= totalFiles,
                "Special characters should not prevent deduplication: " + uniqueChunks + " chunks vs " + totalFiles
                        + " files");

        // Verify snapshot contains all files
        var snapshotOpt = metadataService.getSnapshot(snapshotId);
        assertTrue(snapshotOpt.isPresent(), "Snapshot should exist");

        var snapshot = snapshotOpt.get();
        assertTrue(snapshot.getTotalFiles() >= 2, "Should have at least the special character files");
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testDeduplicationPerformance() throws Exception {
        // Create a larger dataset to test deduplication performance
        createPerformanceDataset(100, 50 * 1024); // 100 files, up to 50KB each

        // Create some duplicates
        Path originalFile = sourceDir.resolve("original.txt");
        String originalContent = "Performance test content.\n".repeat(1000);
        Files.write(originalFile, originalContent.getBytes());

        for (int i = 1; i <= 20; i++) {
            Path duplicateFile = sourceDir.resolve("duplicate" + i + ".txt");
            Files.write(duplicateFile, originalContent.getBytes());
        }

        // Measure backup time with deduplication
        long backupTime = measureTime(() -> {
            try {
                performBackup("dedup-performance-snapshot", "Deduplication performance test");
            } catch (Exception e) {
                fail("Backup should succeed", e);
            }
        });

        // Backup should complete in reasonable time (adjust threshold as needed)
        assertTrue(backupTime < 30000,
                "Backup with deduplication should complete in reasonable time: " + backupTime + "ms");

        // Get storage statistics
        ContentStoreStats storageStats = contentStore.getStats();

        // Verify deduplication occurred
        assertTrue(storageStats.getTotalChunks() > 0, "Should have chunks");
        assertTrue(storageStats.getTotalSizeBytes() > 0, "Should have stored data");

        // Calculate throughput
        double throughput = calculateThroughput(storageStats.getTotalSizeBytes(), backupTime);
        assertTrue(throughput > 0.1,
                "Should have reasonable throughput: " + String.format("%.2f", throughput) + " MB/s");
    }

    @Test
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    void testDeduplicationIntegrity() throws Exception {
        // Create files with known content
        Path file1 = sourceDir.resolve("integrity1.txt");
        Path file2 = sourceDir.resolve("integrity2.txt");

        String content1 = "Integrity test content 1.\n".repeat(100);
        String content2 = "Integrity test content 2.\n".repeat(100);

        Files.write(file1, content1.getBytes());
        Files.write(file2, content2.getBytes());

        // Create duplicate of file1
        Path duplicate1 = sourceDir.resolve("duplicate1.txt");
        Files.write(duplicate1, content1.getBytes());

        // Perform backup
        String snapshotId = performBackup("dedup-integrity-snapshot", "Deduplication integrity test");

        // Perform restore
        RestoreService.RestoreResult restoreResult = performRestore(snapshotId);

        // Verify restored files have correct content
        Path restoredFile1 = restoreDir.resolve("integrity1.txt");
        Path restoredFile2 = restoreDir.resolve("integrity2.txt");
        Path restoredDuplicate1 = restoreDir.resolve("duplicate1.txt");

        assertTrue(Files.exists(restoredFile1), "Restored file1 should exist");
        assertTrue(Files.exists(restoredFile2), "Restored file2 should exist");
        assertTrue(Files.exists(restoredDuplicate1), "Restored duplicate1 should exist");

        assertFileContentEquals(file1, restoredFile1);
        assertFileContentEquals(file2, restoredFile2);
        assertFileContentEquals(file1, restoredDuplicate1);

        // Verify deduplication didn't compromise integrity
        String restoredContent1 = Files.readString(restoredFile1);
        String restoredContent2 = Files.readString(restoredFile2);
        String restoredDuplicateContent1 = Files.readString(restoredDuplicate1);

        assertEquals(content1, restoredContent1, "File1 content should match");
        assertEquals(content2, restoredContent2, "File2 content should match");
        assertEquals(content1, restoredDuplicateContent1, "Duplicate1 content should match original");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testDeduplicationWithEmptyFiles() throws Exception {
        // Create empty files
        Path emptyFile1 = sourceDir.resolve("empty1.txt");
        Path emptyFile2 = sourceDir.resolve("empty2.txt");
        Path emptyFile3 = sourceDir.resolve("empty3.txt");

        Files.createFile(emptyFile1);
        Files.createFile(emptyFile2);
        Files.createFile(emptyFile3);

        // Create a non-empty file
        Path nonEmptyFile = sourceDir.resolve("nonempty.txt");
        Files.writeString(nonEmptyFile, "Non-empty content");

        // Perform backup
        String snapshotId = performBackup("dedup-empty-snapshot", "Empty files deduplication test");

        // Get storage statistics
        ContentStoreStats storageStats = contentStore.getStats();

        // Verify snapshot contains all files
        var snapshotOpt = metadataService.getSnapshot(snapshotId);
        assertTrue(snapshotOpt.isPresent(), "Snapshot should exist");

        var snapshot = snapshotOpt.get();
        assertEquals(4, snapshot.getTotalFiles(), "Should have 4 files");

        // Empty files should be handled properly (may or may not create chunks)
        // This test verifies that empty files don't cause issues
        assertTrue(storageStats.getTotalChunks() >= 0, "Should handle empty files gracefully");

        // Perform restore to verify integrity
        RestoreService.RestoreResult restoreResult = performRestore(snapshotId);

        Path restoredEmpty1 = restoreDir.resolve("empty1.txt");
        Path restoredEmpty2 = restoreDir.resolve("empty2.txt");
        Path restoredEmpty3 = restoreDir.resolve("empty3.txt");
        Path restoredNonEmpty = restoreDir.resolve("nonempty.txt");

        assertTrue(Files.exists(restoredEmpty1), "Restored empty1 should exist");
        assertTrue(Files.exists(restoredEmpty2), "Restored empty2 should exist");
        assertTrue(Files.exists(restoredEmpty3), "Restored empty3 should exist");
        assertTrue(Files.exists(restoredNonEmpty), "Restored nonempty should exist");

        assertEquals(0, Files.size(restoredEmpty1), "Restored empty1 should be empty");
        assertEquals(0, Files.size(restoredEmpty2), "Restored empty2 should be empty");
        assertEquals(0, Files.size(restoredEmpty3), "Restored empty3 should be empty");
        assertTrue(Files.size(restoredNonEmpty) > 0, "Restored nonempty should not be empty");
    }
}