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

import com.justsyncit.network.TransportType;

import java.net.InetSocketAddress;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;

/**
 * Configuration options for restore operations.
 * Follows Builder pattern for flexible configuration.
 */
public class RestoreOptions {

    /** Whether to overwrite existing files. */
    private boolean overwriteExisting;

    /** Whether to skip existing files. */
    private boolean skipExisting;

    /** Whether to backup existing files before overwriting. */
    private boolean backupExisting;

    /** Whether to verify integrity after restore. */
    private boolean verifyIntegrity;

    /** Whether to preserve file attributes. */
    private boolean preserveAttributes;

    /** Include pattern for files to restore. */
    private PathMatcher includePattern;

    /** Original include pattern string for files to restore. */
    private String includePatternString;

    /** Exclude pattern for files to skip. */
    private PathMatcher excludePattern;

    /** Original exclude pattern string for files to skip. */
    private String excludePatternString;

    /** Whether to perform a dry run (simulate changes). */
    private boolean dryRun;

    // Network options for remote restore
    private boolean remoteRestore;
    private InetSocketAddress remoteAddress;
    private TransportType transportType;

    /**
     * Creates a new RestoreOptions.
     */
    private RestoreOptions(Builder builder) {
        this.overwriteExisting = builder.overwriteExisting;
        this.skipExisting = builder.skipExisting;
        this.backupExisting = builder.backupExisting;
        this.verifyIntegrity = builder.verifyIntegrity;
        this.preserveAttributes = builder.preserveAttributes;
        this.includePattern = builder.includePattern;
        this.includePatternString = builder.includePatternString;
        this.excludePattern = builder.excludePattern;
        this.excludePatternString = builder.excludePatternString;
        this.dryRun = builder.dryRun;
        this.remoteRestore = builder.remoteRestore;
        this.remoteAddress = builder.remoteAddress;
        this.transportType = builder.transportType;
    }

    /**
     * Creates a new RestoreOptions with default values.
     */
    public RestoreOptions() {
        this.overwriteExisting = false;
        this.skipExisting = false;
        this.backupExisting = false;
        this.verifyIntegrity = true;
        this.preserveAttributes = true;
        this.includePattern = null;
        this.includePatternString = null;
        this.excludePattern = null;
        this.excludePatternString = null;
        this.dryRun = false;
        this.remoteRestore = false;
        this.remoteAddress = null;
        this.transportType = TransportType.TCP;
    }

    public boolean isOverwriteExisting() {
        return overwriteExisting;
    }

    public void setOverwriteExisting(boolean overwriteExisting) {
        this.overwriteExisting = overwriteExisting;
    }

    public boolean isSkipExisting() {
        return skipExisting;
    }

    public void setSkipExisting(boolean skipExisting) {
        this.skipExisting = skipExisting;
    }

    public boolean isBackupExisting() {
        return backupExisting;
    }

    public void setBackupExisting(boolean backupExisting) {
        this.backupExisting = backupExisting;
    }

    public boolean isVerifyIntegrity() {
        return verifyIntegrity;
    }

    public void setVerifyIntegrity(boolean verifyIntegrity) {
        this.verifyIntegrity = verifyIntegrity;
    }

    public boolean isPreserveAttributes() {
        return preserveAttributes;
    }

    public void setPreserveAttributes(boolean preserveAttributes) {
        this.preserveAttributes = preserveAttributes;
    }

    public PathMatcher getIncludePattern() {
        return includePattern;
    }

    public void setIncludePattern(String pattern) {
        if (pattern != null && !pattern.trim().isEmpty()) {
            this.includePattern = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            this.includePatternString = pattern;
        } else {
            this.includePattern = null;
            this.includePatternString = null;
        }
    }

    public String getIncludePatternString() {
        return includePatternString;
    }

    public PathMatcher getExcludePattern() {
        return excludePattern;
    }

    public void setExcludePattern(String pattern) {
        if (pattern != null && !pattern.trim().isEmpty()) {
            this.excludePattern = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            this.excludePatternString = pattern;
        } else {
            this.excludePattern = null;
            this.excludePatternString = null;
        }
    }

    public String getExcludePatternString() {
        return excludePatternString;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public boolean isRemoteRestore() {
        return remoteRestore;
    }

    public void setRemoteRestore(boolean remoteRestore) {
        this.remoteRestore = remoteRestore;
    }

    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public void setRemoteAddress(InetSocketAddress remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public TransportType getTransportType() {
        return transportType;
    }

    public void setTransportType(TransportType transportType) {
        this.transportType = transportType;
    }

    @Override
    public String toString() {
        return "RestoreOptions{"
                + "overwriteExisting=" + overwriteExisting
                + ", skipExisting=" + skipExisting
                + ", backupExisting=" + backupExisting
                + ", verifyIntegrity=" + verifyIntegrity
                + ", preserveAttributes=" + preserveAttributes
                + ", includePatternString='" + includePatternString + '\''
                + ", excludePatternString='" + excludePatternString + '\''
                + '}';
    }

    /**
     * Builder for creating RestoreOptions instances.
     */
    public static class Builder {
        private boolean overwriteExisting = false;
        private boolean skipExisting = false;
        private boolean backupExisting = false;
        private boolean verifyIntegrity = true;
        private boolean preserveAttributes = true;
        private PathMatcher includePattern;
        private PathMatcher excludePattern;
        private String includePatternString;
        private String excludePatternString;

        // Network options with defaults
        private boolean remoteRestore = false;
        private InetSocketAddress remoteAddress = null;
        private TransportType transportType = TransportType.TCP;

        // Dry run option
        private boolean dryRun = false;

        public Builder overwriteExisting(boolean overwriteExisting) {
            this.overwriteExisting = overwriteExisting;
            return this;
        }

        public Builder skipExisting(boolean skipExisting) {
            this.skipExisting = skipExisting;
            return this;
        }

        public Builder backupExisting(boolean backupExisting) {
            this.backupExisting = backupExisting;
            return this;
        }

        public Builder verifyIntegrity(boolean verifyIntegrity) {
            this.verifyIntegrity = verifyIntegrity;
            return this;
        }

        public Builder preserveAttributes(boolean preserveAttributes) {
            this.preserveAttributes = preserveAttributes;
            return this;
        }

        public Builder includePattern(String pattern) {
            if (pattern != null && !pattern.trim().isEmpty()) {
                this.includePattern = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
                this.includePatternString = pattern;
            }
            return this;
        }

        public Builder excludePattern(String pattern) {
            if (pattern != null && !pattern.trim().isEmpty()) {
                this.excludePattern = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
                this.excludePatternString = pattern;
            }
            return this;
        }

        public Builder remoteRestore(boolean remoteRestore) {
            this.remoteRestore = remoteRestore;
            return this;
        }

        public Builder remoteAddress(InetSocketAddress remoteAddress) {
            this.remoteAddress = remoteAddress;
            return this;
        }

        public Builder transportType(TransportType transportType) {
            this.transportType = transportType;
            return this;
        }

        public RestoreOptions build() {
            return new RestoreOptions(this);
        }
    }
}