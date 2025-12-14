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

package com.justsyncit.network.transfer;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents the status of an ongoing file transfer.
 * Follows the Single Responsibility Principle by focusing solely on
 * representing file transfer status.
 */
public class FileTransferStatus {

    /**
     * Enumeration of transfer states.
     */
    public enum TransferState {
        /**
         * Transfer is pending.
         */
        PENDING,

        /**
         * Transfer is in progress.
         */
        IN_PROGRESS,

        /**
         * Transfer is completed successfully.
         */
        COMPLETED,

        /**
         * Transfer failed.
         */
        FAILED,

        /**
         * Transfer was cancelled.
         */
        CANCELLED
    }

    /** The unique identifier for this transfer. */
    private final String transferId;
    /** The path to the file being transferred. */
    private final Path filePath;
    /** The remote address of the peer. */
    private final InetSocketAddress remoteAddress;
    /** The total size of the file in bytes. */
    private final long fileSize;
    /** The number of bytes transferred so far. */
    private final AtomicLong bytesTransferred;
    /** The timestamp when the transfer started. */
    private final long startTime;
    /** The current state of the transfer. */
    private volatile TransferState state;
    /** The error message if the transfer failed. */
    private volatile String errorMessage;
    /** The timestamp of the last update. */
    private volatile long lastUpdateTime;
    /** The compression type used for this transfer. */
    private final String compressionType;

    /**
     * Creates a new file transfer status.
     *
     * @param transferId    the transfer ID
     * @param filePath      the file path
     * @param remoteAddress the remote address
     * @param fileSize      the file size
     * @param state         the initial state
     */
    public FileTransferStatus(String transferId, Path filePath, InetSocketAddress remoteAddress,
            long fileSize, TransferState state) {
        this(transferId, filePath, remoteAddress, fileSize, state, "NONE");
    }

    /**
     * Creates a new file transfer status with compression type.
     *
     * @param transferId      the transfer ID
     * @param filePath        the file path
     * @param remoteAddress   the remote address
     * @param fileSize        the file size
     * @param state           the initial state
     * @param compressionType the compression type
     */
    public FileTransferStatus(String transferId, Path filePath, InetSocketAddress remoteAddress,
            long fileSize, TransferState state, String compressionType) {
        this.transferId = Objects.requireNonNull(transferId, "transferId cannot be null");
        this.filePath = Objects.requireNonNull(filePath, "filePath cannot be null");
        this.remoteAddress = Objects.requireNonNull(remoteAddress, "remoteAddress cannot be null");
        this.fileSize = fileSize;
        this.bytesTransferred = new AtomicLong(0);
        this.startTime = System.currentTimeMillis();
        this.state = Objects.requireNonNull(state, "state cannot be null");
        this.lastUpdateTime = startTime;
        this.compressionType = Objects.requireNonNull(compressionType, "compressionType cannot be null");
    }

    /**
     * Creates a new file transfer status in PENDING state.
     *
     * @param transferId    the transfer ID
     * @param filePath      the file path
     * @param remoteAddress the remote address
     * @param fileSize      the file size
     * @return a new file transfer status
     */
    public static FileTransferStatus pending(String transferId, Path filePath,
            InetSocketAddress remoteAddress, long fileSize) {
        return new FileTransferStatus(transferId, filePath, remoteAddress, fileSize, TransferState.PENDING, "NONE");
    }

    /**
     * Creates a new file transfer status in PENDING state with compression.
     *
     * @param transferId      the transfer ID
     * @param filePath        the file path
     * @param remoteAddress   the remote address
     * @param fileSize        the file size
     * @param compressionType the compression type
     * @return a new file transfer status
     */
    public static FileTransferStatus pending(String transferId, Path filePath,
            InetSocketAddress remoteAddress, long fileSize, String compressionType) {
        return new FileTransferStatus(transferId, filePath, remoteAddress, fileSize, TransferState.PENDING,
                compressionType);
    }

