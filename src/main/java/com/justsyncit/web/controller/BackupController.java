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

package com.justsyncit.web.controller;

import com.justsyncit.backup.BackupOptions;
import com.justsyncit.backup.BackupService;
import com.justsyncit.web.WebServer;
import com.justsyncit.web.WebServerContext;
import com.justsyncit.web.dto.ApiError;
import com.justsyncit.web.dto.BackupRequest;
import com.justsyncit.web.dto.BackupStatusResponse;

import io.javalin.http.Context;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * REST controller for backup operations.
 */
public final class BackupController {

    private static final Logger LOGGER = Logger.getLogger(BackupController.class.getName());

    private final WebServerContext context;
    private final WebServer webServer;
    private final AtomicReference<BackupState> currentBackup;
    private final List<BackupHistoryEntry> backupHistory;

    public BackupController(WebServerContext context, WebServer webServer) {
        this.context = context;
        this.webServer = webServer;
        this.currentBackup = new AtomicReference<>();
        this.backupHistory = new ArrayList<>();
    }

    /**
     * POST /api/backup - Start a new backup operation.
     */
    public void startBackup(Context ctx) {
        try {
            BackupRequest request = ctx.bodyAsClass(BackupRequest.class);

            // Validate request
            if (request.getSourcePath() == null || request.getSourcePath().isEmpty()) {
                ctx.status(400).json(ApiError.badRequest("Source path is required", ctx.path()));
                return;
            }

            Path sourcePath = Paths.get(request.getSourcePath());
            if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
                ctx.status(400).json(ApiError.badRequest("Source path must be an existing directory",
                        ctx.path()));
                return;
            }

            // Check if a backup is already running
            BackupState state = currentBackup.get();
            if (state != null && state.isRunning()) {
                ctx.status(409).json(ApiError.of(409, "Conflict",
                        "A backup is already in progress", ctx.path()));
                return;
            }

            // Create backup options using Builder pattern
            BackupOptions.Builder optionsBuilder = new BackupOptions.Builder()
                    .chunkSize(request.getChunkSize())
                    .includeHiddenFiles(request.isIncludeHidden())
                    .verifyIntegrity(request.isVerifyIntegrity());

            if (request.getSnapshotName() != null) {
                optionsBuilder.snapshotName(request.getSnapshotName());
            }
            if (request.getDescription() != null) {
                optionsBuilder.description(request.getDescription());
            }

            BackupOptions options = optionsBuilder.build();

            // Start backup asynchronously
            BackupState newState = new BackupState(request.getSnapshotName());
            currentBackup.set(newState);

            BackupService backupService = context.getBackupService();
            BackupOptions finalOptions = options;

            CompletableFuture.runAsync(() -> {
                try {
                    newState.setStatus("running");
                    webServer.broadcast("backup:started", Map.of("snapshotId", newState.getSnapshotId()));

                    // Use atomic long for thread-safe timestamp
                    java.util.concurrent.atomic.AtomicLong lastBroadcast = new java.util.concurrent.atomic.AtomicLong(
                            0);

                    // Set up event listener for detailed logs
                    backupService.setEventListener((type, level, message, file) -> {
                        webServer.broadcast("backup:event", Map.of(
                                "snapshotId", newState.getSnapshotId(),
                                "type", type,
                                "level", level,
                                "message", message,
                                "file", file != null ? file : ""));
                    });

                    // Use the overload with progress listener
                    BackupService.BackupResult result = backupService.backup(sourcePath, finalOptions, processor -> {
                        // Update state with live progress from processor
                        newState.setFilesProcessed(processor.getProcessedFilesCount());
                        newState.setBytesProcessed(processor.getProcessedBytesCount());
                        newState.setTotalFiles(processor.getDetectedFilesCount());
                        newState.setTotalBytes(processor.getTotalBytesCount());
                        newState.setCurrentFile(processor.getCurrentFile()); // Update current file in state
                        newState.setCurrentActivity(processor.getCurrentActivity()); // Update current activity in state

                        // Broadcast progress update (throttled to max 10/sec)
                        long now = System.currentTimeMillis();
                        if (now - lastBroadcast.get() > 100) {
                            lastBroadcast.set(now);
                            double progressPercent = processor.getProcessingPercentage();
                            webServer.broadcast("backup:progress", Map.of(
                                    "snapshotId", newState.getSnapshotId(),
                                    "filesProcessed", processor.getProcessedFilesCount(),
                                    "bytesProcessed", processor.getProcessedBytesCount(),
                                    "currentFile", processor.getCurrentFile() != null ? processor.getCurrentFile() : "",
                                    "currentActivity",
                                    processor.getCurrentActivity() != null ? processor.getCurrentActivity() : "", // Added
                                                                                                                  // currentActivity
                                    "progressPercent", progressPercent));
                        }
                    }).get();
                    if (result.isSuccess()) {
                        newState.setStatus("completed");
                        newState.setFilesProcessed(result.getFilesProcessed());
                        newState.setBytesProcessed(result.getTotalBytesProcessed());

                        backupHistory.add(0, new BackupHistoryEntry(
                                result.getSnapshotId(),
                                "completed",
                                result.getFilesProcessed(),
                                result.getTotalBytesProcessed(),
                                System.currentTimeMillis()));

                        webServer.broadcast("backup:completed", Map.of(
                                "snapshotId", result.getSnapshotId(),
                                "filesProcessed", result.getFilesProcessed(),
                                "bytesProcessed", result.getTotalBytesProcessed()));
                    } else {
                        newState.setStatus("failed");
                        newState.setError(result.getError());
                        webServer.broadcast("backup:failed", Map.of("error", result.getError()));
                    }
                } catch (Exception e) {
                    LOGGER.severe("Backup failed: " + e.getMessage());
                    newState.setStatus("failed");
                    newState.setError(e.getMessage());
                    webServer.broadcast("backup:failed", Map.of("error", e.getMessage()));
                }
            });

            ctx.status(202).json(Map.of(
                    "status", "accepted",
                    "message", "Backup started",
                    "snapshotId", newState.getSnapshotId()));

        } catch (Exception e) {
            LOGGER.severe("Failed to start backup: " + e.getMessage());
            ctx.status(500).json(ApiError.internalError(e.getMessage(), ctx.path()));
        }
    }

    /**
     * GET /api/backup/status - Get current backup status.
     */
    public void getStatus(Context ctx) {
        BackupState state = currentBackup.get();
        if (state == null) {
            ctx.json(BackupStatusResponse.idle());
        } else {
            BackupStatusResponse response = new BackupStatusResponse();
            response.setStatus(state.getStatus());
            response.setSnapshotId(state.getSnapshotId());
            response.setFilesProcessed(state.getFilesProcessed());
            response.setBytesProcessed(state.getBytesProcessed());
            response.setTotalFiles(state.getTotalFiles());
            response.setTotalBytes(state.getTotalBytes());
            response.setCurrentFile(state.getCurrentFile());
            response.setCurrentActivity(state.getCurrentActivity());
            response.setError(state.getError());
            response.setStartTime(state.getStartTime());
            response.setElapsedMs(System.currentTimeMillis() - state.getStartTime());

            if (state.getTotalBytes() > 0) {
                response.setProgressPercent((double) state.getBytesProcessed() / state.getTotalBytes() * 100);
            }

            ctx.json(response);
        }
    }

    /**
     * GET /api/backup/history - Get backup history.
     */
    public void getHistory(Context ctx) {
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(10);
        List<BackupHistoryEntry> limited = backupHistory.subList(0, Math.min(limit, backupHistory.size()));
        ctx.json(Map.of("history", limited));
    }

    /**
     * POST /api/backup/cancel - Cancel running backup.
     */
    public void cancelBackup(Context ctx) {
        BackupState state = currentBackup.get();
        if (state == null || !state.isRunning()) {
            ctx.status(400).json(ApiError.badRequest("No backup is currently running", ctx.path()));
            return;
        }

        state.setStatus("cancelled");
        webServer.broadcast("backup:cancelled", Map.of("snapshotId", state.getSnapshotId()));
        ctx.json(Map.of("status", "cancelled", "message", "Backup cancelled"));
    }

    // Internal state classes

    private static class BackupState {
        private final String snapshotId;
        private final long startTime;
        private volatile String status;
        private volatile int filesProcessed;
        private volatile long bytesProcessed;
        private volatile int totalFiles;
        private volatile long totalBytes;
        private volatile String currentFile;
        private volatile String currentActivity;
        private volatile String error;

        BackupState(String snapshotId) {
            this.snapshotId = snapshotId != null ? snapshotId : "backup-" + System.currentTimeMillis();
            this.startTime = System.currentTimeMillis();
            this.status = "starting";
        }

        boolean isRunning() {
            return "starting".equals(status) || "running".equals(status);
        }

        String getSnapshotId() {
            return snapshotId;
        }

        long getStartTime() {
            return startTime;
        }

        String getStatus() {
            return status;
        }

        void setStatus(String status) {
            this.status = status;
        }

        int getFilesProcessed() {
            return filesProcessed;
        }

        void setFilesProcessed(int filesProcessed) {
            this.filesProcessed = filesProcessed;
        }

        long getBytesProcessed() {
            return bytesProcessed;
        }

        void setBytesProcessed(long bytesProcessed) {
            this.bytesProcessed = bytesProcessed;
        }

        int getTotalFiles() {
            return totalFiles;
        }

        void setTotalFiles(int totalFiles) {
            this.totalFiles = totalFiles;
        }

        long getTotalBytes() {
            return totalBytes;
        }

        void setTotalBytes(long totalBytes) {
            this.totalBytes = totalBytes;
        }

        String getCurrentFile() {
            return currentFile;
        }

        void setCurrentFile(String currentFile) {
            this.currentFile = currentFile;
        }

        String getCurrentActivity() {
            return currentActivity;
        }

        void setCurrentActivity(String currentActivity) {
            this.currentActivity = currentActivity;
        }

        String getError() {
            return error;
        }

        void setError(String error) {
            this.error = error;
        }
    }

    private static class BackupHistoryEntry {
        private final String snapshotId;
        private final String status;
        private final int filesProcessed;
        private final long bytesProcessed;
        private final long timestamp;

        BackupHistoryEntry(String snapshotId, String status, int filesProcessed,
                long bytesProcessed, long timestamp) {
            this.snapshotId = snapshotId;
            this.status = status;
            this.filesProcessed = filesProcessed;
            this.bytesProcessed = bytesProcessed;
            this.timestamp = timestamp;
        }

        public String getSnapshotId() {
            return snapshotId;
        }

        public String getStatus() {
            return status;
        }

        public int getFilesProcessed() {
            return filesProcessed;
        }

        public long getBytesProcessed() {
            return bytesProcessed;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
