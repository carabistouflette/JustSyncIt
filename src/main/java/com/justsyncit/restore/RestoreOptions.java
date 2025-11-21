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
    private final boolean overwriteExisting;

    /** Whether to backup existing files before overwriting. */
    private final boolean backupExisting;

    /** Whether to verify integrity after restore. */
    private final boolean verifyIntegrity;

    /** Whether to preserve file attributes. */
    private final boolean preserveAttributes;

    /** Include pattern for files to restore. */
    private final PathMatcher includePattern;

    /** Original include pattern string for files to restore. */
    private final String includePatternString;

    /** Exclude pattern for files to skip. */
    private final PathMatcher excludePattern;

    /** Original exclude pattern string for files to skip. */
    private final String excludePatternString;
    
    // Network options for remote restore
    private final boolean remoteRestore;
    private final InetSocketAddress remoteAddress;
    private final TransportType transportType;

    /**
     * Creates a new RestoreOptions.
     */
    private RestoreOptions(Builder builder) {
        this.overwriteExisting = builder.overwriteExisting;
        this.backupExisting = builder.backupExisting;
        this.verifyIntegrity = builder.verifyIntegrity;
        this.preserveAttributes = builder.preserveAttributes;
        this.includePattern = builder.includePattern;
        this.includePatternString = builder.includePatternString;
        this.excludePattern = builder.excludePattern;
        this.excludePatternString = builder.excludePatternString;
        this.remoteRestore = builder.remoteRestore;
        this.remoteAddress = builder.remoteAddress;
        this.transportType = builder.transportType;
    }

    /**
     * Creates a new RestoreOptions with default values.
     */
    public RestoreOptions() {
        this.overwriteExisting = false;
        this.backupExisting = false;
        this.verifyIntegrity = true;
        this.preserveAttributes = true;
        this.includePattern = null;
        this.includePatternString = null;
        this.excludePattern = null;
        this.excludePatternString = null;
        this.remoteRestore = false;
        this.remoteAddress = null;
        this.transportType = TransportType.TCP;
    }

    public boolean isOverwriteExisting() {
        return overwriteExisting;
    }

    public boolean isBackupExisting() {
        return backupExisting;
    }

    public boolean isVerifyIntegrity() {
        return verifyIntegrity;
    }

    public boolean isPreserveAttributes() {
        return preserveAttributes;
    }

    public PathMatcher getIncludePattern() {
        return includePattern;
    }

    public String getIncludePatternString() {
        return includePatternString;
    }

    public PathMatcher getExcludePattern() {
        return excludePattern;
    }

    public String getExcludePatternString() {
        return excludePatternString;
    }

    public boolean isRemoteRestore() {
        return remoteRestore;
    }

    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public TransportType getTransportType() {
        return transportType;
    }

    @Override
    public String toString() {
        return "RestoreOptions{" +
                "overwriteExisting=" + overwriteExisting +
                ", backupExisting=" + backupExisting +
                ", verifyIntegrity=" + verifyIntegrity +
                ", preserveAttributes=" + preserveAttributes +
                ", includePatternString='" + includePatternString + '\'' +
                ", excludePatternString='" + excludePatternString + '\'' +
                '}';
    }

    /**
     * Builder for creating RestoreOptions instances.
     */
    public static class Builder {
        private boolean overwriteExisting = false;
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

        public Builder overwriteExisting(boolean overwriteExisting) {
            this.overwriteExisting = overwriteExisting;
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