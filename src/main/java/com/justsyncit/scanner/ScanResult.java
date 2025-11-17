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

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Result of a filesystem scanning operation.
 * Follows Single Responsibility Principle by containing only scan result data.
 */
public class ScanResult {

    /** The root directory that was scanned. */
    private final Path rootDirectory;
    /** List of files that were successfully scanned. */
    private final List<ScannedFile> scannedFiles;
    /** List of errors that occurred during scanning. */
    private final List<ScanError> errors;
    /** Timestamp when the scan started. */
    private final Instant startTime;
    /** Timestamp when the scan completed. */
    private final Instant endTime;
    /** Additional metadata about the scan. */
    private final Map<String, Object> metadata;

    /**
     * Creates a new ScanResult.
     *
     * @param rootDirectory the root directory that was scanned
     * @param scannedFiles list of successfully scanned files
     * @param errors list of errors that occurred
     * @param startTime when the scan started
     * @param endTime when the scan completed
     * @param metadata additional scan metadata
     */
    public ScanResult(Path rootDirectory, List<ScannedFile> scannedFiles, List<ScanError> errors,
                   Instant startTime, Instant endTime, Map<String, Object> metadata) {
        this.rootDirectory = rootDirectory;
        this.scannedFiles = scannedFiles;
        this.errors = errors;
        this.startTime = startTime;
        this.endTime = endTime;
        this.metadata = metadata;
    }

    /**
     * Gets the root directory that was scanned.
     *
     * @return the root directory
     */
    public Path getRootDirectory() {
        return rootDirectory;
    }

    /**
     * Gets the list of successfully scanned files.
     *
     * @return immutable list of scanned files
     */
    public List<ScannedFile> getScannedFiles() {
        return scannedFiles;
    }

    /**
     * Gets the list of errors that occurred during scanning.
     *
     * @return immutable list of scan errors
     */
    public List<ScanError> getErrors() {
        return errors;
    }

    /**
     * Gets the timestamp when the scan started.
     *
     * @return the start time
     */
    public Instant getStartTime() {
        return startTime;
    }

    /**
     * Gets the timestamp when the scan completed.
     *
     * @return the end time
     */
    public Instant getEndTime() {
        return endTime;
    }

    /**
     * Gets the duration of the scan.
     *
     * @return the scan duration in milliseconds
     */
    public long getDurationMillis() {
        return java.time.Duration.between(startTime, endTime).toMillis();
    }

    /**
     * Gets the total number of files scanned.
     *
     * @return the number of scanned files
     */
    public int getScannedFileCount() {
        return scannedFiles.size();
    }

    /**
     * Gets the total number of errors.
     *
     * @return the number of errors
     */
    public int getErrorCount() {
        return errors.size();
    }

    /**
     * Gets the total size of all scanned files.
     *
     * @return the total size in bytes
     */
    public long getTotalSize() {
        return scannedFiles.stream()
                .mapToLong(ScannedFile::getSize)
                .sum();
    }

    /**
     * Gets additional metadata about the scan.
     *
     * @return the metadata map
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Represents a file that was successfully scanned.
     */
    public static class ScannedFile {
        private final Path path;
        private final long size;
        private final Instant lastModified;
        private final boolean isSymbolicLink;
        private final boolean isSparse;
        private final Path linkTarget;

        /**
         * Creates a new ScannedFile.
         *
         * @param path the file path
         * @param size the file size
         * @param lastModified the last modified time
         * @param isSymbolicLink whether the file is a symbolic link
         * @param isSparse whether the file is sparse
         * @param linkTarget the symbolic link target (if applicable)
         */
        public ScannedFile(Path path, long size, Instant lastModified, boolean isSymbolicLink,
                        boolean isSparse, Path linkTarget) {
            this.path = path;
            this.size = size;
            this.lastModified = lastModified;
            this.isSymbolicLink = isSymbolicLink;
            this.isSparse = isSparse;
            this.linkTarget = linkTarget;
        }

        public Path getPath() {
            return path;
        }

        public long getSize() {
            return size;
        }

        public Instant getLastModified() {
            return lastModified;
        }

        public boolean isSymbolicLink() {
            return isSymbolicLink;
        }

        public boolean isSparse() {
            return isSparse;
        }

        public Path getLinkTarget() {
            return linkTarget;
        }
    }

    /**
     * Represents an error that occurred during scanning.
     */
    public static class ScanError {
        private final Path path;
        private final Exception exception;
        private final String message;

        /**
         * Creates a new ScanError.
         *
         * @param path the path where the error occurred
         * @param exception the exception that was thrown
         * @param message a descriptive error message
         */
        public ScanError(Path path, Exception exception, String message) {
            this.path = path;
            this.exception = exception;
            this.message = message;
        }

        public Path getPath() {
            return path;
        }

        public Exception getException() {
            return exception;
        }

        public String getMessage() {
            return message;
        }
    }
}