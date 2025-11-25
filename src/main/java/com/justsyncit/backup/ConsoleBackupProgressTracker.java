package com.justsyncit.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import java.util.logging.Logger;

/**
 * Console-based progress tracker for backup operations.
 */
public class ConsoleBackupProgressTracker implements BackupProgressTracker {

    private static final Logger LOGGER = Logger.getLogger(ConsoleBackupProgressTracker.class.getName());
    private static final long PRINT_INTERVAL_MS = 100;

    private final AtomicLong filesProcessed = new AtomicLong(0);
    private final AtomicLong bytesProcessed = new AtomicLong(0);
    private final AtomicLong totalFiles = new AtomicLong(0);
    private final AtomicLong totalBytes = new AtomicLong(0);

    private volatile boolean started = false;
    private volatile long lastPrintTime = 0;

    @Override
    public void startBackup(Path sourceDir, String snapshotName) {
        this.started = true;

        System.out.println();
        System.out.println("Starting backup operation...");
        System.out.printf("  Source: %s%n", sourceDir);
        System.out.printf("  Snapshot: %s%n", snapshotName);
        System.out.println();
    }

    @Override
    public void updateProgress(long filesProcessed, long totalFiles, long bytesProcessed, long totalBytes) {
        this.filesProcessed.set(filesProcessed);
        this.bytesProcessed.set(bytesProcessed);
        this.totalFiles.set(totalFiles);
        this.totalBytes.set(totalBytes);

        tryPrintProgress(false);
    }

    @Override
    public void completeBackup() {
        if (!started) {
            return;
        }

        tryPrintProgress(true); // Force print
        System.out.println();
        System.out.println("Backup completed successfully!");
        System.out.printf("  Files processed: %d%n", filesProcessed.get());
        System.out.printf("  Bytes processed: %s%n", formatBytes(bytesProcessed.get()));
    }

    @Override
    public void errorBackup(Throwable error) {
        LOGGER.severe("Backup failed: " + error.getMessage());
    }

    @Override
    public void fileProcessed(Path file) {
        filesProcessed.incrementAndGet();
        long size = 0;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            // Fallback if we can't read size, though unlikely if we just processed it
            size = file.toFile().length();
        }
        bytesProcessed.addAndGet(size);

        tryPrintProgress(false);
    }

    @Override
    public void fileError(Path file, String reason) {
        LOGGER.warning(String.format("Error processing file: %s - %s", file, reason));
    }

    @Override
    public void fileSkipped(Path file, String reason) {
        LOGGER.info(String.format("Skipped file: %s - %s", file, reason));
    }

    private void tryPrintProgress(boolean force) {
        long now = System.currentTimeMillis();
        if (force || (now - lastPrintTime >= PRINT_INTERVAL_MS)) {
            synchronized (this) {
                if (force || (System.currentTimeMillis() - lastPrintTime >= PRINT_INTERVAL_MS)) {
                    printProgress();
                    lastPrintTime = System.currentTimeMillis();
                }
            }
        }
    }

    private void printProgress() {
        long currentFiles = filesProcessed.get();
        long currentTotalFiles = totalFiles.get();
        long currentBytes = bytesProcessed.get();
        long currentTotalBytes = totalBytes.get();

        double filePercent = currentTotalFiles > 0 ? (double) currentFiles / currentTotalFiles * 100.0 : 0.0;
        double bytePercent = currentTotalBytes > 0 ? (double) currentBytes / currentTotalBytes * 100.0 : 0.0;

        System.out.printf("\rProgress: %d/%d files (%.1f%%) | %s/%s bytes (%.1f%%)",
                currentFiles, currentTotalFiles, filePercent,
                formatBytes(currentBytes), formatBytes(currentTotalBytes), bytePercent);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}