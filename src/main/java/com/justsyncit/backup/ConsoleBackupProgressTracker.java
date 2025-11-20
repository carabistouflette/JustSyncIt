package com.justsyncit.backup;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Console-based progress tracker for backup operations.
 */
public class ConsoleBackupProgressTracker implements BackupProgressTracker {

    private static final Logger LOGGER = Logger.getLogger(ConsoleBackupProgressTracker.class.getName());

    private final AtomicLong filesProcessed = new AtomicLong(0);
    private final AtomicLong bytesProcessed = new AtomicLong(0);
    private final AtomicLong totalFiles = new AtomicLong(0);
    private final AtomicLong totalBytes = new AtomicLong(0);
    private volatile boolean started = false;

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

        if (filesProcessed % 10 == 0 || filesProcessed == totalFiles) {
            printProgress();
        }
    }

    @Override
    public void completeBackup() {
        if (!started) {
            return;
        }

        printProgress();
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
        long processed = filesProcessed.incrementAndGet();
        long bytes = bytesProcessed.addAndGet(file.toFile().length());

        if (processed % 10 == 0 || processed == totalFiles.get()) {
            printProgress();
        }
    }

    @Override
    public void fileError(Path file, String reason) {
        LOGGER.warning(String.format("Error processing file: %s - %s", file, reason));
    }

    @Override
    public void fileSkipped(Path file, String reason) {
        LOGGER.info(String.format("Skipped file: %s - %s", file, reason));
    }

    private void printProgress() {
        double filePercent = (double) filesProcessed.get() / totalFiles.get() * 100.0;
        double bytePercent = (double) bytesProcessed.get() / totalBytes.get() * 100.0;

        System.out.printf("\rProgress: %d/%d files (%.1f%%) | %s/%s bytes (%.1f%%)",
                filesProcessed.get(), totalFiles.get(), filePercent,
                formatBytes(bytesProcessed.get()), formatBytes(totalBytes.get()), bytePercent);
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