    /**
     * Gets the transfer ID.
     *
     * @return the transfer ID
     */
    public String getTransferId() {
        return transferId;
    }

    /**
     * Gets the file path.
     *
     * @return the file path
     */
    public Path getFilePath() {
        return filePath;
    }

    /**
     * Gets the remote address.
     *
     * @return the remote address
     */
    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    /**
     * Gets the compression type.
     * 
     * @return the compression type
     */
    public String getCompressionType() {
        return compressionType;
    }

    /**
     * Gets the file size.
     *
     * @return the file size
     */
    public long getFileSize() {
        return fileSize;
    }

    /**
     * Gets the number of bytes transferred.
     *
     * @return the number of bytes transferred
     */
    public long getBytesTransferred() {
        return bytesTransferred.get();
    }

    /**
     * Adds to the number of bytes transferred.
     *
     * @param bytes the number of bytes to add
     * @return the new total bytes transferred
     */
    public long addBytesTransferred(long bytes) {
        long newTotal = bytesTransferred.addAndGet(bytes);
        lastUpdateTime = System.currentTimeMillis();
        return newTotal;
    }

    /**
     * Gets the transfer progress percentage.
     *
     * @return the progress percentage (0-100)
     */
    public double getProgressPercentage() {
        if (fileSize <= 0) {
            return 0.0;
        }
        return Math.min(100.0, (getBytesTransferred() * 100.0) / fileSize);
    }

    /**
     * Gets the transfer state.
     *
     * @return the transfer state
     */
    public TransferState getState() {
        return state;
    }

    /**
     * Sets the transfer state.
     *
     * @param state the new transfer state
     */
    public void setState(TransferState state) {
        this.state = Objects.requireNonNull(state, "state cannot be null");
        this.lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Gets the error message.
     *
     * @return the error message, or null if no error
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Sets the error message.
     *
     * @param errorMessage the error message
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Gets the start time.
     *
     * @return the start time in milliseconds
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * Gets the last update time.
     *
     * @return the last update time in milliseconds
     */
    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    /**
     * Gets the elapsed time in milliseconds.
     *
     * @return the elapsed time in milliseconds
     */
    public long getElapsedTime() {
        return lastUpdateTime - startTime;
    }

    /**
     * Gets the current transfer rate in bytes per second.
     *
     * @return the transfer rate in bytes per second
     */
    public double getCurrentTransferRate() {
        long elapsedTime = getElapsedTime();
        if (elapsedTime <= 0) {
            return 0.0;
        }
        return (getBytesTransferred() * 1000.0) / elapsedTime;
    }

    /**
     * Checks if the transfer is active (not completed, failed, or cancelled).
     *
     * @return true if active, false otherwise
     */
    public boolean isActive() {
        return state == TransferState.PENDING || state == TransferState.IN_PROGRESS;
    }

    /**
     * Checks if the transfer is completed successfully.
     *
     * @return true if completed successfully, false otherwise
     */
    public boolean isCompleted() {
        return state == TransferState.COMPLETED;
    }

    /**
     * Checks if the transfer has failed.
     *
     * @return true if failed, false otherwise
     */
    public boolean isFailed() {
        return state == TransferState.FAILED;
    }

    /**
     * Checks if the transfer was cancelled.
     *
     * @return true if cancelled, false otherwise
     */
    public boolean isCancelled() {
        return state == TransferState.CANCELLED;
    }

    @Override
    public String toString() {
        return String.format("FileTransferStatus{transferId='%s', filePath=%s, remoteAddress=%s, "
                + "fileSize=%d, bytesTransferred=%d, progress=%.2f%%, state=%s, "
                + "elapsedTime=%dms, rate=%.2f bytes/s}",
                transferId, filePath, remoteAddress, fileSize, getBytesTransferred(),
                getProgressPercentage(), state, getElapsedTime(), getCurrentTransferRate());
    }
}