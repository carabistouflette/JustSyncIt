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

package com.justsyncit.restore;

import com.justsyncit.storage.metadata.Snapshot;
import java.io.PrintStream;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Console-based implementation of RestoreProgressTracker.
 * Provides progress updates to the console during restore operations.
 * Follows Single Responsibility Principle by focusing only on console output.
 */
@SuppressFBWarnings({ "EI_EXPOSE_REP2", "EI_EXPOSE_REP" })
public class ConsoleRestoreProgressTracker implements RestoreProgressTracker {

    private final PrintStream out;
    private long startTime;
    private long lastUpdate;

    /**
     * Creates a new ConsoleRestoreProgressTracker that writes to System.out.
     */
    public ConsoleRestoreProgressTracker() {
        this(System.out);
    }

    /**
     * Creates a new ConsoleRestoreProgressTracker that writes to the specified
     * PrintStream.
     *
     * @param out the PrintStream to write progress updates to
     */
    public ConsoleRestoreProgressTracker(PrintStream out) {
        this.out = out;
    }

    @Override
    public void startRestore(Snapshot snapshot, java.nio.file.Path targetDirectory) {
        startTime = System.currentTimeMillis();
        lastUpdate = startTime;

        out.println();
        out.println("Starting restore operation...");
        out.println("  Source snapshot: " + snapshot.getId());
        out.println("  Target directory: " + targetDirectory);
        out.println("  Files to restore: " + snapshot.getTotalFiles());
        out.println("  Total size: " + formatBytes(snapshot.getTotalSize()));
        out.println();
    }

    @Override
    public void updateProgress(long filesProcessed, long totalFiles, long bytesProcessed,
            long totalBytes, String currentFile) {
        long now = System.currentTimeMillis();

        // Update progress every 500ms to avoid console spam
        if (now - lastUpdate < 500 && filesProcessed < totalFiles) {
            return;
        }

        lastUpdate = now;

        // Calculate progress percentages
        double fileProgress = totalFiles > 0 ? (double) filesProcessed / totalFiles : 0.0;
        double byteProgress = totalBytes > 0 ? (double) bytesProcessed / totalBytes : 0.0;

        // Calculate estimated time remaining
        long elapsed = now - startTime;
        long eta = filesProcessed > 0 ? (long) ((elapsed / (double) filesProcessed) * (totalFiles - filesProcessed))
                : 0;

        // Build progress bar
        int barWidth = 40;
        int filled = (int) (fileProgress * barWidth);
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < barWidth; i++) {
            bar.append(i < filled ? "=" : i == filled ? ">" : " ");
        }
        bar.append("]");

        // Print progress
        out.printf("\r%s %d/%d files (%.1f%%) %s %s/%s (%.1f%%) ETA: %s",
                bar,
                filesProcessed, totalFiles, fileProgress * 100,
                currentFile != null ? "Current: " + currentFile : "",
                formatBytes(bytesProcessed), formatBytes(totalBytes), byteProgress * 100,
                formatDuration(eta));

        out.flush();
    }

    @Override
    public void completeRestore(RestoreService.RestoreResult result) {
        out.println();
        out.println();
        out.println("Restore completed successfully!");
        out.println("  Files restored: " + result.getFilesRestored());
        out.println("  Files skipped: " + result.getFilesSkipped());
        out.println("  Bytes restored: " + formatBytes(result.getTotalBytesRestored()));
        out.println("  Duration: " + formatDuration(result.getDuration()));
        out.println();
    }

    @Override
    public void errorRestore(Throwable error) {
        out.println();
        out.println();
        out.println("Restore failed: " + error.getMessage());
        if (error.getCause() != null) {
            out.println("Caused by: " + error.getCause().getMessage());
        }
        out.println();
    }

    @Override
    public void fileSkipped(String file, String reason) {
        out.println("Skipped: " + file + " (" + reason + ")");
    }

    @Override
    public void fileError(String file, Throwable error) {
        out.println("Error restoring " + file + ": " + error.getMessage());
    }

    /**
     * Formats a byte count as a human-readable string.
     */
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

    /**
     * Formats a duration in milliseconds as a human-readable string.
     */
    private String formatDuration(long millis) {
        if (millis < 1000) {
            return millis + " ms";
        } else if (millis < 60 * 1000) {
            return String.format("%.1f s", millis / 1000.0);
        } else if (millis < 60 * 60 * 1000) {
            long minutes = millis / (60 * 1000);
            long seconds = (millis % (60 * 1000)) / 1000;
            return String.format("%d:%02d", minutes, seconds);
        } else {
            long hours = millis / (60 * 60 * 1000);
            long minutes = (millis % (60 * 60 * 1000)) / (60 * 1000);
            return String.format("%d:%02d h", hours, minutes);
        }
    }
}