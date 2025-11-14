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

/**
 * Represents the result of a file transfer operation.
 * Follows the Single Responsibility Principle by focusing solely on
 * representing file transfer results.
 */
public class FileTransferResult {

    /** The unique transfer identifier. */
    private final String transferId;
    /** The file path being transferred. */
    private final Path filePath;
    /** The remote address of the transfer. */
    private final InetSocketAddress remoteAddress;
    /** Whether the transfer was successful. */
    private final boolean success;
    /** The error message if the transfer failed. */
    private final String errorMessage;
    /** The total file size in bytes. */
    private final long fileSize;
    /** The number of bytes transferred. */
    private final long bytesTransferred;
    /** The start time in milliseconds. */
    private final long startTime;
    /** The end time in milliseconds. */
    private final long endTime;
    /** The duration in milliseconds. */
    private final long duration;

    /**
     * Parameter object for file transfer result construction.
     */
    private static class TransferParams {
        /** The unique transfer identifier. */
        private final String transferId;
        /** The file path being transferred. */
        private final Path filePath;
        /** The remote address of the transfer. */
        private final InetSocketAddress remoteAddress;
        /** Whether the transfer was successful. */
        private final boolean success;
        /** The error message if the transfer failed. */
        private final String errorMessage;
        /** The total file size in bytes. */
        private final long fileSize;
        /** The number of bytes transferred. */
        private final long bytesTransferred;
        /** The start time in milliseconds. */
        private final long startTime;
        /** The end time in milliseconds. */
        private final long endTime;

        private TransferParams(TransferParamsBuilder builder) {
            this.transferId = builder.transferId;
            this.filePath = builder.filePath;
            this.remoteAddress = builder.remoteAddress;
            this.success = builder.success;
            this.errorMessage = builder.errorMessage;
            this.fileSize = builder.fileSize;
            this.bytesTransferred = builder.bytesTransferred;
            this.startTime = builder.startTime;
            this.endTime = builder.endTime;
        }

        /**
         * Builder for TransferParams.
         */
        public static class TransferParamsBuilder {
            /** The transfer ID. */
            private String transferId;
            /** The file path. */
            private Path filePath;
            /** The remote address. */
            private InetSocketAddress remoteAddress;
            /** Whether the transfer was successful. */
            private boolean success;
            /** The error message. */
            private String errorMessage;
            /** The file size. */
            private long fileSize;
            /** The bytes transferred. */
            private long bytesTransferred;
            /** The start time. */
            private long startTime;
            /** The end time. */
            private long endTime;

            public TransferParamsBuilder transferId(String transferId) {
                this.transferId = transferId;
                return this;
            }

            public TransferParamsBuilder filePath(Path filePath) {
                this.filePath = filePath;
                return this;
            }

            public TransferParamsBuilder remoteAddress(InetSocketAddress remoteAddress) {
                this.remoteAddress = remoteAddress;
                return this;
            }

            public TransferParamsBuilder success(boolean success) {
                this.success = success;
                return this;
            }

            public TransferParamsBuilder errorMessage(String errorMessage) {
                this.errorMessage = errorMessage;
                return this;
            }

            public TransferParamsBuilder fileSize(long fileSize) {
                this.fileSize = fileSize;
                return this;
            }

            public TransferParamsBuilder bytesTransferred(long bytesTransferred) {
                this.bytesTransferred = bytesTransferred;
                return this;
            }

            public TransferParamsBuilder startTime(long startTime) {
                this.startTime = startTime;
                return this;
            }

            public TransferParamsBuilder endTime(long endTime) {
                this.endTime = endTime;
                return this;
            }

            public TransferParams build() {
                return new TransferParams(this);
            }
        }
    }

    /**
     * Creates a new file transfer result.
     *
     * @param params the transfer parameters
     */
    private FileTransferResult(TransferParams params) {
        this.transferId = Objects.requireNonNull(params.transferId, "transferId cannot be null");
        this.filePath = Objects.requireNonNull(params.filePath, "filePath cannot be null");
        this.remoteAddress = Objects.requireNonNull(params.remoteAddress, "remoteAddress cannot be null");
        this.success = params.success;
        this.errorMessage = params.errorMessage;
        this.fileSize = params.fileSize;
        this.bytesTransferred = params.bytesTransferred;
        this.startTime = params.startTime;
        this.endTime = params.endTime;
        this.duration = params.endTime - params.startTime;
    }

