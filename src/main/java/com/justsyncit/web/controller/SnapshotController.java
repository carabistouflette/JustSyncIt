package com.justsyncit.web.controller;

import com.justsyncit.storage.metadata.MetadataService;
import com.justsyncit.storage.metadata.Snapshot;
import com.justsyncit.storage.metadata.FileMetadata;
import com.justsyncit.web.WebServerContext;
import com.justsyncit.web.dto.ApiError;
import com.justsyncit.web.dto.SnapshotResponse;

import io.javalin.http.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST controller for snapshot operations.
 */
public final class SnapshotController {

    private static final Logger LOGGER = LoggerFactory.getLogger(SnapshotController.class);

    private final WebServerContext context;

    public SnapshotController(WebServerContext context) {
        this.context = context;
    }

    /**
     * GET /api/snapshots - List all snapshots.
     */
    public void listSnapshots(Context ctx) {
        try {
            MetadataService metadataService = context.getMetadataService();
            if (metadataService == null) {
                ctx.json(Map.of("snapshots", List.of()));
                return;
            }

            List<Snapshot> snapshots = metadataService.listSnapshots();
            List<SnapshotResponse> responses = new ArrayList<>();

            for (Snapshot snapshot : snapshots) {
                SnapshotResponse response = new SnapshotResponse();
                response.setId(snapshot.getId());
                response.setDescription(snapshot.getDescription());
                response.setCreatedAt(snapshot.getCreatedAt());
                // These are now available in the Snapshot object
                response.setFileCount(snapshot.getTotalFiles());
                response.setTotalBytes(snapshot.getTotalSize());
                LOGGER.info("Snapshot {}: files={}, bytes={}",
                        snapshot.getId(), snapshot.getTotalFiles(), snapshot.getTotalSize());
                responses.add(response);
            }

            ctx.json(Map.of("snapshots", responses));
        } catch (Exception e) {
            LOGGER.error("Failed to list snapshots", e);
            ctx.status(500).json(ApiError.internalError(e.getMessage(), ctx.path()));
        }
    }

    /**
     * GET /api/snapshots/{id} - Get snapshot details.
     */
    public void getSnapshot(Context ctx) {
        try {
            String snapshotId = ctx.pathParam("id");
            MetadataService metadataService = context.getMetadataService();

            if (metadataService == null) {
                ctx.status(404).json(ApiError.notFound("Snapshot not found: " + snapshotId, ctx.path()));
                return;
            }

            Optional<Snapshot> snapshotOpt = metadataService.getSnapshot(snapshotId);
            if (snapshotOpt.isEmpty()) {
                ctx.status(404).json(ApiError.notFound("Snapshot not found: " + snapshotId, ctx.path()));
                return;
            }
            Snapshot snapshot = snapshotOpt.get();

            SnapshotResponse response = new SnapshotResponse();
            response.setId(snapshot.getId());
            response.setDescription(snapshot.getDescription());
            response.setCreatedAt(snapshot.getCreatedAt());
            response.setFileCount(snapshot.getTotalFiles());
            response.setTotalBytes(snapshot.getTotalSize());

            ctx.json(response);
        } catch (Exception e) {
            LOGGER.error("Failed to get snapshot", e);
            ctx.status(500).json(ApiError.internalError(e.getMessage(), ctx.path()));
        }
    }

