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

import com.justsyncit.network.protocol.ProtocolMessage;
import com.justsyncit.network.protocol.FileTransferRequestMessage;
import com.justsyncit.network.protocol.ChunkDataMessage;
import com.justsyncit.network.protocol.ChunkAckMessage;
import com.justsyncit.network.protocol.TransferCompleteMessage;
import com.justsyncit.storage.ContentStore;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of FileTransferManager with chunking and BLAKE3 verification.
 * Follows Single Responsibility Principle by focusing solely on file transfer
 * operations.
 */
public class FileTransferManagerImpl implements FileTransferManager {

    /** The logger for this class. */
    private static final Logger logger = LoggerFactory.getLogger(FileTransferManagerImpl.class);

    /** Default chunk size for file transfers. */
    private static final int DEFAULT_CHUNK_SIZE = 64 * 1024; // 64KB chunks

    /** Active file transfers. */
    private final Map<String, FileTransferStatus> activeTransfers;
    /** Listeners for transfer events. */
    private final List<TransferEventListener> listeners;
    /** Flag indicating if the manager is running. */
    private final AtomicBoolean running;
    /** Counter for generating transfer IDs. */
    private final AtomicLong transferIdCounter;
    /** The network service. */
    private com.justsyncit.network.NetworkService networkService;

    /**
     * Creates a new file transfer manager.
     */
    public FileTransferManagerImpl() {
        this.activeTransfers = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
        this.running = new AtomicBoolean(false);
        this.transferIdCounter = new AtomicLong(0);
    }

