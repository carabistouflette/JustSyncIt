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

import com.justsyncit.network.compression.CompressionService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

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
    private static final String PLACEHOLDER_HASH = "pending_calculation";
    /** Flag indicating if the manager is running. */
    private final AtomicBoolean running;
    /** Counter for generating transfer IDs. */
    private final AtomicLong transferIdCounter;
    /** The network service. */
    private com.justsyncit.network.NetworkService networkService;
    /** The compression service. */
    private CompressionService compressionService;
    /** Executor for parallel decompression tasks. */
    // private final java.util.concurrent.ExecutorService decompressionExecutor; //
    // Removed in favor of ThreadPoolManager

    /** Configuration: Compression enabled. */
    private boolean compressionEnabled = true;

    /** Configuration: Compression level. */
    private int compressionLevel = 3;

    /** Pipeline manager. */
    private final com.justsyncit.network.transfer.pipeline.PipelineManager pipelineManager;

    /**
     * Creates a new file transfer manager.
     */
    public FileTransferManagerImpl() {
        this.activeTransfers = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
        this.running = new AtomicBoolean(false);
        this.transferIdCounter = new AtomicLong(0);
        // Use ThreadPoolManager instead of custom executor
        // this.decompressionExecutor = ...

        // Initialize pipeline manager
        this.pipelineManager = new com.justsyncit.network.transfer.pipeline.PipelineManager();
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
                String compressionType = status.getCompressionType();
                boolean useCompression = !"NONE".equals(compressionType) && compressionService != null;

                // --- NEW PIPELINE EXECUTION ---

                // Create stages
                java.util.concurrent.ExecutorService pipelineExecutor = pipelineManager.getExecutor();

                com.justsyncit.network.transfer.pipeline.ReadStage readStage = new com.justsyncit.network.transfer.pipeline.ReadStage(
                        pipelineExecutor);

                com.justsyncit.network.transfer.pipeline.HashStage hashStage = new com.justsyncit.network.transfer.pipeline.HashStage(
                        pipelineExecutor);

                com.justsyncit.network.transfer.pipeline.CompressStage compressStage = new com.justsyncit.network.transfer.pipeline.CompressStage(
                        pipelineExecutor, compressionService, useCompression);

                com.justsyncit.network.transfer.pipeline.SendStage sendStage = new com.justsyncit.network.transfer.pipeline.SendStage(
                        pipelineExecutor, networkService, remoteAddress);

                com.justsyncit.network.transfer.pipeline.TransferPipeline pipeline = new com.justsyncit.network.transfer.pipeline.TransferPipeline(
                        readStage, hashStage, compressStage, sendStage);

                long offset = 0;
                long remaining = fileSize;
                List<CompletableFuture<Void>> chunkFutures = new ArrayList<>();

                while (remaining > 0) {
                    if (!running.get() || status.isCancelled()) {
                        break;
                    }

                    long chunkSize = Math.min(DEFAULT_CHUNK_SIZE, remaining);

                    // Create task
                    com.justsyncit.network.transfer.pipeline.ChunkTask task = new com.justsyncit.network.transfer.pipeline.ChunkTask(
                            transferId, filePath, offset, (int) chunkSize, fileSize);

                    // Submit to pipeline (this will handle backpressure automatically)
                    CompletableFuture<Void> f = pipeline.submit(task);
                    chunkFutures.add(f);

                    // Update stats (optimistic updates, although real updates happen in stages,
                    // for the status object we update here to keep UI "moving" as we submit)
                    // Note: Ideally stages should callback to update status, but for minimal
                    // changes we keep this.
                    status.addBytesTransferred(chunkSize);
                    notifyTransferProgress(filePath, remoteAddress, status.getBytesTransferred(), fileSize);

                    offset += chunkSize;
                    remaining -= chunkSize;
                }

                // Wait for all pipeline tasks to finish
                pipeline.waitForCompletion().join(); // This joins on internal pipeline futures

                // --- END PIPELINE EXECUTION ---

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

                    try {
                        networkService.sendMessage(
                                new TransferCompleteMessage(filePath.toString(), fileSize, fileSize, PLACEHOLDER_HASH),
                                remoteAddress).orTimeout(30, TimeUnit.SECONDS).join();
                    } catch (Exception e) {
                        logger.warn("Failed to send transfer complete message", e);
                    }
                }

                activeTransfers.remove(transferId);
                notifyTransferCompleted(filePath, remoteAddress, result.isSuccess(), result.getErrorMessage());

                return result;

            } catch (IOException | java.util.concurrent.CompletionException e) {
                long endTime = System.currentTimeMillis();
                FileTransferStatus status = activeTransfers.get(transferId);
                long bytesTransferred = status != null ? status.getBytesTransferred() : 0;

                // Unwrap CompletionException if present
                Throwable cause = e instanceof java.util.concurrent.CompletionException ? e.getCause() : e;
                String errorMessage = cause != null ? cause.getMessage() : e.getMessage();

                FileTransferResult result = FileTransferResult.failure(
                        transferId, filePath, remoteAddress, errorMessage,
                        bytesTransferred, startTime, endTime);

                activeTransfers.remove(transferId);
                notifyTransferCompleted(filePath, remoteAddress, false, errorMessage);
                notifyError(cause != null ? cause : e, "File transfer execution");

                return result;
            } catch (Exception e) {
                // Catch-all for unexpected runtime exceptions to ensure we don't hang
                long endTime = System.currentTimeMillis();
                FileTransferStatus status = activeTransfers.get(transferId);
                long bytesTransferred = status != null ? status.getBytesTransferred() : 0;

                FileTransferResult result = FileTransferResult.failure(
                        transferId, filePath, remoteAddress, "Unexpected error: " + e.getMessage(),
                        bytesTransferred, startTime, endTime);

                activeTransfers.remove(transferId);
                notifyTransferCompleted(filePath, remoteAddress, false, "Unexpected error: " + e.getMessage());
                notifyError(e, "File transfer execution (Unexpected)");

                return result;
            }
        }, com.justsyncit.scanner.ThreadPoolManager.getInstance().getBatchProcessingThreadPool());
    }

    /**
     * Sets whether compression is enabled.
     * 
     * @param enabled true to enable compression, false otherwise
     */
    public void setCompressionEnabled(boolean enabled) {
        this.compressionEnabled = enabled;
    }

    /**
     * Sets the compression level.
     * 
     * @param level the compression level (1-22)
     */
    public void setCompressionLevel(int level) {
        this.compressionLevel = level;
    }

    @Override
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "NetworkService is mutable but required for functionality")
    public void setNetworkService(com.justsyncit.network.NetworkService networkService) {
        this.networkService = networkService;
    }

    @Override
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "CompressionService is mutable but required for functionality")
    public void setCompressionService(CompressionService compressionService) {
        this.compressionService = compressionService;
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

            // Shutdown executor - Handled by ThreadPoolManager globally now
            // decompressionExecutor.shutdown();

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

            // Determine compression type based on config and service availability
            String compressionType = "NONE";
            if (compressionEnabled && compressionService != null) {
                compressionType = compressionService.getAlgorithmName();
                compressionService.setLevel(compressionLevel);
            }

            FileTransferStatus status = FileTransferStatus.pending(transferId, filePath, remoteAddress, fileSize,
                    compressionType);
            activeTransfers.put(transferId, status);

            notifyTransferStarted(filePath, remoteAddress, fileSize);

            // Create file transfer request
            Path fileNamePath = filePath.getFileName();
            String fileName = fileNamePath != null ? fileNamePath.toString() : "unknown";
            if (fileName == null || fileName.trim().isEmpty()) {
                throw new IOException("Invalid file path: filename is empty");
            }

            // Send transfer request
            FileTransferRequestMessage request = new FileTransferRequestMessage(
                    fileName, fileSize, System.currentTimeMillis(), PLACEHOLDER_HASH, DEFAULT_CHUNK_SIZE,
                    compressionType);

            return networkService.sendMessage(request, remoteAddress)
                    .orTimeout(30, TimeUnit.SECONDS)
                    .thenCompose(
                            v -> simulateFileTransfer(transferId, filePath, remoteAddress, contentStore, startTime));

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
        String compressionType = fileRequest.getCompressionType();

        logger.info("Received file transfer request: {} ({}) from {} [Compression: {}]",
                fileName, formatFileSize(fileSize), remoteAddress, compressionType);

        // Check if we can accept the transfer
        // For now, accept all

        // Register the transfer
        // Use fileName as ID for receiving to match what we expect in chunks
        // In a real system we would map this to a local temporary file path
        Path localPath = java.nio.file.Paths.get(fileName); // Simplified

        FileTransferStatus status = FileTransferStatus.pending(fileName, localPath, remoteAddress, fileSize,
                compressionType);
        activeTransfers.put(fileName, status);

        notifyTransferStarted(localPath, remoteAddress, fileSize);

        logger.debug("Accepting file transfer request for {}", fileName);

        // In a real implementation we should send an ACK here
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

        status.setState(FileTransferStatus.TransferState.IN_PROGRESS);

        // Submit decompression task to executor
        return CompletableFuture.supplyAsync(() -> {
            try {
                byte[] dataToStore = chunkDataBytes;

                // Decompress if needed
                if ("ZSTD".equals(status.getCompressionType()) && compressionService != null) {
                    dataToStore = compressionService.decompress(chunkDataBytes);
                } else if ("ZSTD".equals(status.getCompressionType()) && compressionService == null) {
                    throw new IOException("Received ZSTD compressed data but no compression service configured");
                }

                return dataToStore;
            } catch (IOException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, com.justsyncit.scanner.ThreadPoolManager.getInstance().getCpuThreadPool()) // CPU pool for decompression
                .thenComposeAsync(dataToStore -> {
                    // Verify checksum (on raw data) - Keeping small hash verification on CPU thread
                    // or switching to IO?
                    // Since we are switching to IO pool next for storeChunk, might as well do it
                    // there or here.
                    // Let's do verification here in CPU pool context before switching to IO to fail
                    // fast?
                    // Actually, we are chaining from the previous CPU task.
                    // But we want storeChunk to be on IO pool.

                    // We'll wrap the IO part in supplyAsync on IO pool.

                    String actualChecksum = computeChecksum(dataToStore);
                    if (!checksum.equals(actualChecksum)) {
                        return CompletableFuture.failedFuture(
                                new IOException("Checksum mismatch for chunk " + chunkOffset));
                    }

                    return CompletableFuture.runAsync(() -> {
                        // Store chunk in content store (IO operation)
                        try {
                            contentStore.storeChunk(dataToStore);
                        } catch (IOException e) {
                            throw new java.util.concurrent.CompletionException(e);
                        }
                    }, com.justsyncit.scanner.ThreadPoolManager.getInstance().getIoThreadPool()) // IO pool for storage
                            .thenRun(() -> {
                                // Update progress - we use UNCOMPRESSED size for progress to match file size
                                status.addBytesTransferred(dataToStore.length);

                                notifyTransferProgress(status.getFilePath(), remoteAddress,
                                        status.getBytesTransferred(),
                                        status.getFileSize());

                                // Send acknowledgment
                                new ChunkAckMessage(filePath, chunkOffset, dataToStore.length);
                                logger.debug("Sending chunk acknowledgment for offset {}", chunkOffset);

                                // This would delegate to NetworkService to send the acknowledgment
                            });

                }).exceptionallyCompose(e -> {
                    logger.error("Error processing chunk {} for transfer {}", chunkOffset, filePath, e);

                    // Send negative acknowledgment
                    logger.debug("Sending negative chunk acknowledgment for offset {}: {}", chunkOffset,
                            e.getMessage());

                    // This would delegate to NetworkService to send the acknowledgment
                    // We return a completed null future to indicate handled exception
                    return CompletableFuture.<Void>completedFuture(null);
                });
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

    /** The BLAKE3 service for checksums. */
    private com.justsyncit.hash.Blake3Service blake3Service;

    /**
     * Sets the BLAKE3 service.
     *
     * @param blake3Service the BLAKE3 service
     */
    public void setBlake3Service(com.justsyncit.hash.Blake3Service blake3Service) {
        this.blake3Service = blake3Service;
    }

    private String computeChecksum(byte[] data) {
        if (blake3Service != null) {
            try {
                // Use BLAKE3 to compute checksum
                return blake3Service.hashBuffer(data);
            } catch (Exception e) {
                logger.error("Failed to compute BLAKE3 checksum, falling back to SHA-256", e);
            }
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(data);
            return bytesToHex(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            logger.error("SHA-256 algorithm not found, falling back to weak hash", e);
            return Integer.toHexString(java.util.Arrays.hashCode(data));
        }
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (int i = 0; i < hash.length; i++) {
            String hex = Integer.toHexString(0xff & hash[i]);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
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
            } catch (RuntimeException e) {
                logger.error("Error in transfer event listener (onTransferStarted)", e);
            }
        }
    }

    private void notifyTransferProgress(Path filePath, InetSocketAddress remoteAddress,
            long bytesTransferred, long totalBytes) {
        for (TransferEventListener listener : listeners) {
            try {
                listener.onTransferProgress(filePath, remoteAddress, bytesTransferred, totalBytes);
            } catch (RuntimeException e) {
                logger.error("Error in transfer event listener (onTransferProgress)", e);
            }
        }
    }

    private void notifyTransferCompleted(Path filePath, InetSocketAddress remoteAddress,
            boolean success, String errorMessage) {
        for (TransferEventListener listener : listeners) {
            try {
                listener.onTransferCompleted(filePath, remoteAddress, success, errorMessage);
            } catch (RuntimeException e) {
                logger.error("Error in transfer event listener (onTransferCompleted)", e);
            }
        }
    }

    private void notifyError(Throwable error, String context) {
        for (TransferEventListener listener : listeners) {
            try {
                listener.onError(error, context);
            } catch (RuntimeException e) {
                logger.error("Error in transfer event listener (onError)", e);
            }
        }
    }
}