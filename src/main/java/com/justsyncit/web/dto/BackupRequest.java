package com.justsyncit.web.dto;

import java.util.List;

/**
 * Request DTO for starting a backup operation.
 */
public final class BackupRequest {

    private String sourcePath;
    private String snapshotName;
    private String description;
    private int chunkSize;
    private boolean includeHidden;
    private boolean verifyIntegrity;
    private List<String> excludePatterns;

    public BackupRequest() {
        // Default constructor for JSON deserialization
        this.chunkSize = 4 * 1024 * 1024; // 4MB default
        this.includeHidden = false;
        this.verifyIntegrity = true;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public String getSnapshotName() {
        return snapshotName;
    }

    public void setSnapshotName(String snapshotName) {
        this.snapshotName = snapshotName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public boolean isIncludeHidden() {
        return includeHidden;
    }

    public void setIncludeHidden(boolean includeHidden) {
        this.includeHidden = includeHidden;
    }

    public boolean isVerifyIntegrity() {
        return verifyIntegrity;
    }

    public void setVerifyIntegrity(boolean verifyIntegrity) {
        this.verifyIntegrity = verifyIntegrity;
    }

    public List<String> getExcludePatterns() {
        return excludePatterns;
    }

    public void setExcludePatterns(List<String> excludePatterns) {
        this.excludePatterns = excludePatterns;
    }
}