    /**
     * GET /api/snapshots/{id}/files - List files in a snapshot.
     */
    public void getSnapshotFiles(Context ctx) {
        try {
            String snapshotId = ctx.pathParam("id");
            String pathPrefix = ctx.queryParam("path");
            int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(100);
            int offset = ctx.queryParamAsClass("offset", Integer.class).getOrDefault(0);

            MetadataService metadataService = context.getMetadataService();

            if (metadataService == null) {
                ctx.status(404).json(ApiError.notFound("Snapshot not found: " + snapshotId, ctx.path()));
                return;
            }

            // Use efficient pagination from service, excluding chunks for UI display
            int total = metadataService.countFilesInSnapshot(snapshotId, pathPrefix);
            List<FileMetadata> files = metadataService.getFilesInSnapshot(snapshotId, pathPrefix, limit, offset, false);

            List<Map<String, Object>> fileList = new ArrayList<>();
            for (FileMetadata file : files) {
                fileList.add(Map.of(
                        "path", file.getPath(),
                        "size", file.getSize(),
                        "modifiedAt", file.getModifiedTime(),
                        "hash", file.getFileHash() != null ? file.getFileHash() : ""));
            }

            ctx.json(Map.of(
                    "snapshotId", snapshotId,
                    "files", fileList,
                    "total", total,
                    "limit", limit,
                    "offset", offset));
        } catch (Exception e) {
            LOGGER.error("Failed to get snapshot files", e);
            ctx.status(500).json(ApiError.internalError(e.getMessage(), ctx.path()));
        }
    }

    /**
     * DELETE /api/snapshots/{id} - Delete a snapshot.
     */
    public void deleteSnapshot(Context ctx) {
        try {
            String snapshotId = ctx.pathParam("id");
            MetadataService metadataService = context.getMetadataService();

            if (metadataService == null) {
                ctx.status(404).json(ApiError.notFound("Snapshot not found: " + snapshotId, ctx.path()));
                return;
            }

            // Check if snapshot exists first
            Optional<Snapshot> snapshotOpt = metadataService.getSnapshot(snapshotId);
            if (snapshotOpt.isEmpty()) {
                ctx.status(404).json(ApiError.notFound("Snapshot not found: " + snapshotId, ctx.path()));
                return;
            }

            metadataService.deleteSnapshot(snapshotId);
            ctx.json(Map.of("status", "deleted", "snapshotId", snapshotId));
        } catch (Exception e) {
            LOGGER.error("Failed to delete snapshot", e);
            ctx.status(500).json(ApiError.internalError(e.getMessage(), ctx.path()));
        }
    }

    /**
     * POST /api/snapshots/{id}/verify - Verify snapshot integrity.
     */
    public void verifySnapshot(Context ctx) {
        try {
            String snapshotId = ctx.pathParam("id");

            // For now, return a mock response - actual verification would be async
            ctx.status(202).json(Map.of(
                    "status", "accepted",
                    "message", "Verification started",
                    "snapshotId", snapshotId));
        } catch (Exception e) {
            LOGGER.error("Failed to start verification", e);
            ctx.status(500).json(ApiError.internalError(e.getMessage(), ctx.path()));
        }
    }

    /**
     * GET /api/snapshots/{id}/stats - Get snapshot statistics.
     */
    public void getSnapshotStats(Context ctx) {
        try {
            String snapshotId = ctx.pathParam("id");
            MetadataService metadataService = context.getMetadataService();

            if (metadataService == null) {
                ctx.status(404).json(ApiError.notFound("Snapshot not found: " + snapshotId, ctx.path()));
                return;
            }

            List<FileMetadata> files = metadataService.getFilesInSnapshot(snapshotId);
            Map<String, Long> extensionCounts = new HashMap<>();
            long totalSize = 0;

            for (FileMetadata file : files) {
                String path = file.getPath();
                int lastDot = path.lastIndexOf('.');
                String ext = (lastDot > 0 && lastDot < path.length() - 1) ? path.substring(lastDot + 1).toLowerCase()
                        : "other";
                extensionCounts.put(ext, extensionCounts.getOrDefault(ext, 0L) + 1);
                totalSize += file.getSize();
            }

            ctx.json(Map.of(
                    "snapshotId", snapshotId,
                    "fileTypes", extensionCounts,
                    "totalSize", totalSize,
                    "fileCount", files.size()));

        } catch (Exception e) {
            LOGGER.error("Failed to get snapshot stats", e);
            ctx.status(500).json(ApiError.internalError(e.getMessage(), ctx.path()));
        }
    }
}
