package com.justsyncit.backup;

import com.justsyncit.scanner.SymlinkStrategy;

/**
 * Configuration options for backup operations.
 */
public class BackupOptions {
    private final SymlinkStrategy symlinkStrategy;
    private final boolean includeHiddenFiles;
    private final boolean verifyIntegrity;
    private final int chunkSize;
    private final int maxDepth;
    private final String snapshotName;
    private final String description;

    private BackupOptions(Builder builder) {
        this.symlinkStrategy = builder.symlinkStrategy;
        this.includeHiddenFiles = builder.includeHiddenFiles;
        this.verifyIntegrity = builder.verifyIntegrity;
        this.chunkSize = builder.chunkSize;
        this.maxDepth = builder.maxDepth;
        this.snapshotName = builder.snapshotName;
        this.description = builder.description;
    }

    public SymlinkStrategy getSymlinkStrategy() {
        return symlinkStrategy;
    }

    public boolean isIncludeHiddenFiles() {
        return includeHiddenFiles;
    }

    public boolean isVerifyIntegrity() {
        return verifyIntegrity;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public String getSnapshotName() {
        return snapshotName;
    }

    public String getDescription() {
        return description;
    }

    public static class Builder {
        private SymlinkStrategy symlinkStrategy = SymlinkStrategy.RECORD;
        private boolean includeHiddenFiles = false;
        private boolean verifyIntegrity = true;
        private int chunkSize = 64 * 1024; // 64KB default
        private int maxDepth = Integer.MAX_VALUE; // Unlimited depth by default
        private String snapshotName;
        private String description;

        public Builder symlinkStrategy(SymlinkStrategy symlinkStrategy) {
            this.symlinkStrategy = symlinkStrategy;
            return this;
        }

        public Builder includeHiddenFiles(boolean includeHiddenFiles) {
            this.includeHiddenFiles = includeHiddenFiles;
            return this;
        }

        public Builder verifyIntegrity(boolean verifyIntegrity) {
            this.verifyIntegrity = verifyIntegrity;
            return this;
        }

        public Builder chunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
            return this;
        }

        public Builder maxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }

        public Builder snapshotName(String snapshotName) {
            this.snapshotName = snapshotName;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public BackupOptions build() {
            return new BackupOptions(this);
        }
    }
}