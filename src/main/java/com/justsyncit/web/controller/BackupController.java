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

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST controller for backup operations.
 */
public final class BackupController {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackupController.class);

    private final WebServerContext context;
    private final WebServer webServer;
    private final ConfigController configController;
    private final AtomicReference<BackupState> currentBackup;
    private final List<BackupHistoryEntry> backupHistory;

    public BackupController(WebServerContext context, WebServer webServer, ConfigController configController) {
        this.context = context;
        this.webServer = webServer;
        this.configController = configController;
        this.currentBackup = new AtomicReference<>();
        this.backupHistory = new CopyOnWriteArrayList<>();
    }

    /**
     * POST /api/backup - Start a new backup operation.
     */
    public void startBackup(Context ctx) {
        try {
            BackupRequest request = ctx.bodyAsClass(BackupRequest.class);

            // 1. Gather & Validate Source Paths
            List<Path> sourcePaths = new java.util.ArrayList<>();
            if (request.getSourcePaths() != null && !request.getSourcePaths().isEmpty()) {
                for (String p : request.getSourcePaths())
                    sourcePaths.add(Paths.get(p));
            } else if (request.getSourcePath() != null && !request.getSourcePath().isEmpty()) {
                sourcePaths.add(Paths.get(request.getSourcePath()));
            }

            if (sourcePaths.isEmpty()) {
                ctx.status(400).json(ApiError.badRequest("At least one source path is required", ctx.path()));
                return;
            }

            for (Path p : sourcePaths) {
                if (!Files.exists(p) || !Files.isDirectory(p)) {
                    ctx.status(400).json(ApiError.badRequest("Invalid source directory: " + p, ctx.path()));
                    return;
                }
                if (!isPathAllowed(p)) {
                    ctx.status(403).json(ApiError.forbidden("Access to path not allowed: " + p, ctx.path()));
                    return;
                }
            }

            // 2. Check if running
            BackupState state = currentBackup.get();
            if (state != null && state.isRunning()) {
                ctx.status(409).json(ApiError.of(409, "Conflict", "A backup is already in progress", ctx.path()));
                return;
            }

            // 3. Configure Options
            BackupOptions.Builder optionsBuilder = new BackupOptions.Builder()
                    .chunkSize(request.getChunkSize() > 0 ? request.getChunkSize() : 64 * 1024)
                    .includeHiddenFiles(request.isIncludeHidden())
                    .verifyIntegrity(request.isVerifyIntegrity())
                    .excludePatterns(request.getExcludePatterns());

            if (request.getSnapshotName() != null)
                optionsBuilder.snapshotName(request.getSnapshotName());
            if (request.getDescription() != null)
                optionsBuilder.description(request.getDescription());

            BackupOptions options = optionsBuilder.build();
            BackupState newState = new BackupState(options.getSnapshotName());
            currentBackup.set(newState);

            BackupService backupService = context.getBackupService();

            // 4. Start Async Backup
            CompletableFuture.runAsync(() -> {
                try {
                    newState.setStatus("running");
                    webServer.broadcast("backup:started", Map.of("snapshotId", newState.getSnapshotId()));

                    java.util.concurrent.atomic.AtomicLong lastBroadcast = new java.util.concurrent.atomic.AtomicLong(
                            0);

                    backupService.setEventListener((type, level, message, file) -> {
                        webServer.broadcast("backup:event", Map.of(
                                "snapshotId", newState.getSnapshotId(),
                                "type", type, "level", level, "message", message,
                                "file", file != null ? file : ""));
                    });

                    backupService.backupMultiple(sourcePaths, options, processor -> {
                        newState.setFilesProcessed(processor.getProcessedFilesCount());
                        newState.setBytesProcessed(processor.getProcessedBytesCount());
                        newState.setTotalFiles(processor.getDetectedFilesCount());
                        newState.setTotalBytes(processor.getTotalBytesCount());
                        newState.setCurrentFile(processor.getCurrentFile());
                        newState.setCurrentActivity(processor.getCurrentActivity());

                        long now = System.currentTimeMillis();
                        if (now - lastBroadcast.get() > 100) {
                            lastBroadcast.set(now);
                            webServer.broadcast("backup:progress", Map.of(
                                    "snapshotId", newState.getSnapshotId(),
                                    "filesProcessed", processor.getProcessedFilesCount(),
                                    "bytesProcessed", processor.getProcessedBytesCount(),
                                    "currentFile", processor.getCurrentFile() != null ? processor.getCurrentFile() : "",
                                    "currentActivity",
                                    processor.getCurrentActivity() != null ? processor.getCurrentActivity() : "",
                                    "progressPercent", processor.getProcessingPercentage()));
                        }
                    }).get();

                    newState.setStatus("completed");
                    newState.setCompletedAt(System.currentTimeMillis());
                    backupHistory.add(new BackupHistoryEntry(newState.getSnapshotId(), "completed",
                            newState.getFilesProcessed(), newState.getBytesProcessed(), newState.getCompletedAt()));
                    webServer.broadcast("backup:completed", Map.of("snapshotId", newState.getSnapshotId()));

                } catch (Exception e) {
                    LOGGER.error("Backup background process failed", e);
                    newState.setStatus("failed");
                    newState.setError(e.getMessage());
                    backupHistory.add(new BackupHistoryEntry(newState.getSnapshotId(), "failed",
                            newState.getFilesProcessed(), newState.getBytesProcessed(), System.currentTimeMillis()));
                    webServer.broadcast("backup:failed",
                            Map.of("snapshotId", newState.getSnapshotId(), "error", e.getMessage()));
                }
            });

            ctx.status(202).json(Map.of("message", "Backup started", "snapshotId", newState.getSnapshotId()));

        } catch (Exception e) {
            LOGGER.error("Failed to start backup", e);
            ctx.status(500).json(ApiError.internalError(e.getMessage(), ctx.path()));
        }
    }

    private boolean isPathAllowed(Path path) {
        String pathStr = path.toAbsolutePath().normalize().toString();
        List<String> allowedSources = configController.getBackupSourcesList();
        if (allowedSources.isEmpty())
            return pathStr.startsWith(System.getProperty("user.home"));
        for (String source : allowedSources)
            if (pathStr.startsWith(source))
                return true;
        return false;
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
        private long totalBytes;
        private String currentFile;
        private String currentActivity;
        private String error;
        private long completedAt;

        BackupState(String snapshotId) {
            this.snapshotId = snapshotId != null ? snapshotId : "backup-" + System.currentTimeMillis();
            this.status = "initialized";
            this.startTime = System.currentTimeMillis();
        }

        boolean isRunning() {
            return "running".equals(status);
        }

        String getSnapshotId() {
            return snapshotId;
        }

        String getStatus() {
            return status;
        }

        void setStatus(String status) {
            this.status = status;
        }

        long getStartTime() {
            return startTime;
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

        long getCompletedAt() {
            return completedAt;
        }

        void setCompletedAt(long completedAt) {
            this.completedAt = completedAt;
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
