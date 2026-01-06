package com.justsyncit.web.dto;

import java.util.List;

/**
 * Request DTO for restore operations.
 */
public final class RestoreRequest {

    private String snapshotId;
    private String targetPath;
    private boolean overwriteExisting;
    private boolean preserveAttributes;
    private boolean verifyIntegrity;
    private List<String> includePatterns;
    private List<String> excludePatterns;

    public RestoreRequest() {
        // Default constructor for JSON deserialization
        this.overwriteExisting = false;
        this.preserveAttributes = true;
        this.verifyIntegrity = true;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(String snapshotId) {
        this.snapshotId = snapshotId;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    public boolean isOverwriteExisting() {
        return overwriteExisting;
    }

    public void setOverwriteExisting(boolean overwriteExisting) {
        this.overwriteExisting = overwriteExisting;
    }

    public boolean isPreserveAttributes() {
        return preserveAttributes;
    }

    public void setPreserveAttributes(boolean preserveAttributes) {
        this.preserveAttributes = preserveAttributes;
    }

    public boolean isVerifyIntegrity() {
        return verifyIntegrity;
    }

    public void setVerifyIntegrity(boolean verifyIntegrity) {
        this.verifyIntegrity = verifyIntegrity;
    }

    public List<String> getIncludePatterns() {
        return includePatterns;
    }

    public void setIncludePatterns(List<String> includePatterns) {
        this.includePatterns = includePatterns;
    }

    public List<String> getExcludePatterns() {
        return excludePatterns;
    }

    public void setExcludePatterns(List<String> excludePatterns) {
        this.excludePatterns = excludePatterns;
    }
}
