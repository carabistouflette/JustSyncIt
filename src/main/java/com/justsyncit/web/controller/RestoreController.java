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

import com.justsyncit.restore.RestoreOptions;
import com.justsyncit.restore.RestoreService;
import com.justsyncit.web.WebServer;
import com.justsyncit.web.WebServerContext;
import com.justsyncit.web.dto.ApiError;
import com.justsyncit.web.dto.RestoreRequest;

import io.javalin.http.Context;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * REST controller for restore operations.
 */
public final class RestoreController {

    private static final Logger LOGGER = Logger.getLogger(RestoreController.class.getName());

    private final WebServerContext context;
    private final WebServer webServer;
    private final AtomicReference<RestoreState> currentRestore;

    public RestoreController(WebServerContext context, WebServer webServer) {
        this.context = context;
        this.webServer = webServer;
        this.currentRestore = new AtomicReference<>();
    }

    /**
     * POST /api/restore - Start a restore operation.
     */
    public void startRestore(Context ctx) {
        try {
            RestoreRequest request = ctx.bodyAsClass(RestoreRequest.class);

            // Validate request
            if (request.getSnapshotId() == null || request.getSnapshotId().isEmpty()) {
                ctx.status(400).json(ApiError.badRequest("Snapshot ID is required", ctx.path()));
                return;
            }

            if (request.getTargetPath() == null || request.getTargetPath().isEmpty()) {
                ctx.status(400).json(ApiError.badRequest("Target path is required", ctx.path()));
                return;
            }

            Path targetPath = Paths.get(request.getTargetPath());

            // Check if restore is already running
            RestoreState state = currentRestore.get();
            if (state != null && state.isRunning()) {
                ctx.status(409).json(ApiError.of(409, "Conflict",
                        "A restore is already in progress", ctx.path()));
                return;
            }

            // Create restore options using Builder pattern
            RestoreOptions options = new RestoreOptions.Builder()
                    .overwriteExisting(request.isOverwriteExisting())
                    .preserveAttributes(request.isPreserveAttributes())
                    .verifyIntegrity(request.isVerifyIntegrity())
                    .build();

            // Start restore asynchronously
            RestoreState newState = new RestoreState(request.getSnapshotId());
            currentRestore.set(newState);

            RestoreService restoreService = context.getRestoreService();

            CompletableFuture.runAsync(() -> {
                try {
                    newState.setStatus("running");
                    webServer.broadcast("restore:started", Map.of(
                            "snapshotId", request.getSnapshotId()));

                    RestoreService.RestoreResult result = restoreService
                            .restore(request.getSnapshotId(), targetPath, options).get();

                    newState.setStatus("completed");
                    newState.setFilesRestored(result.getFilesRestored());
                    newState.setBytesRestored(result.getTotalBytesRestored());

                    webServer.broadcast("restore:completed", Map.of(
                            "snapshotId", request.getSnapshotId(),
                            "filesRestored", result.getFilesRestored(),
                            "bytesRestored", result.getTotalBytesRestored()));

                } catch (Exception e) {
                    LOGGER.severe("Restore failed: " + e.getMessage());
                    newState.setStatus("failed");
                    newState.setError(e.getMessage());
                    webServer.broadcast("restore:failed", Map.of("error", e.getMessage()));
                }
            });

            ctx.status(202).json(Map.of(
                    "status", "accepted",
                    "message", "Restore started",
                    "snapshotId", request.getSnapshotId()));

        } catch (Exception e) {
            LOGGER.severe("Failed to start restore: " + e.getMessage());
            ctx.status(500).json(ApiError.internalError(e.getMessage(), ctx.path()));
        }
    }

    /**
     * GET /api/restore/status - Get current restore status.
     */
    public void getStatus(Context ctx) {
        RestoreState state = currentRestore.get();
        if (state == null) {
            ctx.json(Map.of("status", "idle"));
        } else {
            ctx.json(Map.of(
                    "status", state.getStatus(),
                    "snapshotId", state.getSnapshotId(),
                    "filesRestored", state.getFilesRestored(),
                    "bytesRestored", state.getBytesRestored(),
                    "elapsedMs", System.currentTimeMillis() - state.getStartTime(),
                    "error", state.getError() != null ? state.getError() : ""));
        }
    }

    /**
     * POST /api/restore/cancel - Cancel running restore.
     */
    public void cancelRestore(Context ctx) {
        RestoreState state = currentRestore.get();
        if (state == null || !state.isRunning()) {
            ctx.status(400).json(ApiError.badRequest("No restore is currently running", ctx.path()));
            return;
        }

        state.setStatus("cancelled");
        webServer.broadcast("restore:cancelled", Map.of("snapshotId", state.getSnapshotId()));
        ctx.json(Map.of("status", "cancelled", "message", "Restore cancelled"));
    }

    // Internal state class

    private static class RestoreState {
        private final String snapshotId;
        private final long startTime;
        private volatile String status;
        private volatile int filesRestored;
        private volatile long bytesRestored;
        private volatile String error;

        RestoreState(String snapshotId) {
            this.snapshotId = snapshotId;
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

        int getFilesRestored() {
            return filesRestored;
        }

        void setFilesRestored(int filesRestored) {
            this.filesRestored = filesRestored;
        }

        long getBytesRestored() {
            return bytesRestored;
        }

        void setBytesRestored(long bytesRestored) {
            this.bytesRestored = bytesRestored;
        }

        String getError() {
            return error;
        }

        void setError(String error) {
            this.error = error;
        }
    }
}