    /**
     * Creates a new builder for FileTransferResult.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for FileTransferResult.
     */
    public static class Builder {
        /** The transfer ID. */
        private String transferId;
        /** The file path. */
        private Path filePath;
        /** The remote address. */
        private InetSocketAddress remoteAddress;
        /** Whether the transfer was successful. */
        private boolean success;
        /** The error message. */
        private String errorMessage;
        /** The file size. */
        private long fileSize;
        /** The bytes transferred. */
        private long bytesTransferred;
        /** The start time. */
        private long startTime;
        /** The end time. */
        private long endTime;

        private Builder() { }

        public Builder transferId(String transferId) {
            this.transferId = transferId;
            return this;
        }

        public Builder filePath(Path filePath) {
            this.filePath = filePath;
            return this;
        }

        public Builder remoteAddress(InetSocketAddress remoteAddress) {
            this.remoteAddress = remoteAddress;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder fileSize(long fileSize) {
            this.fileSize = fileSize;
            return this;
        }

        public Builder bytesTransferred(long bytesTransferred) {
            this.bytesTransferred = bytesTransferred;
            return this;
        }

        public Builder startTime(long startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(long endTime) {
            this.endTime = endTime;
            return this;
        }

        public FileTransferResult build() {
            TransferParams params = new TransferParams.TransferParamsBuilder()
                    .transferId(transferId)
                    .filePath(filePath)
                    .remoteAddress(remoteAddress)
                    .success(success)
                    .errorMessage(errorMessage)
                    .fileSize(fileSize)
                    .bytesTransferred(bytesTransferred)
                    .startTime(startTime)
                    .endTime(endTime)
                    .build();
            return new FileTransferResult(params);
        }
    }

    /**
     * Creates a successful file transfer result.
     *
     * @param transferId the transfer ID
     * @param filePath the file path
     * @param remoteAddress the remote address
     * @param fileSize the file size
     * @param bytesTransferred the number of bytes transferred
     * @param startTime the start time in milliseconds
     * @param endTime the end time in milliseconds
     * @return a successful file transfer result
     */
    public static FileTransferResult success(String transferId, Path filePath, InetSocketAddress remoteAddress,
                                       long fileSize, long bytesTransferred, long startTime, long endTime) {
        TransferParams params = new TransferParams.TransferParamsBuilder()
                    .transferId(transferId)
                    .filePath(filePath)
                    .remoteAddress(remoteAddress)
                    .success(true)
                    .errorMessage(null)
                    .fileSize(fileSize)
                    .bytesTransferred(bytesTransferred)
                    .startTime(startTime)
                    .endTime(endTime)
                    .build();
        return new FileTransferResult(params);
    }

    /**
     * Creates a failed file transfer result.
     *
     * @param transferId the transfer ID
     * @param filePath the file path
     * @param remoteAddress the remote address
     * @param errorMessage the error message
     * @param bytesTransferred the number of bytes transferred
     * @param startTime the start time in milliseconds
     * @param endTime the end time in milliseconds
     * @return a failed file transfer result
     */
    public static FileTransferResult failure(String transferId, Path filePath, InetSocketAddress remoteAddress,
                                       String errorMessage, long bytesTransferred, long startTime, long endTime) {
        TransferParams params = new TransferParams.TransferParamsBuilder()
                    .transferId(transferId)
                    .filePath(filePath)
                    .remoteAddress(remoteAddress)
                    .success(false)
                    .errorMessage(errorMessage)
                    .fileSize(0)
                    .bytesTransferred(bytesTransferred)
                    .startTime(startTime)
                    .endTime(endTime)
                    .build();
        return new FileTransferResult(params);
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
     * Checks if the transfer was successful.
     *
     * @return true if successful, false otherwise
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Gets the error message.
     *
     * @return the error message, or null if successful
     */
    public String getErrorMessage() {
        return errorMessage;
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
        return bytesTransferred;
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
        return Math.min(100.0, (bytesTransferred * 100.0) / fileSize);
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
     * Gets the end time.
     *
     * @return the end time in milliseconds
     */
    public long getEndTime() {
        return endTime;
    }

    /**
     * Gets the duration in milliseconds.
     *
     * @return the duration in milliseconds
     */
    public long getDuration() {
        return duration;
    }

    /**
     * Gets the transfer rate in bytes per second.
     *
     * @return the transfer rate in bytes per second
     */
    public double getTransferRate() {
        if (duration <= 0) {
            return 0.0;
        }
        return (bytesTransferred * 1000.0) / duration;
    }

    @Override
    public String toString() {
        return String.format("FileTransferResult{transferId='%s', filePath=%s, remoteAddress=%s, "
                               + "success=%s, fileSize=%d, bytesTransferred=%d, progress=%.2f%%, "
                               + "duration=%dms, rate=%.2f bytes/s}",
                               transferId, filePath, remoteAddress, success, fileSize,
                               bytesTransferred, getProgressPercentage(), duration, getTransferRate());
    }
}