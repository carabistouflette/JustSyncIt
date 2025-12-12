package com.justsyncit.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BackupProgressTrackerTest {

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testNoOpImplementation() {
        BackupProgressTracker tracker = BackupProgressTracker.NO_OP;
        assertNotNull(tracker);

        // Verify that methods can be called without exception
        assertDoesNotThrow(() -> tracker.startBackup(Paths.get("."), "snap"));
        assertDoesNotThrow(() -> tracker.updateProgress(1, 10, 100, 1000));
        assertDoesNotThrow(() -> tracker.completeBackup());
        assertDoesNotThrow(() -> tracker.errorBackup(new RuntimeException("test")));
        assertDoesNotThrow(() -> tracker.fileProcessed(Paths.get("file.txt")));
        assertDoesNotThrow(() -> tracker.fileError(Paths.get("file.txt"), "error"));
        assertDoesNotThrow(() -> tracker.fileSkipped(Paths.get("file.txt"), "skipped"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testDefaultMethods() {
        // Create an anonymous class that only implements required methods
        BackupProgressTracker tracker = new BackupProgressTracker() {
            @Override
            public void startBackup(Path sourceDir, String snapshotName) {
            }

            @Override
            public void updateProgress(long filesProcessed, long totalFiles, long bytesProcessed, long totalBytes) {
            }

            @Override
            public void completeBackup() {
            }

            @Override
            public void errorBackup(Throwable error) {
            }
        };

        // Verify default methods do nothing and don't throw
        assertDoesNotThrow(() -> tracker.fileProcessed(Paths.get("file.txt")));
        assertDoesNotThrow(() -> tracker.fileError(Paths.get("file.txt"), "error"));
        assertDoesNotThrow(() -> tracker.fileSkipped(Paths.get("file.txt"), "skipped"));
    }
}
