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

import com.justsyncit.hash.Blake3Service;
import com.justsyncit.storage.ContentStore;
import com.justsyncit.storage.StorageIntegrityException;
import com.justsyncit.storage.metadata.FileMetadata;
import com.justsyncit.storage.metadata.MetadataService;
import com.justsyncit.storage.metadata.Snapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Service for orchestrating restore operations.
 * Follows Single Responsibility Principle by focusing only on restore workflow orchestration.
 */
public class RestoreService {

    /** Logger for restore operations. */
    private static final Logger logger = LoggerFactory.getLogger(RestoreService.class);

    /** Content store for retrieving chunks. */
    private final ContentStore contentStore;

    /** Metadata service for snapshot management. */
    private final MetadataService metadataService;

    /** BLAKE3 service for integrity verification. */
    private final Blake3Service blake3Service;

    /** Progress tracker for restore operations. */
    private RestoreProgressTracker progressTracker;

    /**
     * Creates a new RestoreService with required dependencies.
     *
     * @param contentStore the content store for retrieving chunks
     * @param metadataService the metadata service for snapshot management
     * @param blake3Service the BLAKE3 service for integrity verification
     * @throws IllegalArgumentException if any parameter is null
     */
    public RestoreService(ContentStore contentStore, MetadataService metadataService, Blake3Service blake3Service) {
        if (contentStore == null) {
            throw new IllegalArgumentException("Content store cannot be null");
        }
        if (metadataService == null) {
            throw new IllegalArgumentException("Metadata service cannot be null");
        }
        if (blake3Service == null) {
            throw new IllegalArgumentException("BLAKE3 service cannot be null");
        }

        this.contentStore = contentStore;
        this.metadataService = metadataService;
        this.blake3Service = blake3Service;
        this.progressTracker = new ConsoleRestoreProgressTracker();
    }

    /**
     * Performs a restore of the specified snapshot to the target directory.
     *
     * @param snapshotId the ID of the snapshot to restore
     * @param targetDirectory the directory to restore to
     * @param options restore options
     * @return a CompletableFuture that completes with the restore result
     */
    public CompletableFuture<RestoreResult> restore(String snapshotId, Path targetDirectory, RestoreOptions options) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Starting restore of snapshot: {} to directory: {}", snapshotId, targetDirectory);

                // Validate inputs
                if (snapshotId == null || snapshotId.trim().isEmpty()) {
                    throw new IllegalArgumentException("Snapshot ID cannot be null or empty");
                }
                if (targetDirectory == null) {
                    throw new IllegalArgumentException("Target directory cannot be null");
                }

                RestoreOptions finalOptions = options != null ? options : new RestoreOptions();

                // Get snapshot metadata
                Snapshot snapshot = getSnapshot(snapshotId);

                // Create target directory if it doesn't exist
                createTargetDirectory(targetDirectory);

                // Start progress tracking
                progressTracker.startRestore(snapshot, targetDirectory);

                // Get files in snapshot
                List<FileMetadata> files = metadataService.getFilesInSnapshot(snapshotId);

                // Restore files
                RestoreResult result = restoreFiles(files, targetDirectory, finalOptions);

                // Complete restore
                progressTracker.completeRestore(result);

                logger.info("Restore completed successfully. Files: {}, Size: {} bytes",
                        result.getFilesRestored(), result.getTotalBytesRestored());

