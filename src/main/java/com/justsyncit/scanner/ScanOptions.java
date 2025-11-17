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

package com.justsyncit.scanner;

import java.nio.file.PathMatcher;

/**
 * Configuration options for filesystem scanning operations.
 * Follows Builder Pattern for flexible configuration.
 */
public class ScanOptions {

    /** Default maximum scan depth. */
    private static final int DEFAULT_MAX_DEPTH = Integer.MAX_VALUE;

    /** Pattern for files to include in scanning. */
    private PathMatcher includePattern;
    /** Pattern for files to exclude from scanning. */
    private PathMatcher excludePattern;
    /** Strategy for handling symbolic links. */
    private SymlinkStrategy symlinkStrategy = SymlinkStrategy.SKIP;
    /** Whether to follow symbolic links (deprecated, use symlinkStrategy instead). */
    @Deprecated
    private boolean followLinks = false;
    /** Maximum depth for recursive scanning. */
    private int maxDepth = DEFAULT_MAX_DEPTH;
    /** Whether to detect and handle sparse files. */
    private boolean detectSparseFiles = true;
    /** Whether to scan hidden files. */
    private boolean includeHiddenFiles = false;
    /** Maximum file size to process (bytes). */
    private long maxFileSize = Long.MAX_VALUE;
    /** Minimum file size to process (bytes). */
    private long minFileSize = 0;

    /**
     * Creates a new ScanOptions with default values.
     */
    public ScanOptions() {
        // Default constructor
    }

    /**
     * Creates a new ScanOptions as a copy of another.
     *
     * @param other options to copy
     */
    public ScanOptions(ScanOptions other) {
        this.includePattern = other.includePattern;
        this.excludePattern = other.excludePattern;
        this.symlinkStrategy = other.symlinkStrategy;
        this.followLinks = other.followLinks;
        this.maxDepth = other.maxDepth;
        this.detectSparseFiles = other.detectSparseFiles;
        this.includeHiddenFiles = other.includeHiddenFiles;
        this.maxFileSize = other.maxFileSize;
        this.minFileSize = other.minFileSize;
    }

    /**
     * Sets the pattern for files to include.
     *
     * @param includePattern include pattern
     * @return this builder for method chaining
     */
    public ScanOptions withIncludePattern(PathMatcher includePattern) {
        this.includePattern = includePattern;
        return this;
    }

    /**
     * Sets the pattern for files to exclude.
     *
     * @param excludePattern exclude pattern
     * @return this builder for method chaining
     */
    public ScanOptions withExcludePattern(PathMatcher excludePattern) {
        this.excludePattern = excludePattern;
        return this;
    }

    /**
     * Sets the strategy for handling symbolic links.
     *
     * @param symlinkStrategy symlink strategy
     * @return this builder for method chaining
     */
    public ScanOptions withSymlinkStrategy(SymlinkStrategy symlinkStrategy) {
        this.symlinkStrategy = symlinkStrategy;
        return this;
    }

    /**
     * Sets whether to follow symbolic links.
     *
     * @param followLinks whether to follow links
     * @return this builder for method chaining
     * @deprecated Use withSymlinkStrategy() instead
     */
    @Deprecated
    public ScanOptions withFollowLinks(boolean followLinks) {
        this.followLinks = followLinks;
        this.symlinkStrategy = followLinks ? SymlinkStrategy.FOLLOW : SymlinkStrategy.SKIP;
        return this;
    }

    /**
     * Sets the maximum scan depth.
     *
     * @param maxDepth maximum depth (0 = no recursion)
     * @return this builder for method chaining
     */
    public ScanOptions withMaxDepth(int maxDepth) {
        if (maxDepth < 0) {
            throw new IllegalArgumentException("Max depth cannot be negative");
        }
        this.maxDepth = maxDepth;
        return this;
    }

    /**
     * Sets whether to detect sparse files.
     *
     * @param detectSparseFiles whether to detect sparse files
     * @return this builder for method chaining
     */
    public ScanOptions withDetectSparseFiles(boolean detectSparseFiles) {
        this.detectSparseFiles = detectSparseFiles;
        return this;
    }

    /**
     * Sets whether to include hidden files.
     *
     * @param includeHiddenFiles whether to include hidden files
     * @return this builder for method chaining
     */
    public ScanOptions withIncludeHiddenFiles(boolean includeHiddenFiles) {
        this.includeHiddenFiles = includeHiddenFiles;
        return this;
    }

    /**
     * Sets the maximum file size to process.
     *
     * @param maxFileSize maximum file size in bytes
     * @return this builder for method chaining
     */
    public ScanOptions withMaxFileSize(long maxFileSize) {
        if (maxFileSize < 0) {
            throw new IllegalArgumentException("Max file size cannot be negative");
        }
        this.maxFileSize = maxFileSize;
        return this;
    }

    /**
     * Sets the minimum file size to process.
     *
     * @param minFileSize minimum file size in bytes
     * @return this builder for method chaining
     */
    public ScanOptions withMinFileSize(long minFileSize) {
        if (minFileSize < 0) {
            throw new IllegalArgumentException("Min file size cannot be negative");
        }
        this.minFileSize = minFileSize;
        return this;
    }

    // Getters

    public PathMatcher getIncludePattern() {
        return includePattern;
    }

    public PathMatcher getExcludePattern() {
        return excludePattern;
    }

    public SymlinkStrategy getSymlinkStrategy() {
        return symlinkStrategy;
    }

    @Deprecated
    public boolean isFollowLinks() {
        return followLinks;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public boolean isDetectSparseFiles() {
        return detectSparseFiles;
    }

    public boolean isIncludeHiddenFiles() {
        return includeHiddenFiles;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public long getMinFileSize() {
        return minFileSize;
    }
}