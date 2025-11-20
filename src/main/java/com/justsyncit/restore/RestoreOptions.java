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
    
    /** Exclude pattern for files to skip. */
    private final PathMatcher excludePattern;
    
    /**
     * Creates a new RestoreOptions.
     */
    private RestoreOptions(Builder builder) {
        this.overwriteExisting = builder.overwriteExisting;
        this.backupExisting = builder.backupExisting;
        this.verifyIntegrity = builder.verifyIntegrity;
        this.preserveAttributes = builder.preserveAttributes;
        this.includePattern = builder.includePattern;
        this.excludePattern = builder.excludePattern;
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
        this.excludePattern = null;
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
    
    public PathMatcher getExcludePattern() {
        return excludePattern;
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
            }
            return this;
        }
        
        public Builder excludePattern(String pattern) {
            if (pattern != null && !pattern.trim().isEmpty()) {
                this.excludePattern = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            }
            return this;
        }
        
        public RestoreOptions build() {
            return new RestoreOptions(this);
        }
    }
}