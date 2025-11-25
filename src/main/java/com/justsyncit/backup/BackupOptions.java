package com.justsyncit.backup;

import com.justsyncit.scanner.SymlinkStrategy;
import com.justsyncit.network.TransportType;

import java.net.InetSocketAddress;
import java.util.Objects;

/**
 * Configuration options for backup operations.
 * <p>
 * This class is immutable and thread-safe. Use the {@link Builder} to create
 * instances.
 * </p>
 */
public class BackupOptions {
    private final SymlinkStrategy symlinkStrategy;
    private final boolean includeHiddenFiles;
    private final boolean verifyIntegrity;
    private final int chunkSize;
    private final int maxDepth;
    private final String snapshotName;
    private final String description;

    // Network options for remote backup
    private final boolean remoteBackup;
    private final InetSocketAddress remoteAddress;
    private final TransportType transportType;

    private BackupOptions(Builder builder) {
        this.symlinkStrategy = builder.symlinkStrategy;
        this.includeHiddenFiles = builder.includeHiddenFiles;
        this.verifyIntegrity = builder.verifyIntegrity;
        this.chunkSize = builder.chunkSize;
        this.maxDepth = builder.maxDepth;
        this.snapshotName = builder.snapshotName;
        this.description = builder.description;
        this.remoteBackup = builder.remoteBackup;
        this.remoteAddress = builder.remoteAddress;
        this.transportType = builder.transportType;
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

    public boolean isRemoteBackup() {
        return remoteBackup;
    }

    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public TransportType getTransportType() {
        return transportType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        BackupOptions that = (BackupOptions) o;
        return includeHiddenFiles == that.includeHiddenFiles &&
                verifyIntegrity == that.verifyIntegrity &&
                chunkSize == that.chunkSize &&
                maxDepth == that.maxDepth &&
                remoteBackup == that.remoteBackup &&
                symlinkStrategy == that.symlinkStrategy &&
                Objects.equals(snapshotName, that.snapshotName) &&
                Objects.equals(description, that.description) &&
                Objects.equals(remoteAddress, that.remoteAddress) &&
                transportType == that.transportType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(symlinkStrategy, includeHiddenFiles, verifyIntegrity, chunkSize, maxDepth, snapshotName,
                description, remoteBackup, remoteAddress, transportType);
    }

    @Override
    public String toString() {
        return "BackupOptions{" +
                "symlinkStrategy=" + symlinkStrategy +
                ", includeHiddenFiles=" + includeHiddenFiles +
                ", verifyIntegrity=" + verifyIntegrity +
                ", chunkSize=" + chunkSize +
                ", maxDepth=" + maxDepth +
                ", snapshotName='" + snapshotName + '\'' +
                ", description='" + description + '\'' +
                ", remoteBackup=" + remoteBackup +
                ", remoteAddress=" + remoteAddress +
                ", transportType=" + transportType +
                '}';
    }

    public static class Builder {
        private SymlinkStrategy symlinkStrategy = SymlinkStrategy.RECORD;
        private boolean includeHiddenFiles = false;
        private boolean verifyIntegrity = true;
        private int chunkSize = 64 * 1024; // 64KB default
        private int maxDepth = Integer.MAX_VALUE; // Unlimited depth by default
        private String snapshotName;
        private String description;

        // Network options with defaults
        private boolean remoteBackup = false;
        private InetSocketAddress remoteAddress = null;
        private TransportType transportType = TransportType.TCP;

        public Builder symlinkStrategy(SymlinkStrategy symlinkStrategy) {
            this.symlinkStrategy = Objects.requireNonNull(symlinkStrategy, "Symlink strategy cannot be null");
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
            if (chunkSize <= 0) {
                throw new IllegalArgumentException("Chunk size must be positive");
            }
            this.chunkSize = chunkSize;
            return this;
        }

        public Builder maxDepth(int maxDepth) {
            if (maxDepth < 0) {
                throw new IllegalArgumentException("Max depth must be non-negative");
            }
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

        public Builder remoteBackup(boolean remoteBackup) {
            this.remoteBackup = remoteBackup;
            return this;
        }

        public Builder remoteAddress(InetSocketAddress remoteAddress) {
            this.remoteAddress = remoteAddress;
            return this;
        }

        public Builder transportType(TransportType transportType) {
            this.transportType = Objects.requireNonNull(transportType, "Transport type cannot be null");
            return this;
        }

        public BackupOptions build() {
            if (remoteBackup && remoteAddress == null) {
                throw new IllegalStateException("Remote address is required for remote backup");
            }
            return new BackupOptions(this);
        }
    }
}