                return result;

            } catch (RestoreException e) {
                logger.error("Restore failed", e);
                progressTracker.errorRestore(e);
                throw e;
            } catch (Exception e) {
                logger.error("Restore failed", e);
                progressTracker.errorRestore(e);
                throw new RestoreException("Restore operation failed", e);
            }
        });
    }

    /**
     * Gets snapshot metadata.
     */
    private Snapshot getSnapshot(String snapshotId) throws IOException {
        Optional<Snapshot> snapshotOpt = metadataService.getSnapshot(snapshotId);
        if (!snapshotOpt.isPresent()) {
            throw new IllegalArgumentException("Snapshot not found: " + snapshotId);
        }
        return snapshotOpt.get();
    }

    /**
     * Creates target directory if it doesn't exist.
     */
    private void createTargetDirectory(Path targetDirectory) throws IOException {
        if (!Files.exists(targetDirectory)) {
            Files.createDirectories(targetDirectory);
            logger.info("Created target directory: {}", targetDirectory);
        } else if (!Files.isDirectory(targetDirectory)) {
            throw new IllegalArgumentException("Target path exists but is not a directory: " + targetDirectory);
        }
    }

    /**
     * Restores files from snapshot to target directory.
     */
    private RestoreResult restoreFiles(List<FileMetadata> files, Path targetDirectory, RestoreOptions options) {
        int filesRestored = 0;
        int filesSkipped = 0;
        int filesWithErrors = 0;
        long totalBytesRestored = 0;
        long totalFiles = files.size();

        progressTracker.updateProgress(0, totalFiles, 0, -1, null);

        for (int i = 0; i < files.size(); i++) {
            FileMetadata file = files.get(i);

            try {
                progressTracker.updateProgress(i, totalFiles, totalBytesRestored, -1, file.getPath());

                if (shouldRestoreFile(file, options)) {
                    restoreFile(file, targetDirectory, options);
                    filesRestored++;
                    totalBytesRestored += file.getSize();
                } else {
                    filesSkipped++;
                    progressTracker.fileSkipped(file.getPath(), "Skipped by user options");
                }

            } catch (Exception e) {
                logger.error("Failed to restore file: {}", file.getPath(), e);
                filesWithErrors++;
                progressTracker.fileError(file.getPath(), e);
            }
        }

        // Verify integrity if requested
        boolean integrityVerified = false;
        if (options.isVerifyIntegrity()) {
            integrityVerified = verifyRestoreIntegrity(files, targetDirectory);
        }

        return RestoreResult.create(
                filesRestored,
                filesSkipped,
                filesWithErrors,
                totalBytesRestored,
                integrityVerified
        );
    }

    /**
     * Determines if a file should be restored based on options.
     */
    private boolean shouldRestoreFile(FileMetadata file, RestoreOptions options) {
        // Apply include/exclude patterns
        if (options.getIncludePattern() != null
            && !options.getIncludePattern().matches(Path.of(file.getPath()))) {
            return false;
        }

        if (options.getExcludePattern() != null
            && options.getExcludePattern().matches(Path.of(file.getPath()))) {
            return false;
        }

        return true;
    }

    /**
     * Restores a single file.
     */
    private void restoreFile(FileMetadata fileMetadata, Path targetDirectory, RestoreOptions options) throws IOException {
        Path targetFile = targetDirectory.resolve(fileMetadata.getPath());

        // Create parent directories if needed
        Path parentDir = targetFile.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        // Check if file already exists
        if (Files.exists(targetFile)) {
            if (!options.isOverwriteExisting()) {
                throw new IOException("Target file already exists: " + targetFile);
            }

            if (options.isBackupExisting()) {
                backupExistingFile(targetFile);
            }
        }

        // Reconstruct file from chunks
        reconstructFileFromChunks(fileMetadata, targetFile);

        // Set file permissions and timestamps
        if (options.isPreserveAttributes()) {
            preserveFileAttributes(targetFile, fileMetadata);
        }
    }

    /**
     * Reconstructs a file from its chunks.
     */
    private void reconstructFileFromChunks(FileMetadata fileMetadata, Path targetFile) throws IOException {
        try {
            // Create file and write chunks
            java.io.FileOutputStream outputStream = new java.io.FileOutputStream(targetFile.toFile());

            for (String chunkHash : fileMetadata.getChunkHashes()) {
                byte[] chunkData = contentStore.retrieveChunk(chunkHash);
                if (chunkData == null) {
                    throw new IOException("Chunk not found in content store: " + chunkHash);
                }

                // Verify chunk integrity
                if (!verifyChunkIntegrity(chunkHash, chunkData)) {
                    throw new StorageIntegrityException("Chunk integrity verification failed: " + chunkHash);
                }

                outputStream.write(chunkData);
            }

            outputStream.close();

            // Verify file integrity
            String actualHash = blake3Service.hashFile(targetFile);
            if (!actualHash.equals(fileMetadata.getFileHash())) {
                throw new StorageIntegrityException("File integrity verification failed for: " + targetFile);
            }

            logger.debug("Successfully restored file: {}", targetFile);

        } catch (StorageIntegrityException e) {
            throw new RestoreException("Storage integrity error", e);
        } catch (Exception e) {
            throw new IOException("Failed to reconstruct file from chunks: " + targetFile, e);
        }
    }

    /**
     * Verifies chunk integrity.
     */
    private boolean verifyChunkIntegrity(String expectedHash, byte[] chunkData) {
        try {
            String actualHash = blake3Service.hashBuffer(chunkData);
            return expectedHash.equals(actualHash);
        } catch (Exception e) {
            logger.warn("Failed to verify chunk integrity: {}", expectedHash, e);
            return false;
        }
    }

    /**
     * Backs up an existing file.
     */
    private void backupExistingFile(Path targetFile) throws IOException {
        Path backupFile = Path.of(targetFile.toString() + ".justsyncit.backup." + System.currentTimeMillis());
        Files.copy(targetFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
        logger.info("Backed up existing file to: {}", backupFile);
    }

    /**
     * Preserves file attributes.
     */
    private void preserveFileAttributes(Path targetFile, FileMetadata fileMetadata) throws IOException {
        // Set last modified time
        if (fileMetadata.getModifiedTime() != null) {
            Files.setLastModifiedTime(targetFile,
                    FileTime.fromMillis(fileMetadata.getModifiedTime().toEpochMilli()));
        }

        // Note: In a real implementation, we might also preserve permissions,
        // ownership, and other attributes based on the platform
    }

    /**
     * Verifies restore integrity.
     */
    private boolean verifyRestoreIntegrity(List<FileMetadata> originalFiles, Path targetDirectory) {
        try {
            for (FileMetadata originalFile : originalFiles) {
                Path restoredFile = targetDirectory.resolve(originalFile.getPath());
                if (Files.exists(restoredFile)) {
                    String actualHash = blake3Service.hashFile(restoredFile);
                    if (!actualHash.equals(originalFile.getFileHash())) {
                        logger.error("Integrity verification failed for file: {}", restoredFile);
                        return false;
                    }
                }
            }
            return true;
        } catch (Exception e) {
            logger.error("Failed to verify restore integrity", e);
            return false;
        }
    }

    /**
     * Sets the progress tracker for restore operations.
     *
     * @param progressTracker the progress tracker to use
     */
    public void setProgressTracker(RestoreProgressTracker progressTracker) {
        this.progressTracker = progressTracker != null ? progressTracker : new ConsoleRestoreProgressTracker();
    }

    /**
     * Gets the current progress tracker.
     *
     * @return the current progress tracker
     */
    public RestoreProgressTracker getProgressTracker() {
        return progressTracker;
    }

    /**
     * Result of a restore operation.
     */
    public static class RestoreResult {
        /** Number of files successfully restored. */
        private final int filesRestored;
        /** Number of files skipped. */
        private final int filesSkipped;
        /** Number of files with errors. */
        private final int filesWithErrors;
        /** Total bytes restored. */
        private final long totalBytesRestored;
        /** Whether integrity was verified. */
        private final boolean integrityVerified;

        /**
         * Creates a new RestoreResult.
         */
        private RestoreResult(int filesRestored, int filesSkipped, int filesWithErrors,
                           long totalBytesRestored, boolean integrityVerified) {
            this.filesRestored = filesRestored;
            this.filesSkipped = filesSkipped;
            this.filesWithErrors = filesWithErrors;
            this.totalBytesRestored = totalBytesRestored;
            this.integrityVerified = integrityVerified;
        }

        /**
         * Creates a new RestoreResult.
         *
         * @param filesRestored number of files restored
         * @param filesSkipped number of files skipped
         * @param filesWithErrors number of files with errors
         * @param totalBytesRestored total bytes restored
         * @param integrityVerified whether integrity was verified
         * @return a new RestoreResult instance
         */
        public static RestoreResult create(int filesRestored, int filesSkipped, int filesWithErrors,
                                         long totalBytesRestored, boolean integrityVerified) {
            return new RestoreResult(filesRestored, filesSkipped, filesWithErrors,
                    totalBytesRestored, integrityVerified);
        }

        public int getFilesRestored() {
            return filesRestored;
        }

        public int getFilesSkipped() {
            return filesSkipped;
        }

        public int getFilesWithErrors() {
            return filesWithErrors;
        }

        public long getTotalBytesRestored() {
            return totalBytesRestored;
        }

        public boolean isIntegrityVerified() {
            return integrityVerified;
        }

        public boolean isSuccess() {
            return filesWithErrors == 0 && integrityVerified;
        }

        public long getDuration() {
            return 0; // TODO: Implement duration tracking
        }
    }
}