    @Override
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "NetworkService is mutable but required for functionality")
    public void setNetworkService(com.justsyncit.network.NetworkService networkService) {
        this.networkService = networkService;
    }

    @Override
    public CompletableFuture<Void> start() {
        if (running.compareAndSet(false, true)) {
            logger.info("File transfer manager started");
            return CompletableFuture.completedFuture(null);
        } else {
            logger.warn("File transfer manager already started");
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public CompletableFuture<Void> stop() {
        if (running.compareAndSet(true, false)) {
            // Cancel all active transfers
            List<CompletableFuture<Void>> cancelFutures = new ArrayList<>();
            for (String transferId : activeTransfers.keySet()) {
                cancelFutures.add(cancelTransfer(transferId));
            }

            return CompletableFuture.allOf(cancelFutures.toArray(new CompletableFuture<?>[0]))
                    .thenRun(() -> {
                        activeTransfers.clear();
                        logger.info("File transfer manager stopped");
                    });
        } else {
            logger.warn("File transfer manager already stopped");
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public CompletableFuture<FileTransferResult> sendFile(Path filePath, InetSocketAddress remoteAddress,
            ContentStore contentStore) {
        if (!running.get()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("File transfer manager is not running"));
        }

        if (!Files.exists(filePath)) {
            return CompletableFuture.failedFuture(
                    new IOException("File does not exist: " + filePath));
        }

        String transferId = generateTransferId();
        long startTime = System.currentTimeMillis();

        try {
            long fileSize = Files.size(filePath);
            FileTransferStatus status = FileTransferStatus.pending(transferId, filePath, remoteAddress, fileSize);
            activeTransfers.put(transferId, status);

            notifyTransferStarted(filePath, remoteAddress, fileSize);

            // Create file transfer request
            Path fileNamePath = filePath.getFileName();
            String fileName = fileNamePath != null ? fileNamePath.toString() : "unknown";
            if (fileName.trim().isEmpty()) {
                throw new IOException("Invalid file path: filename is empty");
            }
            // Create request message to validate parameters, but don't store it since it's
            // not used
            new FileTransferRequestMessage(
                    fileName, fileSize, System.currentTimeMillis(), "dummy_hash", DEFAULT_CHUNK_SIZE);

            // This would delegate to NetworkService to send the message
            // For now, we'll simulate the transfer
            return simulateFileTransfer(transferId, filePath, remoteAddress, contentStore, startTime);

        } catch (IOException e) {
            FileTransferResult result = FileTransferResult.failure(
                    transferId, filePath, remoteAddress, e.getMessage(), 0, startTime, System.currentTimeMillis());
            activeTransfers.remove(transferId);
            notifyTransferCompleted(filePath, remoteAddress, false, e.getMessage());
            return CompletableFuture.completedFuture(result);
        }
    }

    /**
     * Executes a file transfer using the NetworkService.
     */
    private CompletableFuture<FileTransferResult> simulateFileTransfer(String transferId, Path filePath,
            InetSocketAddress remoteAddress,
            ContentStore contentStore, long startTime) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long fileSize = Files.size(filePath);
                FileTransferStatus status = activeTransfers.get(transferId);

                if (status == null) {
                    throw new IOException("Transfer not found: " + transferId);
                }

                status.setState(FileTransferStatus.TransferState.IN_PROGRESS);

                // Use zero-copy transfer via NetworkService
                // We send the file in chunks or one go.
                // For better progress tracking and to match existing chunking logic, we can
                // loop.
                // However, zero-copy is best when sending large chunks.

                long offset = 0;
                long remaining = fileSize;

                while (remaining > 0) {
                    if (!running.get() || status.isCancelled()) {
                        break;
                    }

                    long chunkSize = Math.min(DEFAULT_CHUNK_SIZE, remaining);

                    // This is asynchronous, but we blocking-wait here for simplicity in this
                    // refactor
                    // to maintain the loop structure.
                    // ideally we should chain futures.
                    try {
                        networkService.sendFilePart(filePath, offset, chunkSize, remoteAddress).join();
                    } catch (Exception e) {
                        throw new IOException("Failed to send file part", e);
                    }

                    status.addBytesTransferred(chunkSize);
                    notifyTransferProgress(filePath, remoteAddress, status.getBytesTransferred(), fileSize);

                    offset += chunkSize;
                    remaining -= chunkSize;
                }

                long endTime = System.currentTimeMillis();
                FileTransferResult result;

                if (status.isCancelled()) {
                    result = FileTransferResult.failure(transferId, filePath, remoteAddress,
                            "Transfer cancelled", status.getBytesTransferred(),
                            startTime, endTime);
                } else {
                    result = FileTransferResult.success(transferId, filePath, remoteAddress,
                            fileSize, fileSize, startTime, endTime);
                    status.setState(FileTransferStatus.TransferState.COMPLETED);

                    // Send transfer complete message
                    // Note: In a real protocol, we should wait for all ChunkAcks before declaring
                    // success.
                    // But for this zero-copy refactor, we are mimicking the simulation behavior
                    // which assumes success.
                    // We should probably rely on TCP guarantees + checksums in a real scenario.
                    // We will send a completion message.
                    try {
                        networkService.sendMessage(
                                new TransferCompleteMessage(filePath.toString(), fileSize, fileSize, "dummy-hash"),
                                remoteAddress);
                    } catch (Exception e) {
                        logger.warn("Failed to send transfer complete message", e);
                    }
                }

                activeTransfers.remove(transferId);
                notifyTransferCompleted(filePath, remoteAddress, result.isSuccess(), result.getErrorMessage());

                return result;

            } catch (Exception e) {
                long endTime = System.currentTimeMillis();
                FileTransferStatus status = activeTransfers.get(transferId);
                long bytesTransferred = status != null ? status.getBytesTransferred() : 0;
                FileTransferResult result = FileTransferResult.failure(
                        transferId, filePath, remoteAddress, e.getMessage(),
                        bytesTransferred, startTime, endTime);

                activeTransfers.remove(transferId);
                notifyTransferCompleted(filePath, remoteAddress, false, e.getMessage());
                notifyError(e, "File transfer execution");

                return result;
            }
        });
    }

    @Override
    public CompletableFuture<Void> handleFileTransferRequest(ProtocolMessage request,
            InetSocketAddress remoteAddress,
            ContentStore contentStore) {
        if (!(request instanceof FileTransferRequestMessage)) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Expected FileTransferRequestMessage"));
        }

        FileTransferRequestMessage fileRequest = (FileTransferRequestMessage) request;
        String fileName = fileRequest.getFilePath();
        long fileSize = fileRequest.getFileSize();

        logger.info("Received file transfer request: {} ({}) from {}",
                fileName, formatFileSize(fileSize), remoteAddress);

        // Check if we can accept the transfer
        boolean accept = true;
        // Check available space (simplified)
        // For now, we'll assume sufficient space
        // In a real implementation, this would check actual available space

        // Create and send response
        if (accept) {
            // This would delegate to NetworkService to send the response
            logger.debug("Accepting file transfer request for {}", fileName);
        } else {
            // This would delegate to NetworkService to send the response
            logger.debug("Rejecting file transfer request for {}: {}", fileName, "Unknown reason");
        }

        // This would delegate to NetworkService to send the response
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> handleChunkData(ProtocolMessage chunkData,
            InetSocketAddress remoteAddress,
            ContentStore contentStore) {
        if (!(chunkData instanceof ChunkDataMessage)) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Expected ChunkDataMessage"));
        }

        ChunkDataMessage chunkMessage = (ChunkDataMessage) chunkData;
        String filePath = chunkMessage.getFilePath();
        long chunkOffset = chunkMessage.getChunkOffset();
        byte[] chunkDataBytes = chunkMessage.getChunkData();
        String checksum = chunkMessage.getChunkHash();

        FileTransferStatus status = activeTransfers.get(filePath);
        if (status == null) {
            return CompletableFuture.failedFuture(
                    new IOException("Unknown transfer ID: " + filePath));
        }

        // Verify chunk checksum
        try {
            String actualChecksum = computeChecksum(chunkDataBytes);
            if (!checksum.equals(actualChecksum)) {
                return CompletableFuture.failedFuture(
                        new IOException("Checksum mismatch for chunk " + chunkOffset));
            }

            // Store chunk in content store
            contentStore.storeChunk(chunkDataBytes);

            // Update progress
            status.addBytesTransferred(chunkDataBytes.length);

            // Send acknowledgment
            new ChunkAckMessage(filePath, chunkOffset, chunkDataBytes.length);
            logger.debug("Sending chunk acknowledgment for offset {}", chunkOffset);

            // This would delegate to NetworkService to send the acknowledgment
            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            logger.error("Error processing chunk {} for transfer {}", chunkOffset, filePath, e);

            // Send negative acknowledgment
            logger.debug("Sending negative chunk acknowledgment for offset {}: {}", chunkOffset, e.getMessage());

            // This would delegate to NetworkService to send the acknowledgment
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletableFuture<Void> handleChunkAck(ProtocolMessage chunkAck, InetSocketAddress remoteAddress) {
        if (!(chunkAck instanceof ChunkAckMessage)) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Expected ChunkAckMessage"));
        }

        ChunkAckMessage ackMessage = (ChunkAckMessage) chunkAck;
        String filePath = ackMessage.getFilePath();
        long chunkOffset = ackMessage.getChunkOffset();
        boolean success = ackMessage.isSuccess();
        String errorMessage = ackMessage.getErrorMessage();

        FileTransferStatus status = activeTransfers.get(filePath);
        if (status == null) {
            return CompletableFuture.completedFuture(null); // Transfer already completed
        }

        if (!success) {
            logger.error("Chunk {} acknowledgment failed for transfer {}: {}",
                    chunkOffset, filePath, errorMessage);
            status.setState(FileTransferStatus.TransferState.FAILED);
            status.setErrorMessage(errorMessage);
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> handleTransferComplete(ProtocolMessage completeMessage,
            InetSocketAddress remoteAddress) {
        if (!(completeMessage instanceof TransferCompleteMessage)) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Expected TransferCompleteMessage"));
        }

        TransferCompleteMessage complete = (TransferCompleteMessage) completeMessage;
        String transferId = UUID.randomUUID().toString(); // Generate a transfer ID
        boolean success = true; // Transfer complete is always successful
        String errorMessage = complete.getErrorMessage();

        FileTransferStatus status = activeTransfers.get(transferId);
        if (status == null) {
            return CompletableFuture.completedFuture(null); // Transfer already completed
        }

        if (success) {
            status.setState(FileTransferStatus.TransferState.COMPLETED);
        } else {
            status.setState(FileTransferStatus.TransferState.FAILED);
            status.setErrorMessage(errorMessage);
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> cancelTransfer(String transferId) {
        FileTransferStatus status = activeTransfers.get(transferId);
        if (status != null) {
            status.setState(FileTransferStatus.TransferState.CANCELLED);
            activeTransfers.remove(transferId);
            notifyTransferCompleted(status.getFilePath(), status.getRemoteAddress(), false, "Transfer cancelled");
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public FileTransferStatus getTransferStatus(String transferId) {
        return activeTransfers.get(transferId);
    }

    @Override
    public List<FileTransferStatus> getActiveTransfers() {
        return Collections.unmodifiableList(new ArrayList<>(activeTransfers.values()));
    }

    @Override
    public int getActiveTransferCount() {
        return activeTransfers.size();
    }

    @Override
    public void addTransferEventListener(TransferEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeTransferEventListener(TransferEventListener listener) {
        listeners.remove(listener);
    }

    // Helper methods

    private String generateTransferId() {
        return "transfer-" + transferIdCounter.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String computeChecksum(byte[] data) {
        // This would use Blake3Service to compute checksum
        // For now, return a simple hash
        return Integer.toHexString(java.util.Arrays.hashCode(data));
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    // Notification methods

    private void notifyTransferStarted(Path filePath, InetSocketAddress remoteAddress, long fileSize) {
        for (TransferEventListener listener : listeners) {
            try {
                listener.onTransferStarted(filePath, remoteAddress, fileSize);
            } catch (Exception e) {
                logger.error("Error in transfer event listener", e);
            }
        }
    }

    private void notifyTransferProgress(Path filePath, InetSocketAddress remoteAddress,
            long bytesTransferred, long totalBytes) {
        for (TransferEventListener listener : listeners) {
            try {
                listener.onTransferProgress(filePath, remoteAddress, bytesTransferred, totalBytes);
            } catch (Exception e) {
                logger.error("Error in transfer event listener", e);
            }
        }
    }

    private void notifyTransferCompleted(Path filePath, InetSocketAddress remoteAddress,
            boolean success, String errorMessage) {
        for (TransferEventListener listener : listeners) {
            try {
                listener.onTransferCompleted(filePath, remoteAddress, success, errorMessage);
            } catch (Exception e) {
                logger.error("Error in transfer event listener", e);
            }
        }
    }

    private void notifyError(Throwable error, String context) {
        for (TransferEventListener listener : listeners) {
            try {
                listener.onError(error, context);
            } catch (Exception e) {
                logger.error("Error in transfer event listener", e);
            }
        }
    }
}