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

package com.justsyncit.web.dto;

/**
 * Response DTO for backup status.
 */
public final class BackupStatusResponse {

    private String status;
    private String snapshotId;
    private int filesProcessed;
    private long bytesProcessed;
    private int totalFiles;
    private long totalBytes;
    private double progressPercent;
    private String currentFile;
    private String error;
    private long startTime;
    private long elapsedMs;

    public BackupStatusResponse() {
        // Default constructor for JSON serialization
    }

    public static BackupStatusResponse idle() {
        BackupStatusResponse response = new BackupStatusResponse();
        response.setStatus("idle");
        return response;
    }

    public static BackupStatusResponse running(String snapshotId, int filesProcessed, long bytesProcessed,
            int totalFiles, long totalBytes, String currentFile,
            long startTime) {
        BackupStatusResponse response = new BackupStatusResponse();
        response.setStatus("running");
        response.setSnapshotId(snapshotId);
        response.setFilesProcessed(filesProcessed);
        response.setBytesProcessed(bytesProcessed);
        response.setTotalFiles(totalFiles);
        response.setTotalBytes(totalBytes);
        response.setCurrentFile(currentFile);
        response.setStartTime(startTime);
        response.setElapsedMs(System.currentTimeMillis() - startTime);
        if (totalBytes > 0) {
            response.setProgressPercent((double) bytesProcessed / totalBytes * 100);
        }
        return response;
    }

    public static BackupStatusResponse completed(String snapshotId, int filesProcessed,
            long bytesProcessed, long elapsedMs) {
        BackupStatusResponse response = new BackupStatusResponse();
        response.setStatus("completed");
        response.setSnapshotId(snapshotId);
        response.setFilesProcessed(filesProcessed);
        response.setBytesProcessed(bytesProcessed);
        response.setProgressPercent(100.0);
        response.setElapsedMs(elapsedMs);
        return response;
    }

    public static BackupStatusResponse failed(String error) {
        BackupStatusResponse response = new BackupStatusResponse();
        response.setStatus("failed");
        response.setError(error);
        return response;
    }

    // Getters and Setters

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(String snapshotId) {
        this.snapshotId = snapshotId;
    }

    public int getFilesProcessed() {
        return filesProcessed;
    }

    public void setFilesProcessed(int filesProcessed) {
        this.filesProcessed = filesProcessed;
    }

    public long getBytesProcessed() {
        return bytesProcessed;
    }

    public void setBytesProcessed(long bytesProcessed) {
        this.bytesProcessed = bytesProcessed;
    }

    public int getTotalFiles() {
        return totalFiles;
    }

    public void setTotalFiles(int totalFiles) {
        this.totalFiles = totalFiles;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
    }

    public double getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(double progressPercent) {
        this.progressPercent = progressPercent;
    }

    public String getCurrentFile() {
        return currentFile;
    }

    public void setCurrentFile(String currentFile) {
        this.currentFile = currentFile;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }
}
