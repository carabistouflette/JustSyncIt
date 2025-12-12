package com.justsyncit.backup;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleBackupProgressTrackerTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private ConsoleBackupProgressTracker tracker;

    @BeforeEach
    void setUp() {
        System.setOut(new PrintStream(outContent));
        tracker = new ConsoleBackupProgressTracker();
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testStartBackup() {
        Path source = Paths.get("/tmp/source");
        String snapshot = "snap1";
        tracker.startBackup(source, snapshot);

        String output = outContent.toString();
        assertTrue(output.contains("Starting backup operation..."));
        assertTrue(output.contains("Source: /tmp/source"));
        assertTrue(output.contains("Snapshot: snap1"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testUpdateProgress() {
        tracker.startBackup(Paths.get("."), "snap");
        outContent.reset();

        // Force update by setting totalFiles to match processed
        tracker.updateProgress(10, 10, 1000, 1000);

        // Since we have rate limiting, we might need to wait or force it.
        // The current implementation of updateProgress calls tryPrintProgress(false).
        // If it's the first call, it should print because lastPrintTime is 0.

        String output = outContent.toString();
        // It uses \r so we check if it contains "Progress:"
        assertTrue(output.contains("Progress:"));
        assertTrue(output.contains("10/10 files"));
        assertTrue(output.contains("100.0%"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testCompleteBackup() {
        tracker.startBackup(Paths.get("."), "snap");
        outContent.reset();

        tracker.completeBackup();

        String output = outContent.toString();
        assertTrue(output.contains("Backup completed successfully!"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testDivisionByZeroProtection() {
        tracker.startBackup(Paths.get("."), "snap");
        outContent.reset();

        // 0 files, 0 bytes
        tracker.updateProgress(0, 0, 0, 0);

        String output = outContent.toString();
        assertTrue(output.contains("0/0 files (0.0%)"));
        assertTrue(output.contains("0 B/0 B bytes (0.0%)"));
        assertFalse(output.contains("NaN"));
        assertFalse(output.contains("Infinity"));
    }
}
